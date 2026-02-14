/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 19:00
 * File: DhtStorage.java
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DhtStorage {

    private static final AnonetLogger LOG = AnonetLogger.get(DhtStorage.class);

    private static final long DEFAULT_EXPIRY_MS = 60 * 60 * 1000;
    private static final int MAX_ENTRIES = 10000;

    private final Map<NodeId, StoredValue> storage;
    private final long expiryMs;

    public DhtStorage() {
        this(DEFAULT_EXPIRY_MS);
    }

    public DhtStorage(long expiryMs) {
        this.storage = new ConcurrentHashMap<>();
        this.expiryMs = expiryMs;
    }

    public void store(NodeId key, byte[] value) {
        store(key, value, System.currentTimeMillis());
    }

    public void store(NodeId key, byte[] value, long timestamp) {
        if (storage.size() >= MAX_ENTRIES) {
            LOG.debug("Storage full (%d entries), evicting expired", storage.size());
            evictExpired();
        }
        storage.put(key, new StoredValue(value, timestamp));
    }

    public Optional<byte[]> get(NodeId key) {
        StoredValue stored = storage.get(key);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.isExpired(expiryMs)) {
            storage.remove(key);
            return Optional.empty();
        }
        return Optional.of(stored.getValue());
    }

    public boolean contains(NodeId key) {
        StoredValue stored = storage.get(key);
        if (stored == null) {
            return false;
        }
        if (stored.isExpired(expiryMs)) {
            storage.remove(key);
            return false;
        }
        return true;
    }

    public void remove(NodeId key) {
        storage.remove(key);
    }

    public int size() {
        return storage.size();
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        storage.entrySet().removeIf(entry -> entry.getValue().isExpired(expiryMs));
    }

    public void clear() {
        storage.clear();
    }

    private static final class StoredValue {
        private final byte[] value;
        private final long timestamp;

        StoredValue(byte[] value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        byte[] getValue() {
            return value;
        }

        long getTimestamp() {
            return timestamp;
        }

        boolean isExpired(long expiryMs) {
            return System.currentTimeMillis() - timestamp > expiryMs;
        }
    }
}
