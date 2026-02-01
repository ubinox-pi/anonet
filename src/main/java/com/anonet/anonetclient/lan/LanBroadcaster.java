/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.lan
Created by: Ashish Kushwaha on 19-01-2026 21:50
File: LanBroadcaster.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.lan;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanBroadcaster {

    private final String fingerprint;
    private final AtomicBoolean running;
    private final ScheduledExecutorService scheduler;
    private DatagramSocket socket;

    public LanBroadcaster(String fingerprint) {
        this.fingerprint = fingerprint;
        this.running = new AtomicBoolean(false);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "LanBroadcaster");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                scheduler.scheduleAtFixedRate(
                        this::broadcast,
                        0,
                        LanDiscoveryProtocol.BROADCAST_INTERVAL_MS,
                        TimeUnit.MILLISECONDS
                );
            } catch (SocketException e) {
                running.set(false);
                throw new LanDiscoveryException("Failed to start broadcaster", e);
            }
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void broadcast() {
        if (!running.get() || socket == null || socket.isClosed()) {
            return;
        }

        byte[] messageData = LanDiscoveryProtocol.createDiscoveryMessage(fingerprint);
        List<InetAddress> broadcastAddresses = getBroadcastAddresses();

        for (InetAddress broadcastAddress : broadcastAddresses) {
            try {
                DatagramPacket packet = new DatagramPacket(
                        messageData,
                        messageData.length,
                        broadcastAddress,
                        LanDiscoveryProtocol.DISCOVERY_PORT
                );
                socket.send(packet);
            } catch (IOException e) {
                // Ignore broadcast failures on individual interfaces
            }
        }
    }

    private List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> broadcastAddresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        broadcastAddresses.add(broadcast);
                    }
                }
            }
        } catch (SocketException e) {
            // Return empty list if unable to enumerate interfaces
        }
        return broadcastAddresses;
    }

    public boolean isRunning() {
        return running.get();
    }
}
