/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 19:20
 * File: DhtBootstrap.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.dht;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DhtBootstrap {

    private static final AnonetLogger LOG = AnonetLogger.get(DhtBootstrap.class);

    private static final String BOOTSTRAP_MAGIC = "ANONET_DHT_BOOTSTRAP";
    private static final int BOOTSTRAP_PORT = 51819;
    private static final int BROADCAST_INTERVAL_MS = 5000;
    private static final String NODES_FILE = "dht_nodes.json";

    private static final List<String> HARDCODED_BOOTSTRAP_NODES = List.of(
        "dht1.anonet.community:51820",
        "dht2.anonet.community:51820"
    );

    private final NodeId localNodeId;
    private final Path anonetDir;
    private final List<InetSocketAddress> discoveredNodes;
    private final AtomicBoolean running;
    private ExecutorService executor;
    private DatagramSocket socket;
    private Consumer<String> statusCallback;
    private volatile int actualDhtPort = DhtNode.DEFAULT_PORT;

    public DhtBootstrap(NodeId localNodeId, Path anonetDir) {
        this.localNodeId = localNodeId;
        this.anonetDir = anonetDir;
        this.discoveredNodes = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
    }

    public void setActualDhtPort(int port) {
        this.actualDhtPort = port;
    }

    public List<InetSocketAddress> getDiscoveredNodes() {
        return new ArrayList<>(discoveredNodes);
    }

    public void clearDiscoveredNodes() {
        discoveredNodes.clear();
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public List<InetSocketAddress> getBootstrapNodes() {
        List<InetSocketAddress> nodes = new ArrayList<>();

        nodes.addAll(loadCachedNodes());

        nodes.addAll(discoveredNodes);

        for (String addr : HARDCODED_BOOTSTRAP_NODES) {
            try {
                String[] parts = addr.split(":");
                InetSocketAddress sockAddr = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                if (!sockAddr.isUnresolved()) {
                    nodes.add(sockAddr);
                } else {
                    LOG.debug("Skipping unresolvable bootstrap node: %s", addr);
                }
            } catch (Exception e) {
                LOG.debug("Invalid bootstrap address: %s", addr);
            }
        }

        return nodes;
    }

    public void startLanDiscovery() {
        if (running.getAndSet(true)) {
            return;
        }

        LOG.info("Starting LAN bootstrap discovery");

        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DHT-Bootstrap");
            t.setDaemon(true);
            return t;
        });

        boolean bound = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            int portToTry = BOOTSTRAP_PORT + attempt;
            try {
                socket = new DatagramSocket(portToTry);
                socket.setBroadcast(true);
                socket.setSoTimeout(1000);
                socket.setReuseAddress(true);
                LOG.info("Bootstrap listener started on port %d", portToTry);
                bound = true;
                break;
            } catch (Exception e) {
                LOG.warn("Bootstrap port %d in use, trying next...", portToTry);
            }
        }

        if (!bound) {
            LOG.error("Failed to start bootstrap listener after 10 attempts");
            notifyStatus("Failed to start bootstrap listener - all ports in use");
            running.set(false);
            return;
        }

        executor.submit(this::listenLoop);
        executor.submit(this::broadcastLoop);

        notifyStatus("LAN bootstrap discovery started");
    }

    public void stopLanDiscovery() {
        if (!running.getAndSet(false)) {
            return;
        }

        LOG.info("LAN bootstrap discovery stopped");

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (executor != null) {
            executor.shutdownNow();
        }

        notifyStatus("LAN bootstrap discovery stopped");
    }

    public void saveNodes(List<DhtContact> contacts) {
        try {
            Path nodesFile = anonetDir.resolve(NODES_FILE);
            Files.createDirectories(anonetDir);

            StringBuilder json = new StringBuilder();
            json.append("[\n");

            for (int i = 0; i < contacts.size(); i++) {
                DhtContact c = contacts.get(i);
                json.append("  {");
                json.append("\"nodeId\":\"").append(c.getNodeId().toHex()).append("\",");
                json.append("\"ip\":\"").append(c.getIp().getHostAddress()).append("\",");
                json.append("\"port\":").append(c.getPort());
                json.append("}");
                if (i < contacts.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("]");

            Files.writeString(nodesFile, json.toString());
            LOG.info("Saved %d nodes to cache", contacts.size());
            notifyStatus("Saved " + contacts.size() + " nodes to cache");

        } catch (IOException e) {
            LOG.error("Failed to save DHT nodes: %s", e.getMessage());
            notifyStatus("Failed to save nodes: " + e.getMessage());
        }
    }

    private List<InetSocketAddress> loadCachedNodes() {
        List<InetSocketAddress> nodes = new ArrayList<>();
        Path nodesFile = anonetDir.resolve(NODES_FILE);

        if (!Files.exists(nodesFile)) {
            return nodes;
        }

        try {
            String content = Files.readString(nodesFile);

            int start = 0;
            while ((start = content.indexOf("{", start)) != -1) {
                int end = content.indexOf("}", start);
                if (end == -1) break;

                String entry = content.substring(start, end + 1);

                String ip = extractJsonValue(entry, "ip");
                String portStr = extractJsonValue(entry, "port");

                if (ip != null && portStr != null) {
                    nodes.add(new InetSocketAddress(ip, Integer.parseInt(portStr)));
                }

                start = end + 1;
            }

            notifyStatus("Loaded " + nodes.size() + " cached nodes");

        } catch (Exception e) {
            notifyStatus("Failed to load cached nodes: " + e.getMessage());
        }

        return nodes;
    }

    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return null;

        int valueStart = idx + search.length();
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else {
            int valueEnd = valueStart;
            while (valueEnd < json.length() &&
                   (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '.')) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[256];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                if (message.startsWith(BOOTSTRAP_MAGIC + "|")) {
                    String payload = message.substring(BOOTSTRAP_MAGIC.length() + 1);
                    String[] parts = payload.split("\\|");
                    String nodeIdHex = parts[0];
                    int peerDhtPort = parts.length > 1 ? Integer.parseInt(parts[1]) : DhtNode.DEFAULT_PORT;
                    NodeId senderId = NodeId.fromHex(nodeIdHex);

                    if (!senderId.equals(localNodeId)) {
                        InetSocketAddress senderAddr = new InetSocketAddress(
                            packet.getAddress(), peerDhtPort);

                        if (!discoveredNodes.contains(senderAddr)) {
                            discoveredNodes.add(senderAddr);
                            LOG.info("Discovered DHT node via LAN: %s (DHT port %d)", senderAddr, peerDhtPort);
                            notifyStatus("Discovered DHT node: " + senderAddr);
                        }
                    }
                }

            } catch (java.net.SocketTimeoutException e) {
            } catch (NumberFormatException e) {
                LOG.debug("Invalid port in bootstrap message: %s", e.getMessage());
            } catch (IOException e) {
                if (running.get()) {
                    notifyStatus("Bootstrap listen error: " + e.getMessage());
                }
            }
        }
    }

    private void broadcastLoop() {
        while (running.get()) {
            try {
                broadcast();
                Thread.sleep(BROADCAST_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void broadcast() {
        String message = BOOTSTRAP_MAGIC + "|" + localNodeId.toHex() + "|" + actualDhtPort;
        byte[] data = message.getBytes(StandardCharsets.UTF_8);

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast != null) {
                        try {
                            DatagramPacket packet = new DatagramPacket(
                                data, data.length, broadcast, BOOTSTRAP_PORT);
                            socket.send(packet);
                        } catch (IOException e) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            notifyStatus("Broadcast error: " + e.getMessage());
        }
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }
}
