/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.transfer
 * Created by: Ashish Kushwaha on 19-01-2026 22:30
 * File: SecureSocketChannel.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.transfer;

import com.anonet.anonetclient.crypto.session.SecureChannel;
import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public final class SecureSocketChannel implements AutoCloseable {

    private static final AnonetLogger LOG = AnonetLogger.get(SecureSocketChannel.class);
    private static final int MAX_MESSAGE_SIZE = 256 * 1024;

    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final SecureChannel secureChannel;

    public SecureSocketChannel(Socket socket, SecureChannel secureChannel) throws IOException {
        this.socket = socket;
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.secureChannel = secureChannel;
    }

    public void sendMessage(byte messageType, byte[] payload) throws IOException {
        SecureChannel.EncryptedMessage encrypted = secureChannel.encrypt(payload);
        byte[] encryptedBytes = encrypted.toBytes();

        outputStream.writeByte(messageType);
        outputStream.writeInt(encryptedBytes.length);
        outputStream.write(encryptedBytes);
        outputStream.flush();
    }

    public ReceivedMessage receiveMessage() throws IOException {
        byte messageType = inputStream.readByte();
        int length = inputStream.readInt();
        if (length <= 0 || length > MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid message size: " + length);
        }
        byte[] encryptedBytes = new byte[length];
        inputStream.readFully(encryptedBytes);

        SecureChannel.EncryptedMessage encrypted = SecureChannel.EncryptedMessage.fromBytes(encryptedBytes);
        byte[] decrypted = secureChannel.decrypt(encrypted);

        return new ReceivedMessage(messageType, decrypted);
    }

    public void sendRaw(byte[] data) throws IOException {
        outputStream.writeInt(data.length);
        outputStream.write(data);
        outputStream.flush();
    }

    public byte[] receiveRaw() throws IOException {
        int length = inputStream.readInt();
        if (length <= 0 || length > MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid raw message size: " + length);
        }
        byte[] data = new byte[length];
        inputStream.readFully(data);
        return data;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void close() {
        LOG.debug("Closing secure socket channel");
        secureChannel.close();
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public static final class ReceivedMessage {

        private final byte messageType;
        private final byte[] payload;

        public ReceivedMessage(byte messageType, byte[] payload) {
            this.messageType = messageType;
            this.payload = payload;
        }

        public byte getMessageType() {
            return messageType;
        }

        public byte[] getPayload() {
            return payload;
        }
    }
}
