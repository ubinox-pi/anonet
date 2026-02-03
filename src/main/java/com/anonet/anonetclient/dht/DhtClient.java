/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 19:15
 * File: DhtClient.java
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

import com.anonet.anonetclient.identity.LocalIdentity;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class DhtClient {

    private static final long LOOKUP_TIMEOUT_MS = 10000;
    private static final long ANNOUNCE_INTERVAL_MS = 5 * 60 * 1000;

    private final DhtNode node;
    private final LocalIdentity identity;
    private final String username;
    private volatile PeerAnnouncement lastAnnouncement;
    private Consumer<String> statusCallback;

    public DhtClient(LocalIdentity identity, String username, int port) {
        NodeId nodeId = NodeId.fromFingerprint(identity.getFingerprint());
        this.node = new DhtNode(nodeId, port);
        this.identity = identity;
        this.username = username;
    }

    public DhtClient(LocalIdentity identity, String username) {
        this(identity, username, DhtNode.DEFAULT_PORT);
    }

    public void start() throws SocketException {
        node.setStatusCallback(this::notifyStatus);
        node.start();
    }

    public void stop() {
        node.stop();
    }

    public boolean isRunning() {
        return node.isRunning();
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
        node.setStatusCallback(callback);
    }

    public void bootstrap(List<InetSocketAddress> bootstrapNodes) {
        for (InetSocketAddress addr : bootstrapNodes) {
            node.bootstrap(addr);
        }
    }

    public void bootstrap(InetSocketAddress bootstrapNode) {
        node.bootstrap(bootstrapNode);
    }

    public CompletableFuture<Optional<PeerAnnouncement>> lookup(String targetUsername) {
        return CompletableFuture.supplyAsync(() -> {
            NodeId key = NodeId.fromString(targetUsername);
            return iterativeFindValue(key);
        });
    }

    public CompletableFuture<Optional<PeerAnnouncement>> lookupByFingerprint(String fingerprint) {
        return CompletableFuture.supplyAsync(() -> {
            NodeId key = NodeId.fromString(fingerprint);
            return iterativeFindValue(key);
        });
    }

    public void announce(List<Integer> portCandidates) {
        String fullUsername = username + "#" + identity.getFingerprint().substring(0, 8).toUpperCase();

        PeerAnnouncement announcement = PeerAnnouncement.create(
            fullUsername,
            identity.getFingerprint(),
            identity.getPublicKey(),
            portCandidates,
            identity.getPrivateKey()
        );

        lastAnnouncement = announcement;

        NodeId usernameKey = announcement.getDhtKey();
        NodeId fingerprintKey = announcement.getFingerprintKey();
        byte[] announcementBytes = announcement.toBytes();

        List<DhtContact> closestToUsername = node.getRoutingTable().getClosestContacts(usernameKey, KBucket.K);
        List<DhtContact> closestToFingerprint = node.getRoutingTable().getClosestContacts(fingerprintKey, KBucket.K);

        for (DhtContact contact : closestToUsername) {
            node.announce(announcement, contact.getAddress());
        }

        for (DhtContact contact : closestToFingerprint) {
            if (!closestToUsername.contains(contact)) {
                node.announce(announcement, contact.getAddress());
            }
        }

        notifyStatus("Announced presence to " + (closestToUsername.size() + closestToFingerprint.size()) + " nodes");
    }

    public int getKnownPeersCount() {
        return node.getRoutingTable().getTotalContacts();
    }

    public List<DhtContact> getKnownPeers() {
        return node.getRoutingTable().getAllContacts();
    }

    public DhtNode getNode() {
        return node;
    }

    private Optional<PeerAnnouncement> iterativeFindValue(NodeId key) {
        Set<NodeId> queried = new HashSet<>();
        List<DhtContact> candidates = new ArrayList<>(node.getRoutingTable().getClosestContacts(key, DhtNode.ALPHA));

        long startTime = System.currentTimeMillis();

        while (!candidates.isEmpty() && System.currentTimeMillis() - startTime < LOOKUP_TIMEOUT_MS) {
            List<DhtContact> toQuery = new ArrayList<>();
            for (DhtContact contact : candidates) {
                if (!queried.contains(contact.getNodeId()) && toQuery.size() < DhtNode.ALPHA) {
                    toQuery.add(contact);
                    queried.add(contact.getNodeId());
                }
            }

            if (toQuery.isEmpty()) {
                break;
            }

            for (DhtContact contact : toQuery) {
                node.findValue(key, contact.getAddress());
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Optional<byte[]> stored = node.getStorage().get(key);
            if (stored.isPresent()) {
                try {
                    PeerAnnouncement announcement = PeerAnnouncement.fromBytes(stored.get());
                    if (announcement.verify()) {
                        return Optional.of(announcement);
                    }
                } catch (Exception e) {
                    notifyStatus("Invalid announcement data: " + e.getMessage());
                }
            }

            candidates = node.getRoutingTable().getClosestContacts(key, KBucket.K);
        }

        return Optional.empty();
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }

    @Override
    public String toString() {
        return "DhtClient[user=" + username + ", peers=" + getKnownPeersCount() + "]";
    }
}
