/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 18:45
 * File: DhtContact.java
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;

public final class DhtContact {

    private final NodeId nodeId;
    private final InetSocketAddress address;
    private volatile long lastSeen;
    private volatile int failedQueries;

    public DhtContact(NodeId nodeId, InetSocketAddress address) {
        this.nodeId = Objects.requireNonNull(nodeId);
        this.address = Objects.requireNonNull(address);
        this.lastSeen = System.currentTimeMillis();
        this.failedQueries = 0;
    }

    public DhtContact(NodeId nodeId, InetAddress ip, int port) {
        this(nodeId, new InetSocketAddress(ip, port));
    }

    public DhtContact(NodeId nodeId, String host, int port) {
        this(nodeId, new InetSocketAddress(host, port));
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public InetAddress getIp() {
        return address.getAddress();
    }

    public int getPort() {
        return address.getPort();
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
        this.failedQueries = 0;
    }

    public int getFailedQueries() {
        return failedQueries;
    }

    public void incrementFailedQueries() {
        this.failedQueries++;
    }

    public boolean isStale(long thresholdMs) {
        return System.currentTimeMillis() - lastSeen > thresholdMs;
    }

    public boolean isQuestionable() {
        return failedQueries > 0;
    }

    public boolean isBad() {
        return failedQueries >= 3;
    }

    public byte[] toBytes() {
        byte[] nodeIdBytes = nodeId.getBytes();
        byte[] ipBytes = address.getAddress().getAddress();
        int port = address.getPort();

        byte[] result = new byte[nodeIdBytes.length + ipBytes.length + 2];
        System.arraycopy(nodeIdBytes, 0, result, 0, nodeIdBytes.length);
        System.arraycopy(ipBytes, 0, result, nodeIdBytes.length, ipBytes.length);
        result[result.length - 2] = (byte) (port >> 8);
        result[result.length - 1] = (byte) port;

        return result;
    }

    public static DhtContact fromBytes(byte[] data, int offset) {
        byte[] nodeIdBytes = new byte[NodeId.ID_LENGTH_BYTES];
        System.arraycopy(data, offset, nodeIdBytes, 0, NodeId.ID_LENGTH_BYTES);
        NodeId nodeId = new NodeId(nodeIdBytes);

        int ipOffset = offset + NodeId.ID_LENGTH_BYTES;
        byte[] ipBytes = new byte[4];
        System.arraycopy(data, ipOffset, ipBytes, 0, 4);

        int portOffset = ipOffset + 4;
        int port = ((data[portOffset] & 0xFF) << 8) | (data[portOffset + 1] & 0xFF);

        try {
            InetAddress ip = InetAddress.getByAddress(ipBytes);
            return new DhtContact(nodeId, ip, port);
        } catch (Exception e) {
            throw new DhtException("Failed to parse contact", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DhtContact that = (DhtContact) o;
        return nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }

    @Override
    public String toString() {
        return "DhtContact[" + nodeId.toShortHex() + "@" + address + "]";
    }
}
