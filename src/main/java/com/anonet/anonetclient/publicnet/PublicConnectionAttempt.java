/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 19-01-2026 23:45
 * File: PublicConnectionAttempt.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.publicnet;

import com.anonet.anonetclient.crypto.session.SessionKeyAgreement;
import com.anonet.anonetclient.crypto.session.SessionKeys;
import com.anonet.anonetclient.crypto.session.SignedEphemeralKey;
import com.anonet.anonetclient.identity.LocalIdentity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

public final class PublicConnectionAttempt {

    private static final int AUTH_TIMEOUT_MS = 10000;
    private static final String AUTH_INIT_PREFIX = "ANONET_AUTH_INIT|";
    private static final String AUTH_RESP_PREFIX = "ANONET_AUTH_RESP|";
    private static final String AUTH_COMPLETE_PREFIX = "ANONET_AUTH_DONE|";

    private final LocalIdentity localIdentity;
    private final PublicPeerEndpoint remoteEndpoint;
    private final DatagramSocket socket;
    private final InetSocketAddress remoteAddress;
    private final AtomicReference<ConnectionState> state;
    private final UdpHolePuncher.ConnectionStateCallback stateCallback;

    private volatile SessionKeys sessionKeys;
    private volatile boolean authenticated;

    public PublicConnectionAttempt(LocalIdentity localIdentity,
                                    PublicPeerEndpoint remoteEndpoint,
                                    DatagramSocket socket,
                                    InetSocketAddress remoteAddress,
                                    UdpHolePuncher.ConnectionStateCallback stateCallback) {
        this.localIdentity = localIdentity;
        this.remoteEndpoint = remoteEndpoint;
        this.socket = socket;
        this.remoteAddress = remoteAddress;
        this.state = new AtomicReference<>(ConnectionState.HOLE_PUNCHING);
        this.stateCallback = stateCallback;
        this.authenticated = false;
    }

    public boolean authenticate() {
        updateState(ConnectionState.AUTHENTICATING);

        try {
            socket.setSoTimeout(AUTH_TIMEOUT_MS);

            SessionKeyAgreement keyAgreement = new SessionKeyAgreement(localIdentity);
            SignedEphemeralKey localSignedKey = keyAgreement.generateSignedEphemeralKey();

            sendAuthInit(localSignedKey);

            SignedEphemeralKey peerSignedKey = receiveAuthResponse();
            if (peerSignedKey == null) {
                updateState(ConnectionState.FAILED_AUTH);
                return false;
            }

            try {
                sessionKeys = keyAgreement.completeKeyAgreement(peerSignedKey);
            } catch (Exception e) {
                updateState(ConnectionState.FAILED_AUTH);
                throw new PublicConnectionException("Peer authentication failed: " + e.getMessage(),
                        PublicConnectionException.FailureReason.AUTHENTICATION_FAILED, e);
            }

            sendAuthComplete();

            authenticated = true;
            updateState(ConnectionState.CONNECTED);
            return true;

        } catch (PublicConnectionException e) {
            updateState(ConnectionState.FAILED_AUTH);
            throw e;
        } catch (Exception e) {
            updateState(ConnectionState.FAILED_AUTH);
            throw new PublicConnectionException("Authentication failed: " + e.getMessage(),
                    PublicConnectionException.FailureReason.AUTHENTICATION_FAILED, e);
        }
    }

    private void sendAuthInit(SignedEphemeralKey signedKey) throws IOException {
        String payload = AUTH_INIT_PREFIX +
                localIdentity.getFingerprint() + "|" +
                Base64.getEncoder().encodeToString(signedKey.getEphemeralPublicKey()) + "|" +
                Base64.getEncoder().encodeToString(signedKey.getSignature()) + "|" +
                Base64.getEncoder().encodeToString(signedKey.getIdentityPublicKey());

        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress);
        socket.send(packet);
    }

    private SignedEphemeralKey receiveAuthResponse() throws IOException {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        long deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            try {
                socket.receive(packet);
                String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                if (data.startsWith(AUTH_RESP_PREFIX) || data.startsWith(AUTH_INIT_PREFIX)) {
                    String prefix = data.startsWith(AUTH_RESP_PREFIX) ? AUTH_RESP_PREFIX : AUTH_INIT_PREFIX;
                    SignedEphemeralKey key = parseSignedKey(data.substring(prefix.length()));
                    if (key != null) {
                        return key;
                    }
                }
            } catch (SocketTimeoutException ignored) {
            }
        }

        return null;
    }

    private void sendAuthComplete() throws IOException {
        String payload = AUTH_COMPLETE_PREFIX + localIdentity.getFingerprint();
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress);
        socket.send(packet);
    }

    private SignedEphemeralKey parseSignedKey(String data) {
        try {
            String[] parts = data.split("\\|");
            if (parts.length < 4) {
                return null;
            }

            String fingerprint = parts[0];
            byte[] ephemeralPublicKey = Base64.getDecoder().decode(parts[1]);
            byte[] signature = Base64.getDecoder().decode(parts[2]);
            byte[] identityPublicKey = Base64.getDecoder().decode(parts[3]);

            if (!fingerprint.equalsIgnoreCase(remoteEndpoint.getPublicKeyFingerprint())) {
                return null;
            }

            return new SignedEphemeralKey(ephemeralPublicKey, signature, identityPublicKey);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateState(ConnectionState newState) {
        state.set(newState);
        if (stateCallback != null) {
            stateCallback.onStateChanged(newState);
        }
    }

    public ConnectionState getState() {
        return state.get();
    }

    public SessionKeys getSessionKeys() {
        return sessionKeys;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
}
