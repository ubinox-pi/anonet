/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.relay
 * Created by: Ashish Kushwaha on 03-02-2026 11:20
 * File: RelayClient.java
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

import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.relay.RelayProtocol.MessageType;
import com.anonet.anonetclient.relay.RelayProtocol.RelayMessage;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RelayClient implements Closeable {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final LocalIdentity identity;
    private final InetSocketAddress relayAddress;
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private RelaySession session;
    private final BlockingQueue<byte[]> receiveQueue;
    private final AtomicBoolean connected;
    private final AtomicBoolean closed;
    private Thread receiveThread;
    private Consumer<String> statusCallback;

    public RelayClient(LocalIdentity identity, InetSocketAddress relayAddress) {
        this.identity = identity;
        this.relayAddress = relayAddress;
        this.receiveQueue = new LinkedBlockingQueue<>();
        this.connected = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public boolean connect() throws IOException {
        socket = new Socket();
        socket.connect(relayAddress, CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);

        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());

        session = new RelaySession(identity.getFingerprint(), identity.getPublicKey().getEncoded());

        RelayMessage hello = RelayProtocol.createHello(
            identity.getFingerprint(),
            identity.getPublicKey().getEncoded()
        );
        sendMessage(hello);

        RelayMessage response = receiveMessage();
        if (response.type != MessageType.HELLO_ACK) {
            throw new IOException("Expected HELLO_ACK, got " + response.type);
        }

        session = new RelaySession(response.sessionId, identity.getFingerprint(),
                                   identity.getPublicKey().getEncoded());
        session.setState(RelaySession.State.CONNECTED);
        connected.set(true);

        startReceiveThread();
        notifyStatus("Connected to relay: " + relayAddress);

        return true;
    }

    public boolean requestPeer(String targetFingerprint) throws IOException {
        if (!connected.get()) {
            throw new IOException("Not connected to relay");
        }

        RelayMessage request = RelayProtocol.createRequest(session.getSessionId(), targetFingerprint);
        sendMessage(request);

        session.setState(RelaySession.State.WAITING_FOR_PEER);
        session.setPeerFingerprint(targetFingerprint);
        notifyStatus("Requesting connection to peer: " + targetFingerprint.substring(0, 8));

        return true;
    }

    public boolean waitForAccept(long timeoutMs) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (session.getState() == RelaySession.State.RELAYING) {
                return true;
            }
            Thread.sleep(100);
        }

        return false;
    }

    public void send(byte[] data) throws IOException {
        if (!connected.get() || session.getState() != RelaySession.State.RELAYING) {
            throw new IOException("Not in relaying state");
        }

        RelayMessage dataMsg = RelayProtocol.createData(session.getSessionId(), data);
        sendMessage(dataMsg);
        session.updateActivity();
    }

    public byte[] receive() throws InterruptedException {
        return receiveQueue.take();
    }

    public byte[] receive(long timeout, TimeUnit unit) throws InterruptedException {
        return receiveQueue.poll(timeout, unit);
    }

    public void ping() throws IOException {
        if (connected.get()) {
            RelayMessage ping = RelayProtocol.createPing(session.getSessionId());
            sendMessage(ping);
        }
    }

    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    public boolean isRelaying() {
        return isConnected() && session != null && session.getState() == RelaySession.State.RELAYING;
    }

    public RelaySession getSession() {
        return session;
    }

    @Override
    public void close() throws IOException {
        if (closed.getAndSet(true)) {
            return;
        }

        connected.set(false);

        if (receiveThread != null) {
            receiveThread.interrupt();
        }

        try {
            if (session != null && outputStream != null) {
                RelayMessage closeMsg = RelayProtocol.createClose(session.getSessionId());
                sendMessage(closeMsg);
            }
        } catch (IOException ignored) {
        }

        if (inputStream != null) {
            try { inputStream.close(); } catch (IOException ignored) {}
        }
        if (outputStream != null) {
            try { outputStream.close(); } catch (IOException ignored) {}
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (session != null) {
            session.close();
        }

        notifyStatus("Relay client closed");
    }

    private void startReceiveThread() {
        receiveThread = new Thread(() -> {
            while (!closed.get() && connected.get()) {
                try {
                    RelayMessage message = receiveMessage();
                    handleMessage(message);
                } catch (IOException e) {
                    if (!closed.get()) {
                        notifyStatus("Receive error: " + e.getMessage());
                        connected.set(false);
                    }
                    break;
                }
            }
        }, "RelayClient-Receiver");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void handleMessage(RelayMessage message) throws IOException {
        session.updateActivity();

        switch (message.type) {
            case ACCEPT -> {
                session.setState(RelaySession.State.RELAYING);
                notifyStatus("Peer connection accepted");
            }
            case REJECT -> {
                String reason = new String(message.payload);
                session.setState(RelaySession.State.CONNECTED);
                notifyStatus("Peer connection rejected: " + reason);
            }
            case DATA -> {
                receiveQueue.offer(message.payload);
            }
            case PING -> {
                RelayMessage pong = RelayProtocol.createPong(session.getSessionId());
                sendMessage(pong);
            }
            case PONG -> {
            }
            case CLOSE -> {
                session.setState(RelaySession.State.CLOSED);
                connected.set(false);
                notifyStatus("Connection closed by relay");
            }
            case ERROR -> {
                String error = new String(message.payload);
                notifyStatus("Relay error: " + error);
            }
            default -> notifyStatus("Unknown message type: " + message.type);
        }
    }

    private void sendMessage(RelayMessage message) throws IOException {
        byte[] data = message.toBytes();
        synchronized (outputStream) {
            outputStream.writeInt(data.length);
            outputStream.write(data);
            outputStream.flush();
        }
    }

    private RelayMessage receiveMessage() throws IOException {
        int length = inputStream.readInt();
        if (length <= 0 || length > RelayProtocol.MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid message length: " + length);
        }

        byte[] data = new byte[length];
        inputStream.readFully(data);
        return RelayMessage.fromBytes(data);
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }
}
