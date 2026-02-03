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
import java.util.function.BiConsumer;

public final class LanListener {

    private static final int SOCKET_TIMEOUT_MS = 1000;

    private final String ownFingerprint;
    private final BiConsumer<InetAddress, String> peerDiscoveredCallback;
    private final AtomicBoolean running;
    private final ExecutorService executor;
    private DatagramSocket socket;

    public LanListener(String ownFingerprint, BiConsumer<InetAddress, String> peerDiscoveredCallback) {
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
            try {
                socket = new DatagramSocket(LanDiscoveryProtocol.DISCOVERY_PORT);
                socket.setBroadcast(true);
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                executor.submit(this::listen);
            } catch (SocketException e) {
                running.set(false);
                throw new LanDiscoveryException("Failed to start listener on port " + LanDiscoveryProtocol.DISCOVERY_PORT, e);
            }
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
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

        peerDiscoveredCallback.accept(packet.getAddress(), peerFingerprint);
    }

    public boolean isRunning() {
        return running.get();
    }
}
