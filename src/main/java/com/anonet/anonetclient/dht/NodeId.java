/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 18:30
 * File: NodeId.java
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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class NodeId implements Comparable<NodeId> {

    public static final int ID_LENGTH_BITS = 160;
    public static final int ID_LENGTH_BYTES = ID_LENGTH_BITS / 8;

    private final byte[] id;

    public NodeId(byte[] id) {
        if (id == null || id.length != ID_LENGTH_BYTES) {
            throw new IllegalArgumentException("Node ID must be exactly " + ID_LENGTH_BYTES + " bytes");
        }
        this.id = Arrays.copyOf(id, id.length);
    }

    public static NodeId random() {
        byte[] randomBytes = new byte[ID_LENGTH_BYTES];
        new SecureRandom().nextBytes(randomBytes);
        return new NodeId(randomBytes);
    }

    public static NodeId fromFingerprint(String fingerprint) {
        return fromString(fingerprint);
    }

    public static NodeId fromString(String input) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(input.getBytes());
            return new NodeId(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    public static NodeId fromHex(String hex) {
        if (hex.length() != ID_LENGTH_BYTES * 2) {
            throw new IllegalArgumentException("Hex string must be " + (ID_LENGTH_BYTES * 2) + " characters");
        }
        byte[] bytes = new byte[ID_LENGTH_BYTES];
        for (int i = 0; i < ID_LENGTH_BYTES; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new NodeId(bytes);
    }

    public NodeId xorDistance(NodeId other) {
        byte[] result = new byte[ID_LENGTH_BYTES];
        for (int i = 0; i < ID_LENGTH_BYTES; i++) {
            result[i] = (byte) (this.id[i] ^ other.id[i]);
        }
        return new NodeId(result);
    }

    public int getBucketIndex(NodeId other) {
        NodeId distance = xorDistance(other);
        for (int i = 0; i < ID_LENGTH_BYTES; i++) {
            int b = distance.id[i] & 0xFF;
            if (b != 0) {
                int bitIndex = 7 - Integer.numberOfLeadingZeros(b) + 24;
                return (ID_LENGTH_BYTES - 1 - i) * 8 + (7 - Integer.numberOfLeadingZeros(b));
            }
        }
        return 0;
    }

    public boolean isCloserTo(NodeId target, NodeId other) {
        NodeId distanceThis = this.xorDistance(target);
        NodeId distanceOther = other.xorDistance(target);
        return distanceThis.compareTo(distanceOther) < 0;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(id, id.length);
    }

    public String toHex() {
        StringBuilder hex = new StringBuilder();
        for (byte b : id) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }

    public String toShortHex() {
        return toHex().substring(0, 8).toUpperCase();
    }

    public BigInteger toBigInteger() {
        return new BigInteger(1, id);
    }

    @Override
    public int compareTo(NodeId other) {
        for (int i = 0; i < ID_LENGTH_BYTES; i++) {
            int cmp = Integer.compare(this.id[i] & 0xFF, other.id[i] & 0xFF);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeId nodeId = (NodeId) o;
        return Arrays.equals(id, nodeId.id);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }

    @Override
    public String toString() {
        return "NodeId[" + toShortHex() + "...]";
    }
}
