/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 18:55
 * File: DhtMessage.java
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class DhtMessage {

    public static final int MAGIC = 0x414E4F44;
    public static final int HEADER_SIZE = 29;

    public enum Type {
        PING(0x01),
        PONG(0x02),
        FIND_NODE(0x03),
        NODES(0x04),
        FIND_VALUE(0x05),
        VALUE(0x06),
        STORE(0x07),
        STORED(0x08),
        ANNOUNCE(0x09),
        ANNOUNCED(0x0A);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static Type fromCode(int code) {
            for (Type t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            throw new DhtException("Unknown message type: " + code);
        }
    }

    private final Type type;
    private final int transactionId;
    private final NodeId senderId;
    private final byte[] payload;

    public DhtMessage(Type type, int transactionId, NodeId senderId, byte[] payload) {
        this.type = type;
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.payload = payload != null ? payload : new byte[0];
    }

    public Type getType() {
        return type;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public NodeId getSenderId() {
        return senderId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buffer.putInt(MAGIC);
        buffer.put((byte) type.getCode());
        buffer.putInt(transactionId);
        buffer.put(senderId.getBytes());
        buffer.put(payload);
        return buffer.array();
    }

    public static DhtMessage fromBytes(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new DhtException("Message too short: " + data.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new DhtException("Invalid magic: " + Integer.toHexString(magic));
        }

        Type type = Type.fromCode(buffer.get() & 0xFF);
        int transactionId = buffer.getInt();

        byte[] senderIdBytes = new byte[NodeId.ID_LENGTH_BYTES];
        buffer.get(senderIdBytes);
        NodeId senderId = new NodeId(senderIdBytes);

        byte[] payload = new byte[data.length - HEADER_SIZE];
        buffer.get(payload);

        return new DhtMessage(type, transactionId, senderId, payload);
    }

    public static DhtMessage createPing(int txnId, NodeId senderId) {
        return new DhtMessage(Type.PING, txnId, senderId, null);
    }

    public static DhtMessage createPong(int txnId, NodeId senderId) {
        return new DhtMessage(Type.PONG, txnId, senderId, null);
    }

    public static DhtMessage createFindNode(int txnId, NodeId senderId, NodeId targetId) {
        return new DhtMessage(Type.FIND_NODE, txnId, senderId, targetId.getBytes());
    }

    public static DhtMessage createNodes(int txnId, NodeId senderId, List<DhtContact> contacts) {
        byte[] payload = encodeContacts(contacts);
        return new DhtMessage(Type.NODES, txnId, senderId, payload);
    }

    public static DhtMessage createFindValue(int txnId, NodeId senderId, NodeId key) {
        return new DhtMessage(Type.FIND_VALUE, txnId, senderId, key.getBytes());
    }

    public static DhtMessage createValue(int txnId, NodeId senderId, byte[] value) {
        return new DhtMessage(Type.VALUE, txnId, senderId, value);
    }

    public static DhtMessage createStore(int txnId, NodeId senderId, NodeId key, byte[] value) {
        ByteBuffer buffer = ByteBuffer.allocate(NodeId.ID_LENGTH_BYTES + value.length);
        buffer.put(key.getBytes());
        buffer.put(value);
        return new DhtMessage(Type.STORE, txnId, senderId, buffer.array());
    }

    public static DhtMessage createStored(int txnId, NodeId senderId, boolean success) {
        return new DhtMessage(Type.STORED, txnId, senderId, new byte[]{(byte) (success ? 1 : 0)});
    }

    public static DhtMessage createAnnounce(int txnId, NodeId senderId, byte[] announcement) {
        return new DhtMessage(Type.ANNOUNCE, txnId, senderId, announcement);
    }

    public static DhtMessage createAnnounced(int txnId, NodeId senderId, boolean success) {
        return new DhtMessage(Type.ANNOUNCED, txnId, senderId, new byte[]{(byte) (success ? 1 : 0)});
    }

    public NodeId getTargetId() {
        if (payload.length >= NodeId.ID_LENGTH_BYTES) {
            byte[] targetBytes = new byte[NodeId.ID_LENGTH_BYTES];
            System.arraycopy(payload, 0, targetBytes, 0, NodeId.ID_LENGTH_BYTES);
            return new NodeId(targetBytes);
        }
        return null;
    }

    public List<DhtContact> getContacts() {
        return decodeContacts(payload);
    }

    public NodeId getStoreKey() {
        if (payload.length >= NodeId.ID_LENGTH_BYTES) {
            byte[] keyBytes = new byte[NodeId.ID_LENGTH_BYTES];
            System.arraycopy(payload, 0, keyBytes, 0, NodeId.ID_LENGTH_BYTES);
            return new NodeId(keyBytes);
        }
        return null;
    }

    public byte[] getStoreValue() {
        if (payload.length > NodeId.ID_LENGTH_BYTES) {
            byte[] value = new byte[payload.length - NodeId.ID_LENGTH_BYTES];
            System.arraycopy(payload, NodeId.ID_LENGTH_BYTES, value, 0, value.length);
            return value;
        }
        return new byte[0];
    }

    private static byte[] encodeContacts(List<DhtContact> contacts) {
        int contactSize = NodeId.ID_LENGTH_BYTES + 6;
        ByteBuffer buffer = ByteBuffer.allocate(contacts.size() * contactSize);
        for (DhtContact contact : contacts) {
            buffer.put(contact.toBytes());
        }
        return buffer.array();
    }

    private static List<DhtContact> decodeContacts(byte[] data) {
        List<DhtContact> contacts = new ArrayList<>();
        int contactSize = NodeId.ID_LENGTH_BYTES + 6;
        for (int offset = 0; offset + contactSize <= data.length; offset += contactSize) {
            contacts.add(DhtContact.fromBytes(data, offset));
        }
        return contacts;
    }

    @Override
    public String toString() {
        return "DhtMessage[type=" + type + ", txn=" + transactionId +
               ", sender=" + senderId.toShortHex() + ", payload=" + payload.length + "b]";
    }
}
