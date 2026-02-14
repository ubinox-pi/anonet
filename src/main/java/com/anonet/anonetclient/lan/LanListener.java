/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.lan
 * Created by: Ashish Kushwaha on 19-01-2026 21:50
 * File: LanListener.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.lan;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanListener {

    private static final AnonetLogger LOG = AnonetLogger.get(LanListener.class);

    private static final int SOCKET_TIMEOUT_MS = 1000;
    private static final int MAX_PORT_ATTEMPTS = 10;

    private final String ownFingerprint;
    private final PeerDiscoveredCallback peerDiscoveredCallback;
    private final AtomicBoolean running;
    private final ExecutorService executor;
    private DatagramSocket socket;
    private int actualPort;

    @FunctionalInterface
    public interface PeerDiscoveredCallback {
        void onPeerDiscovered(InetAddress address, String fingerprint, int dhtPort);
    }

    public LanListener(String ownFingerprint, PeerDiscoveredCallback peerDiscoveredCallback) {
        this.ownFingerprint = ownFingerprint;
        this.peerDiscoveredCallback = peerDiscoveredCallback;
        this.running = new AtomicBoolean(false);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "LanListener");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            SocketException lastException = null;
            int basePort = LanDiscoveryProtocol.DISCOVERY_PORT;

            for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
                int portToTry = basePort + attempt;
                try {
                    socket = new DatagramSocket(portToTry);
                    socket.setBroadcast(true);
                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    socket.setReuseAddress(true);
                    actualPort = portToTry;
                    LOG.info("LAN listener started on port %d", actualPort);
                    executor.submit(this::listen);
                    return;
                } catch (SocketException e) {
                    lastException = e;
                    LOG.warn("Port %d in use, trying next port...", portToTry);
                }
            }

            running.set(false);
            LOG.error("Failed to start listener after %d attempts", MAX_PORT_ATTEMPTS);
            throw new LanDiscoveryException("Failed to start listener - all ports in use", lastException);
        }
    }

    public int getActualPort() {
        return actualPort;
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOG.info("LAN listener stopped");
            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void listen() {
        byte[] buffer = new byte[LanDiscoveryProtocol.BUFFER_SIZE];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                processPacket(packet);
            } catch (SocketTimeoutException e) {
                // Expected timeout, continue listening
            } catch (IOException e) {
                if (running.get()) {
                    // Unexpected error while running
                }
            }
        }
    }

    private void processPacket(DatagramPacket packet) {
        LanDiscoveryProtocol.DiscoveryMessage message = LanDiscoveryProtocol.parseDiscoveryMessage(
                packet.getData(),
                packet.getLength()
        );

        if (message == null) {
            return;
        }

        String peerFingerprint = message.getFingerprint();

        if (peerFingerprint.equals(ownFingerprint)) {
            return;
        }

        LOG.debug("Discovered LAN peer: %s from %s (DHT port: %d)", peerFingerprint, packet.getAddress(), message.getDhtPort());
        peerDiscoveredCallback.onPeerDiscovered(packet.getAddress(), peerFingerprint, message.getDhtPort());
    }

    public boolean isRunning() {
        return running.get();
    }
}
