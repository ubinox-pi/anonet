/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 19-01-2026 23:45
 * File: NatTraversalService.java
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

import com.anonet.anonetclient.crypto.session.SessionKeys;
import com.anonet.anonetclient.identity.LocalIdentity;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class NatTraversalService {

    private static final int DEFAULT_LOCAL_PORT = 51821;
    private static final int SOCKET_TIMEOUT_MS = 30000;

    private final LocalIdentity localIdentity;
    private final ExecutorService executor;
    private final AtomicReference<DatagramSocket> activeSocket;
    private final AtomicReference<PublicConnectionAttempt> activeConnection;
    private final AtomicReference<StunLikeClient.ExternalAddress> cachedExternalAddress;

    private volatile UdpHolePuncher currentPuncher;

    public NatTraversalService(LocalIdentity localIdentity) {
        this.localIdentity = localIdentity;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "anonet-nat-traversal");
            t.setDaemon(true);
            return t;
        });
        this.activeSocket = new AtomicReference<>();
        this.activeConnection = new AtomicReference<>();
        this.cachedExternalAddress = new AtomicReference<>();
    }

    public CompletableFuture<ConnectionResult> connectToPeer(
            PublicPeerEndpoint remoteEndpoint,
            Consumer<ConnectionState> stateCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return performConnection(remoteEndpoint, stateCallback::accept);
            } catch (PublicConnectionException e) {
                stateCallback.accept(mapFailureToState(e.getReason()));
                return new ConnectionResult(false, null, null, e.getReason());
            } catch (Exception e) {
                stateCallback.accept(ConnectionState.FAILED_NAT);
                return new ConnectionResult(false, null, null,
                        PublicConnectionException.FailureReason.NETWORK_ERROR);
            }
        }, executor);
    }

    private ConnectionResult performConnection(
            PublicPeerEndpoint remoteEndpoint,
            UdpHolePuncher.ConnectionStateCallback stateCallback) {

        stateCallback.onStateChanged(ConnectionState.ATTEMPTING_P2P);

        DatagramSocket socket = createSocket();
        activeSocket.set(socket);

        StunLikeClient.ExternalAddress externalAddress = discoverExternalAddress(socket);
        if (externalAddress == null) {
            stateCallback.onStateChanged(ConnectionState.FAILED_NAT);
            return new ConnectionResult(false, null, null,
                    PublicConnectionException.FailureReason.STUN_FAILED);
        }

        cachedExternalAddress.set(externalAddress);

        UdpHolePuncher puncher = new UdpHolePuncher(localIdentity.getFingerprint(), socket);
        currentPuncher = puncher;

        UdpHolePuncher.HolePunchResult punchResult = puncher.punchHole(remoteEndpoint, stateCallback);

        if (!punchResult.success()) {
            closeSocket(socket);
            return new ConnectionResult(false, null, null,
                    PublicConnectionException.FailureReason.HOLE_PUNCH_FAILED);
        }

        PublicConnectionAttempt connectionAttempt = new PublicConnectionAttempt(
                localIdentity,
                remoteEndpoint,
                punchResult.socket(),
                punchResult.remoteAddress(),
                stateCallback
        );

        activeConnection.set(connectionAttempt);

        boolean authenticated = connectionAttempt.authenticate();

        if (!authenticated) {
            closeSocket(socket);
            return new ConnectionResult(false, null, null,
                    PublicConnectionException.FailureReason.AUTHENTICATION_FAILED);
        }

        return new ConnectionResult(
                true,
                connectionAttempt,
                connectionAttempt.getSessionKeys(),
                null
        );
    }

    private DatagramSocket createSocket() {
        try {
            DatagramSocket socket = new DatagramSocket(DEFAULT_LOCAL_PORT);
            socket.setReuseAddress(true);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            return socket;
        } catch (SocketException e) {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setReuseAddress(true);
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                return socket;
            } catch (SocketException ex) {
                throw new PublicConnectionException("Failed to create UDP socket",
                        PublicConnectionException.FailureReason.NETWORK_ERROR, ex);
            }
        }
    }

    private StunLikeClient.ExternalAddress discoverExternalAddress(DatagramSocket socket) {
        StunLikeClient.ExternalAddress cached = cachedExternalAddress.get();
        if (cached != null) {
            return cached;
        }

        return StunLikeClient.discoverExternalAddress(socket);
    }

    public StunLikeClient.ExternalAddress getExternalAddress() {
        return cachedExternalAddress.get();
    }

    public List<Integer> getPortCandidates() {
        DatagramSocket socket = activeSocket.get();
        int localPort = socket != null ? socket.getLocalPort() : DEFAULT_LOCAL_PORT;
        return StunLikeClient.discoverPortCandidates(localPort);
    }

    public void cancelCurrentConnection() {
        if (currentPuncher != null) {
            currentPuncher.cancel();
        }

        DatagramSocket socket = activeSocket.get();
        if (socket != null) {
            closeSocket(socket);
        }

        activeConnection.set(null);
    }

    public void disconnect() {
        cancelCurrentConnection();
        activeSocket.set(null);
        cachedExternalAddress.set(null);
    }

    public void shutdown() {
        disconnect();
        executor.shutdownNow();
    }

    private void closeSocket(DatagramSocket socket) {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private ConnectionState mapFailureToState(PublicConnectionException.FailureReason reason) {
        return switch (reason) {
            case STUN_FAILED, HOLE_PUNCH_FAILED, SYMMETRIC_NAT, TIMEOUT, PEER_UNREACHABLE, NETWORK_ERROR ->
                    ConnectionState.FAILED_NAT;
            case AUTHENTICATION_FAILED -> ConnectionState.FAILED_AUTH;
        };
    }

    public record ConnectionResult(
            boolean success,
            PublicConnectionAttempt connection,
            SessionKeys sessionKeys,
            PublicConnectionException.FailureReason failureReason
    ) {}
}
