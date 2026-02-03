/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.relay
 * Created by: Ashish Kushwaha on 03-02-2026 11:00
 * File: RelayProtocol.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.relay;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class RelayProtocol {

    public static final int RELAY_PORT = 51822;
    public static final int MAX_MESSAGE_SIZE = 65536;
    public static final String PROTOCOL_VERSION = "1.0";

    public enum MessageType {
        HELLO(0x01),
        HELLO_ACK(0x02),
        REQUEST(0x03),
        ACCEPT(0x04),
        REJECT(0x05),
        DATA(0x06),
        CLOSE(0x07),
        PING(0x08),
        PONG(0x09),
        ERROR(0xFF);

        private final int code;

        MessageType(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static MessageType fromCode(int code) {
            for (MessageType t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            return ERROR;
        }
    }

    public static class RelayMessage {
        public final MessageType type;
        public final String sessionId;
        public final byte[] payload;

        public RelayMessage(MessageType type, String sessionId, byte[] payload) {
            this.type = type;
            this.sessionId = sessionId;
            this.payload = payload != null ? payload : new byte[0];
        }

        public byte[] toBytes() {
            byte[] sessionBytes = sessionId != null ? sessionId.getBytes(StandardCharsets.UTF_8) : new byte[0];
            ByteBuffer buffer = ByteBuffer.allocate(1 + 2 + sessionBytes.length + 4 + payload.length);

            buffer.put((byte) type.getCode());
            buffer.putShort((short) sessionBytes.length);
            buffer.put(sessionBytes);
            buffer.putInt(payload.length);
            buffer.put(payload);

            return buffer.array();
        }

        public static RelayMessage fromBytes(byte[] data) {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            MessageType type = MessageType.fromCode(buffer.get() & 0xFF);

            int sessionLen = buffer.getShort() & 0xFFFF;
            byte[] sessionBytes = new byte[sessionLen];
            buffer.get(sessionBytes);
            String sessionId = new String(sessionBytes, StandardCharsets.UTF_8);

            int payloadLen = buffer.getInt();
            byte[] payload = new byte[payloadLen];
            buffer.get(payload);

            return new RelayMessage(type, sessionId, payload);
        }
    }

    public static RelayMessage createHello(String fingerprint, byte[] publicKey) {
        ByteBuffer buffer = ByteBuffer.allocate(fingerprint.length() + 2 + publicKey.length);
        byte[] fpBytes = fingerprint.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) fpBytes.length);
        buffer.put(fpBytes);
        buffer.put(publicKey);
        return new RelayMessage(MessageType.HELLO, "", buffer.array());
    }

    public static RelayMessage createHelloAck(String sessionId) {
        return new RelayMessage(MessageType.HELLO_ACK, sessionId, new byte[0]);
    }

    public static RelayMessage createRequest(String sessionId, String targetFingerprint) {
        byte[] fpBytes = targetFingerprint.getBytes(StandardCharsets.UTF_8);
        return new RelayMessage(MessageType.REQUEST, sessionId, fpBytes);
    }

    public static RelayMessage createAccept(String sessionId) {
        return new RelayMessage(MessageType.ACCEPT, sessionId, new byte[0]);
    }

    public static RelayMessage createReject(String sessionId, String reason) {
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        return new RelayMessage(MessageType.REJECT, sessionId, reasonBytes);
    }

    public static RelayMessage createData(String sessionId, byte[] data) {
        return new RelayMessage(MessageType.DATA, sessionId, data);
    }

    public static RelayMessage createClose(String sessionId) {
        return new RelayMessage(MessageType.CLOSE, sessionId, new byte[0]);
    }

    public static RelayMessage createPing(String sessionId) {
        return new RelayMessage(MessageType.PING, sessionId, new byte[0]);
    }

    public static RelayMessage createPong(String sessionId) {
        return new RelayMessage(MessageType.PONG, sessionId, new byte[0]);
    }

    public static RelayMessage createError(String sessionId, String error) {
        byte[] errorBytes = error.getBytes(StandardCharsets.UTF_8);
        return new RelayMessage(MessageType.ERROR, sessionId, errorBytes);
    }
}
