/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 18:50
 * File: RoutingTable.java
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RoutingTable {

    private static final int NUM_BUCKETS = NodeId.ID_LENGTH_BITS;

    private final NodeId localNodeId;
    private final KBucket[] buckets;

    public RoutingTable(NodeId localNodeId) {
        this.localNodeId = localNodeId;
        this.buckets = new KBucket[NUM_BUCKETS];
        for (int i = 0; i < NUM_BUCKETS; i++) {
            buckets[i] = new KBucket(i);
        }
    }

    public NodeId getLocalNodeId() {
        return localNodeId;
    }

    public Optional<DhtContact> addContact(DhtContact contact) {
        if (contact.getNodeId().equals(localNodeId)) {
            return Optional.empty();
        }
        int bucketIndex = getBucketIndex(contact.getNodeId());
        return buckets[bucketIndex].addOrUpdate(contact);
    }

    public void removeContact(NodeId nodeId) {
        int bucketIndex = getBucketIndex(nodeId);
        buckets[bucketIndex].remove(nodeId);
    }

    public void markSeen(NodeId nodeId) {
        int bucketIndex = getBucketIndex(nodeId);
        buckets[bucketIndex].markSeen(nodeId);
    }

    public Optional<DhtContact> getContact(NodeId nodeId) {
        int bucketIndex = getBucketIndex(nodeId);
        return buckets[bucketIndex].getContact(nodeId);
    }

    public List<DhtContact> getClosestContacts(NodeId target, int count) {
        List<DhtContact> allContacts = new ArrayList<>();

        int startBucket = getBucketIndex(target);

        allContacts.addAll(buckets[startBucket].getContacts());

        for (int offset = 1; offset < NUM_BUCKETS && allContacts.size() < count; offset++) {
            int lowerIndex = startBucket - offset;
            int higherIndex = startBucket + offset;

            if (lowerIndex >= 0) {
                allContacts.addAll(buckets[lowerIndex].getContacts());
            }
            if (higherIndex < NUM_BUCKETS) {
                allContacts.addAll(buckets[higherIndex].getContacts());
            }
        }

        allContacts.sort((a, b) -> {
            NodeId distA = a.getNodeId().xorDistance(target);
            NodeId distB = b.getNodeId().xorDistance(target);
            return distA.compareTo(distB);
        });

        return allContacts.subList(0, Math.min(count, allContacts.size()));
    }

    public List<DhtContact> getAllContacts() {
        List<DhtContact> all = new ArrayList<>();
        for (KBucket bucket : buckets) {
            all.addAll(bucket.getContacts());
        }
        return all;
    }

    public List<DhtContact> getStaleContacts() {
        List<DhtContact> stale = new ArrayList<>();
        for (KBucket bucket : buckets) {
            stale.addAll(bucket.getStaleContacts());
        }
        return stale;
    }

    public int getTotalContacts() {
        int total = 0;
        for (KBucket bucket : buckets) {
            total += bucket.size();
        }
        return total;
    }

    public KBucket getBucket(int index) {
        if (index < 0 || index >= NUM_BUCKETS) {
            throw new IndexOutOfBoundsException("Bucket index out of range: " + index);
        }
        return buckets[index];
    }

    public int getNumBuckets() {
        return NUM_BUCKETS;
    }

    public int getNonEmptyBucketCount() {
        int count = 0;
        for (KBucket bucket : buckets) {
            if (!bucket.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int getBucketIndex(NodeId nodeId) {
        NodeId distance = localNodeId.xorDistance(nodeId);
        byte[] distBytes = distance.getBytes();

        for (int i = 0; i < NodeId.ID_LENGTH_BYTES; i++) {
            int b = distBytes[i] & 0xFF;
            if (b != 0) {
                int leadingZeros = Integer.numberOfLeadingZeros(b) - 24;
                return NUM_BUCKETS - 1 - (i * 8 + leadingZeros);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "RoutingTable[localId=" + localNodeId.toShortHex() +
               ", contacts=" + getTotalContacts() +
               ", buckets=" + getNonEmptyBucketCount() + "/" + NUM_BUCKETS + "]";
    }
}
