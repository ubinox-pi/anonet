/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 19-01-2026 23:45
 * File: UdpHolePuncher.java
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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpHolePuncher {

    private static final int PUNCH_TIMEOUT_MS = 100;
    private static final int PUNCH_ATTEMPTS = 50;
    private static final int PUNCH_INTERVAL_MS = 100;
    private static final int RECEIVE_TIMEOUT_MS = 5000;

    private static final String PUNCH_PREFIX = "ANONET_PUNCH|";
    private static final String PUNCH_ACK_PREFIX = "ANONET_PUNCH_ACK|";

    public record HolePunchResult(
            boolean success,
            InetSocketAddress remoteAddress,
            DatagramSocket socket,
            String peerFingerprint
    ) {}

    private final String localFingerprint;
    private final DatagramSocket socket;
    private final AtomicBoolean cancelled;

    public UdpHolePuncher(String localFingerprint, DatagramSocket socket) {
        this.localFingerprint = localFingerprint;
        this.socket = socket;
        this.cancelled = new AtomicBoolean(false);
    }

    public HolePunchResult punchHole(PublicPeerEndpoint remoteEndpoint,
                                      ConnectionStateCallback stateCallback) {
        List<InetSocketAddress> targetAddresses = remoteEndpoint.getAllSocketAddresses();
        String expectedFingerprint = remoteEndpoint.getPublicKeyFingerprint();

        byte[] nonce = generateNonce();
        byte[] punchPacket = createPunchPacket(nonce);

        stateCallback.onStateChanged(ConnectionState.HOLE_PUNCHING);

        try {
            socket.setSoTimeout(PUNCH_TIMEOUT_MS);

            for (int attempt = 0; attempt < PUNCH_ATTEMPTS && !cancelled.get(); attempt++) {
                for (InetSocketAddress target : targetAddresses) {
                    sendPunchPacket(target, punchPacket);
                }

                HolePunchResult result = tryReceivePunchResponse(expectedFingerprint);
                if (result != null && result.success()) {
                    sendPunchAck(result.remoteAddress(), nonce);
                    return result;
                }

                sleep(PUNCH_INTERVAL_MS);
            }

            socket.setSoTimeout(RECEIVE_TIMEOUT_MS);
            long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;

            while (System.currentTimeMillis() < deadline && !cancelled.get()) {
                HolePunchResult result = tryReceivePunchResponse(expectedFingerprint);
                if (result != null && result.success()) {
                    sendPunchAck(result.remoteAddress(), nonce);
                    return result;
                }
            }

        } catch (IOException e) {
            throw new PublicConnectionException("Hole punch failed: " + e.getMessage(),
                    PublicConnectionException.FailureReason.HOLE_PUNCH_FAILED, e);
        }

        stateCallback.onStateChanged(ConnectionState.FAILED_NAT);
        return new HolePunchResult(false, null, socket, null);
    }

    private void sendPunchPacket(InetSocketAddress target, byte[] packet) {
        try {
            DatagramPacket datagram = new DatagramPacket(packet, packet.length, target);
            socket.send(datagram);
        } catch (IOException ignored) {
        }
    }

    private void sendPunchAck(InetSocketAddress target, byte[] nonce) {
        try {
            byte[] ackPacket = createPunchAckPacket(nonce);
            DatagramPacket datagram = new DatagramPacket(ackPacket, ackPacket.length, target);
            socket.send(datagram);
        } catch (IOException ignored) {
        }
    }

    private HolePunchResult tryReceivePunchResponse(String expectedFingerprint) {
        byte[] buffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(packet);

            String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

            if (data.startsWith(PUNCH_PREFIX)) {
                String fingerprint = data.substring(PUNCH_PREFIX.length()).split("\\|")[0];

                if (expectedFingerprint.equalsIgnoreCase(fingerprint)) {
                    InetSocketAddress remoteAddress = new InetSocketAddress(
                            packet.getAddress(), packet.getPort());
                    return new HolePunchResult(true, remoteAddress, socket, fingerprint);
                }
            }

            if (data.startsWith(PUNCH_ACK_PREFIX)) {
                String fingerprint = data.substring(PUNCH_ACK_PREFIX.length()).split("\\|")[0];

                if (expectedFingerprint.equalsIgnoreCase(fingerprint)) {
                    InetSocketAddress remoteAddress = new InetSocketAddress(
                            packet.getAddress(), packet.getPort());
                    return new HolePunchResult(true, remoteAddress, socket, fingerprint);
                }
            }

        } catch (SocketTimeoutException ignored) {
        } catch (IOException e) {
            throw new PublicConnectionException("Error receiving punch response",
                    PublicConnectionException.FailureReason.NETWORK_ERROR, e);
        }

        return null;
    }

    private byte[] createPunchPacket(byte[] nonce) {
        String packet = PUNCH_PREFIX + localFingerprint + "|" + bytesToHex(nonce);
        return packet.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] createPunchAckPacket(byte[] nonce) {
        String packet = PUNCH_ACK_PREFIX + localFingerprint + "|" + bytesToHex(nonce);
        return packet.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void cancel() {
        cancelled.set(true);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface ConnectionStateCallback {
        void onStateChanged(ConnectionState state);
    }
}
