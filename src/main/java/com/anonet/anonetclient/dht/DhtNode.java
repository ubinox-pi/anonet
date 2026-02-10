/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 19:10
 * File: DhtNode.java
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
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class DhtNode {

    private static final AnonetLogger LOG = AnonetLogger.get(DhtNode.class);

    public static final int DEFAULT_PORT = 51820;
    public static final int MAX_PACKET_SIZE = 1400;
    public static final long QUERY_TIMEOUT_MS = 5000;
    public static final int ALPHA = 3;

    private final NodeId nodeId;
    private final RoutingTable routingTable;
    private final DhtStorage storage;
    private final int port;

    private DatagramSocket socket;
    private ExecutorService listenerExecutor;
    private ScheduledExecutorService maintenanceExecutor;
    private final AtomicBoolean running;
    private final AtomicInteger transactionIdCounter;
    private final Map<Integer, PendingQuery> pendingQueries;
    private Consumer<String> statusCallback;

    public DhtNode(NodeId nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.routingTable = new RoutingTable(nodeId);
        this.storage = new DhtStorage();
        this.running = new AtomicBoolean(false);
        this.transactionIdCounter = new AtomicInteger(1);
        this.pendingQueries = new ConcurrentHashMap<>();
    }

    public DhtNode(NodeId nodeId) {
        this(nodeId, DEFAULT_PORT);
    }

    public void start() throws SocketException {
        if (running.getAndSet(true)) {
            return;
        }

        socket = new DatagramSocket(port);
        socket.setSoTimeout(1000);

        listenerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DHT-Listener");
            t.setDaemon(true);
            return t;
        });

        maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DHT-Maintenance");
            t.setDaemon(true);
            return t;
        });

        listenerExecutor.submit(this::listenLoop);
        maintenanceExecutor.scheduleAtFixedRate(this::maintenance, 60, 60, TimeUnit.SECONDS);

        LOG.info("DHT node started on port %d with nodeId %s", port, nodeId.toHex().substring(0, 16));
        notifyStatus("DHT node started on port " + port);
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (listenerExecutor != null) {
            listenerExecutor.shutdownNow();
        }

        if (maintenanceExecutor != null) {
            maintenanceExecutor.shutdownNow();
        }

        LOG.info("DHT node stopped");
        notifyStatus("DHT node stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public RoutingTable getRoutingTable() {
        return routingTable;
    }

    public DhtStorage getStorage() {
        return storage;
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public void ping(InetSocketAddress address) {
        int txnId = transactionIdCounter.getAndIncrement();
        DhtMessage ping = DhtMessage.createPing(txnId, nodeId);
        sendMessage(ping, address);
    }

    public void findNode(NodeId targetId, InetSocketAddress address) {
        int txnId = transactionIdCounter.getAndIncrement();
        DhtMessage findNode = DhtMessage.createFindNode(txnId, nodeId, targetId);
        sendMessage(findNode, address);
    }

    public void findValue(NodeId key, InetSocketAddress address) {
        int txnId = transactionIdCounter.getAndIncrement();
        DhtMessage findValue = DhtMessage.createFindValue(txnId, nodeId, key);
        sendMessage(findValue, address);
    }

    public void store(NodeId key, byte[] value, InetSocketAddress address) {
        int txnId = transactionIdCounter.getAndIncrement();
        DhtMessage store = DhtMessage.createStore(txnId, nodeId, key, value);
        sendMessage(store, address);
    }

    public void announce(PeerAnnouncement announcement, InetSocketAddress address) {
        int txnId = transactionIdCounter.getAndIncrement();
        DhtMessage announce = DhtMessage.createAnnounce(txnId, nodeId, announcement.toBytes());
        sendMessage(announce, address);
    }

    public void bootstrap(InetSocketAddress bootstrapNode) {
        notifyStatus("Bootstrapping from " + bootstrapNode);
        ping(bootstrapNode);
        findNode(nodeId, bootstrapNode);
    }

    private void listenLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
                handlePacket(data, sender);

            } catch (java.net.SocketTimeoutException e) {
                checkPendingQueries();
            } catch (IOException e) {
                if (running.get()) {
                    notifyStatus("Receive error: " + e.getMessage());
                }
            }
        }
    }

    private void handlePacket(byte[] data, InetSocketAddress sender) {
        try {
            DhtMessage message = DhtMessage.fromBytes(data);
            DhtContact contact = new DhtContact(message.getSenderId(), sender);
            routingTable.addContact(contact);

            switch (message.getType()) {
                case PING -> handlePing(message, sender);
                case PONG -> handlePong(message, sender);
                case FIND_NODE -> handleFindNode(message, sender);
                case NODES -> handleNodes(message, sender);
                case FIND_VALUE -> handleFindValue(message, sender);
                case VALUE -> handleValue(message, sender);
                case STORE -> handleStore(message, sender);
                case STORED -> handleStored(message, sender);
                case ANNOUNCE -> handleAnnounce(message, sender);
                case ANNOUNCED -> handleAnnounced(message, sender);
            }
        } catch (Exception e) {
            notifyStatus("Error handling packet: " + e.getMessage());
        }
    }

    private void handlePing(DhtMessage message, InetSocketAddress sender) {
        DhtMessage pong = DhtMessage.createPong(message.getTransactionId(), nodeId);
        sendMessage(pong, sender);
    }

    private void handlePong(DhtMessage message, InetSocketAddress sender) {
        routingTable.markSeen(message.getSenderId());
        completePendingQuery(message.getTransactionId());
    }

    private void handleFindNode(DhtMessage message, InetSocketAddress sender) {
        NodeId targetId = message.getTargetId();
        if (targetId != null) {
            List<DhtContact> closest = routingTable.getClosestContacts(targetId, KBucket.K);
            DhtMessage nodes = DhtMessage.createNodes(message.getTransactionId(), nodeId, closest);
            sendMessage(nodes, sender);
        }
    }

    private void handleNodes(DhtMessage message, InetSocketAddress sender) {
        List<DhtContact> contacts = message.getContacts();
        for (DhtContact contact : contacts) {
            if (!contact.getNodeId().equals(nodeId)) {
                routingTable.addContact(contact);
            }
        }
        completePendingQuery(message.getTransactionId());
    }

    private void handleFindValue(DhtMessage message, InetSocketAddress sender) {
        NodeId key = message.getTargetId();
        if (key != null) {
            Optional<byte[]> value = storage.get(key);
            if (value.isPresent()) {
                DhtMessage valueMsg = DhtMessage.createValue(message.getTransactionId(), nodeId, value.get());
                sendMessage(valueMsg, sender);
            } else {
                List<DhtContact> closest = routingTable.getClosestContacts(key, KBucket.K);
                DhtMessage nodes = DhtMessage.createNodes(message.getTransactionId(), nodeId, closest);
                sendMessage(nodes, sender);
            }
        }
    }

    private void handleValue(DhtMessage message, InetSocketAddress sender) {
        completePendingQuery(message.getTransactionId(), message.getPayload());
    }

    private void handleStore(DhtMessage message, InetSocketAddress sender) {
        NodeId key = message.getStoreKey();
        byte[] value = message.getStoreValue();
        if (key != null && value.length > 0) {
            storage.store(key, value);
            DhtMessage stored = DhtMessage.createStored(message.getTransactionId(), nodeId, true);
            sendMessage(stored, sender);
        }
    }

    private void handleStored(DhtMessage message, InetSocketAddress sender) {
        completePendingQuery(message.getTransactionId());
    }

    private void handleAnnounce(DhtMessage message, InetSocketAddress sender) {
        try {
            PeerAnnouncement announcement = PeerAnnouncement.fromBytes(message.getPayload());
            if (announcement.verify()) {
                storage.store(announcement.getDhtKey(), message.getPayload());
                storage.store(announcement.getFingerprintKey(), message.getPayload());
                DhtMessage announced = DhtMessage.createAnnounced(message.getTransactionId(), nodeId, true);
                sendMessage(announced, sender);
            }
        } catch (Exception e) {
            DhtMessage announced = DhtMessage.createAnnounced(message.getTransactionId(), nodeId, false);
            sendMessage(announced, sender);
        }
    }

    private void handleAnnounced(DhtMessage message, InetSocketAddress sender) {
        completePendingQuery(message.getTransactionId());
    }

    private void sendMessage(DhtMessage message, InetSocketAddress address) {
        try {
            byte[] data = message.toBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            socket.send(packet);
        } catch (IOException e) {
            notifyStatus("Send error to " + address + ": " + e.getMessage());
        }
    }

    private void completePendingQuery(int transactionId) {
        completePendingQuery(transactionId, null);
    }

    private void completePendingQuery(int transactionId, byte[] result) {
        PendingQuery query = pendingQueries.remove(transactionId);
        if (query != null && query.callback != null) {
            query.callback.accept(result);
        }
    }

    private void checkPendingQueries() {
        long now = System.currentTimeMillis();
        pendingQueries.entrySet().removeIf(entry -> {
            if (now - entry.getValue().timestamp > QUERY_TIMEOUT_MS) {
                return true;
            }
            return false;
        });
    }

    private void maintenance() {
        storage.evictExpired();

        List<DhtContact> stale = routingTable.getStaleContacts();
        for (DhtContact contact : stale) {
            ping(contact.getAddress());
        }
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }

    private static class PendingQuery {
        final long timestamp;
        final Consumer<byte[]> callback;

        PendingQuery(Consumer<byte[]> callback) {
            this.timestamp = System.currentTimeMillis();
            this.callback = callback;
        }
    }

    @Override
    public String toString() {
        return "DhtNode[id=" + nodeId.toShortHex() + ", port=" + port +
               ", contacts=" + routingTable.getTotalContacts() + "]";
    }
}
