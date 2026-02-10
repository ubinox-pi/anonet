/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.onion
 * Created by: Ashish Kushwaha on 03-02-2026 14:00
 * File: OnionProtocol.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.onion;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class OnionProtocol {

    public static final int CELL_SIZE = 514;
    public static final int PAYLOAD_SIZE = 509;
    public static final int CIRCUIT_ID_SIZE = 4;
    public static final int COMMAND_SIZE = 1;
    public static final int HEADER_SIZE = CIRCUIT_ID_SIZE + COMMAND_SIZE;

    public enum Command {
        CREATE(0x01),
        CREATED(0x02),
        RELAY(0x03),
        DESTROY(0x04),
        PADDING(0x05),
        CREATE_FAST(0x06),
        CREATED_FAST(0x07),
        RELAY_EARLY(0x08);

        private final int code;

        Command(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static Command fromCode(int code) {
            for (Command c : values()) {
                if (c.code == code) {
                    return c;
                }
            }
            throw new OnionException("Unknown command: " + code);
        }
    }

    public enum RelayCommand {
        RELAY_DATA(0x02),
        RELAY_BEGIN(0x01),
        RELAY_END(0x03),
        RELAY_CONNECTED(0x04),
        RELAY_EXTEND(0x06),
        RELAY_EXTENDED(0x07),
        RELAY_DROP(0x0A);

        private final int code;

        RelayCommand(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static RelayCommand fromCode(int code) {
            for (RelayCommand c : values()) {
                if (c.code == code) {
                    return c;
                }
            }
            throw new OnionException("Unknown relay command: " + code);
        }
    }

    public static class OnionCell {
        private final int circuitId;
        private final Command command;
        private final byte[] payload;

        public OnionCell(int circuitId, Command command, byte[] payload) {
            this.circuitId = circuitId;
            this.command = command;
            if (payload.length > PAYLOAD_SIZE) {
                throw new OnionException("Payload too large: " + payload.length);
            }
            this.payload = Arrays.copyOf(payload, PAYLOAD_SIZE);
        }

        public int getCircuitId() {
            return circuitId;
        }

        public Command getCommand() {
            return command;
        }

        public byte[] getPayload() {
            return Arrays.copyOf(payload, payload.length);
        }

        public byte[] toBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(CELL_SIZE);
            buffer.putInt(circuitId);
            buffer.put((byte) command.getCode());
            buffer.put(payload);
            return buffer.array();
        }

        public static OnionCell fromBytes(byte[] data) {
            if (data.length != CELL_SIZE) {
                throw new OnionException("Invalid cell size: " + data.length);
            }
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int circuitId = buffer.getInt();
            Command command = Command.fromCode(buffer.get() & 0xFF);
            byte[] payload = new byte[PAYLOAD_SIZE];
            buffer.get(payload);
            return new OnionCell(circuitId, command, payload);
        }
    }

    public static class RelayCell {
        private final RelayCommand relayCommand;
        private final int streamId;
        private final byte[] data;

        public RelayCell(RelayCommand relayCommand, int streamId, byte[] data) {
            this.relayCommand = relayCommand;
            this.streamId = streamId;
            this.data = data != null ? data : new byte[0];
        }

        public RelayCommand getRelayCommand() {
            return relayCommand;
        }

        public int getStreamId() {
            return streamId;
        }

        public byte[] getData() {
            return Arrays.copyOf(data, data.length);
        }

        public byte[] toPayload() {
            ByteBuffer buffer = ByteBuffer.allocate(PAYLOAD_SIZE);
            buffer.put((byte) relayCommand.getCode());
            buffer.putShort((short) 0);
            buffer.putShort((short) streamId);
            buffer.putInt(0);
            buffer.putShort((short) data.length);
            buffer.put(data);
            while (buffer.position() < PAYLOAD_SIZE) {
                buffer.put((byte) 0);
            }
            return buffer.array();
        }

        public static RelayCell fromPayload(byte[] payload) {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            RelayCommand relayCommand = RelayCommand.fromCode(buffer.get() & 0xFF);
            buffer.getShort();
            int streamId = buffer.getShort() & 0xFFFF;
            buffer.getInt();
            int dataLen = buffer.getShort() & 0xFFFF;
            byte[] data = new byte[Math.min(dataLen, buffer.remaining())];
            buffer.get(data);
            return new RelayCell(relayCommand, streamId, data);
        }
    }

    public static OnionCell createCell(int circuitId, byte[] keyMaterial) {
        return new OnionCell(circuitId, Command.CREATE, keyMaterial);
    }

    public static OnionCell createdCell(int circuitId, byte[] keyMaterial) {
        return new OnionCell(circuitId, Command.CREATED, keyMaterial);
    }

    public static OnionCell relayCell(int circuitId, byte[] encryptedPayload) {
        return new OnionCell(circuitId, Command.RELAY, encryptedPayload);
    }

    public static OnionCell destroyCell(int circuitId) {
        return new OnionCell(circuitId, Command.DESTROY, new byte[0]);
    }

    public static OnionCell paddingCell(int circuitId) {
        byte[] padding = new byte[PAYLOAD_SIZE];
        new java.security.SecureRandom().nextBytes(padding);
        return new OnionCell(circuitId, Command.PADDING, padding);
    }
}
