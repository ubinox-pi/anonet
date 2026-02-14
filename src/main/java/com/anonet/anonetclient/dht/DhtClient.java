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
import com.anonet.anonetclient.logging.AnonetLogger;

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

    private static final AnonetLogger LOG = AnonetLogger.get(DhtClient.class);

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
        LOG.info("DHT client starting for user: %s", username);
        node.setStatusCallback(this::notifyStatus);
        node.start();
    }

    public void stop() {
        LOG.info("DHT client stopped");
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
        LOG.debug("Bootstrapping from %d nodes", bootstrapNodes.size());
        for (InetSocketAddress addr : bootstrapNodes) {
            node.bootstrap(addr);
        }
    }

    public void bootstrap(InetSocketAddress bootstrapNode) {
        node.bootstrap(bootstrapNode);
    }

    public CompletableFuture<Optional<PeerAnnouncement>> lookup(String targetUsername) {
        LOG.info("Looking up user: %s", targetUsername);
        return CompletableFuture.supplyAsync(() -> {
            NodeId key = NodeId.fromString(targetUsername);
            return iterativeFindValue(key);
        });
    }

    public CompletableFuture<Optional<PeerAnnouncement>> lookupByFingerprint(String fingerprint) {
        LOG.info("Looking up fingerprint: %s", fingerprint);
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

        LOG.info("Announcing presence as %s (usernameKey=%s, fpKey=%s)", fullUsername, usernameKey.toShortHex(), fingerprintKey.toShortHex());

        byte[] announcementBytes = announcement.toBytes();

        node.getStorage().store(usernameKey, announcementBytes);
        node.getStorage().store(fingerprintKey, announcementBytes);

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

    public PeerAnnouncement getLastAnnouncement() {
        return lastAnnouncement;
    }

    private Optional<PeerAnnouncement> iterativeFindValue(NodeId key) {
        Optional<byte[]> localStored = node.getStorage().get(key);
        if (localStored.isPresent()) {
            try {
                PeerAnnouncement announcement = PeerAnnouncement.fromBytes(localStored.get());
                if (announcement.verify()) {
                    LOG.info("Found announcement in local storage for key %s", key.toShortHex());
                    return Optional.of(announcement);
                }
            } catch (Exception e) {
                LOG.warn("Invalid local announcement data: %s", e.getMessage());
            }
        }

        Set<NodeId> queried = new HashSet<>();
        List<DhtContact> candidates = new ArrayList<>(node.getRoutingTable().getClosestContacts(key, KBucket.K));

        LOG.debug("DHT lookup for key %s, initial candidates: %d", key.toShortHex(), candidates.size());

        if (candidates.isEmpty()) {
            LOG.warn("No DHT contacts available for lookup");
            return Optional.empty();
        }

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

            LOG.debug("Querying %d DHT contacts for key %s", toQuery.size(), key.toShortHex());

            for (DhtContact contact : toQuery) {
                LOG.debug("Sending FIND_VALUE to %s:%d", contact.getIp().getHostAddress(), contact.getPort());
                node.findValue(key, contact.getAddress());
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Optional<byte[]> stored = node.getStorage().get(key);
            if (stored.isPresent()) {
                try {
                    PeerAnnouncement announcement = PeerAnnouncement.fromBytes(stored.get());
                    if (announcement.verify()) {
                        LOG.info("Found announcement via DHT query for key %s", key.toShortHex());
                        return Optional.of(announcement);
                    }
                } catch (Exception e) {
                    notifyStatus("Invalid announcement data: " + e.getMessage());
                }
            }

            candidates = node.getRoutingTable().getClosestContacts(key, KBucket.K);
        }

        LOG.debug("DHT iterative lookup done, retrying all %d contacts once more for key %s", queried.size(), key.toShortHex());
        for (DhtContact contact : node.getRoutingTable().getClosestContacts(key, KBucket.K)) {
            node.findValue(key, contact.getAddress());
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Optional<byte[]> finalCheck = node.getStorage().get(key);
        if (finalCheck.isPresent()) {
            try {
                PeerAnnouncement announcement = PeerAnnouncement.fromBytes(finalCheck.get());
                if (announcement.verify()) {
                    LOG.info("Found announcement on retry for key %s", key.toShortHex());
                    return Optional.of(announcement);
                }
            } catch (Exception e) {
                LOG.warn("Invalid announcement on retry: %s", e.getMessage());
            }
        }

        LOG.info("DHT lookup completed, no result found for key %s (queried %d nodes)", key.toShortHex(), queried.size());

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
