/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 18:40
 * File: KBucket.java
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

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class KBucket {

    public static final int K = 20; // Maximum nodes per bucket
    public static final long STALE_THRESHOLD_MS = 15 * 60 * 1000; // 15 minutes

    private final int bucketIndex;
    private final List<DhtContact> contacts;
    private final ReentrantReadWriteLock lock;
    private Instant lastUpdated;

    public KBucket(int bucketIndex) {
        this.bucketIndex = bucketIndex;
        this.contacts = new ArrayList<>(K);
        this.lock = new ReentrantReadWriteLock();
        this.lastUpdated = Instant.now();
    }

    public Optional<DhtContact> addOrUpdate(DhtContact contact) {
        lock.writeLock().lock();
        try {
            // Check if contact already exists
            int existingIndex = findContactIndex(contact.getNodeId());
            if (existingIndex >= 0) {
                // Move to end (most recently seen)
                DhtContact existing = contacts.remove(existingIndex);
                existing.updateLastSeen();
                contacts.add(existing);
                lastUpdated = Instant.now();
                return Optional.empty();
            }

            // New contact
            if (contacts.size() < K) {
                // Bucket not full, just add
                contacts.add(contact);
                lastUpdated = Instant.now();
                return Optional.empty();
            }

            // Bucket is full, return oldest for ping check
            return Optional.of(contacts.get(0));

        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceOldest(DhtContact newContact) {
        lock.writeLock().lock();
        try {
            if (!contacts.isEmpty()) {
                contacts.remove(0);
            }
            contacts.add(newContact);
            lastUpdated = Instant.now();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void markSeen(NodeId nodeId) {
        lock.writeLock().lock();
        try {
            int index = findContactIndex(nodeId);
            if (index >= 0) {
                DhtContact contact = contacts.remove(index);
                contact.updateLastSeen();
                contacts.add(contact);
                lastUpdated = Instant.now();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean remove(NodeId nodeId) {
        lock.writeLock().lock();
        try {
            int index = findContactIndex(nodeId);
            if (index >= 0) {
                contacts.remove(index);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<DhtContact> getContacts() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(contacts);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<DhtContact> getContact(NodeId nodeId) {
        lock.readLock().lock();
        try {
            return contacts.stream()
                    .filter(c -> c.getNodeId().equals(nodeId))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<DhtContact> getClosest(NodeId target, int count) {
        lock.readLock().lock();
        try {
            List<DhtContact> sorted = new ArrayList<>(contacts);
            sorted.sort((a, b) -> {
                NodeId distA = a.getNodeId().xorDistance(target);
                NodeId distB = b.getNodeId().xorDistance(target);
                return distA.compareTo(distB);
            });
            return sorted.subList(0, Math.min(count, sorted.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<DhtContact> getStaleContacts() {
        lock.readLock().lock();
        try {
            long now = System.currentTimeMillis();
            List<DhtContact> stale = new ArrayList<>();
            for (DhtContact contact : contacts) {
                if (now - contact.getLastSeen() > STALE_THRESHOLD_MS) {
                    stale.add(contact);
                }
            }
            return stale;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return contacts.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean isFull() {
        return size() >= K;
    }

    public int getBucketIndex() {
        return bucketIndex;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    private int findContactIndex(NodeId nodeId) {
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).getNodeId().equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return "KBucket[index=" + bucketIndex + ", size=" + size() + "/" + K + "]";
    }
}
