/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 03-02-2026 10:00
 * File: ReliableUdp.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.publicnet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ReliableUdp {

    public static final int FLAG_SYN = 0x01;
    public static final int FLAG_ACK = 0x02;
    public static final int FLAG_FIN = 0x04;
    public static final int FLAG_DATA = 0x08;
    public static final int FLAG_RST = 0x10;

    public static final int HEADER_SIZE = 11;
    public static final int MAX_PAYLOAD_SIZE = 1400 - HEADER_SIZE;
    public static final int DEFAULT_WINDOW_SIZE = 32;
    public static final int INITIAL_TIMEOUT_MS = 200;
    public static final int MAX_TIMEOUT_MS = 5000;
    public static final int MAX_RETRIES = 10;

    private final DatagramSocket socket;
    private final InetSocketAddress remoteAddress;
    private final AtomicInteger sendSequence;
    private final AtomicInteger receiveSequence;
    private final AtomicInteger sendWindow;
    private final Map<Integer, UnackedPacket> unackedPackets;
    private final AtomicBoolean connected;
    private final AtomicBoolean closed;
    private Consumer<byte[]> dataCallback;
    private Consumer<String> statusCallback;
    private int currentTimeout;

    public ReliableUdp(DatagramSocket socket, InetSocketAddress remoteAddress) {
        this.socket = socket;
        this.remoteAddress = remoteAddress;
        this.sendSequence = new AtomicInteger(0);
        this.receiveSequence = new AtomicInteger(0);
        this.sendWindow = new AtomicInteger(DEFAULT_WINDOW_SIZE);
        this.unackedPackets = new ConcurrentHashMap<>();
        this.connected = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.currentTimeout = INITIAL_TIMEOUT_MS;
    }

    public void setDataCallback(Consumer<byte[]> callback) {
        this.dataCallback = callback;
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public boolean connect() throws IOException {
        sendPacket(FLAG_SYN, new byte[0]);

        byte[] buffer = new byte[HEADER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                socket.setSoTimeout(currentTimeout);
                socket.receive(packet);

                RudpPacket response = parsePacket(packet.getData(), packet.getLength());
                if ((response.flags & FLAG_SYN) != 0 && (response.flags & FLAG_ACK) != 0) {
                    sendPacket(FLAG_ACK, new byte[0]);
                    connected.set(true);
                    notifyStatus("Connection established");
                    return true;
                }
            } catch (SocketTimeoutException e) {
                retries++;
                currentTimeout = Math.min(currentTimeout * 2, MAX_TIMEOUT_MS);
                sendPacket(FLAG_SYN, new byte[0]);
            }
        }

        notifyStatus("Connection failed after " + MAX_RETRIES + " retries");
        return false;
    }

    public boolean accept() throws IOException {
        byte[] buffer = new byte[HEADER_SIZE + MAX_PAYLOAD_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        socket.setSoTimeout(30000);
        socket.receive(packet);

        RudpPacket synPacket = parsePacket(packet.getData(), packet.getLength());
        if ((synPacket.flags & FLAG_SYN) != 0) {
            sendPacket(FLAG_SYN | FLAG_ACK, new byte[0]);

            socket.setSoTimeout(currentTimeout);
            socket.receive(packet);

            RudpPacket ackPacket = parsePacket(packet.getData(), packet.getLength());
            if ((ackPacket.flags & FLAG_ACK) != 0) {
                connected.set(true);
                notifyStatus("Connection accepted");
                return true;
            }
        }

        return false;
    }

    public void send(byte[] data) throws IOException {
        if (!connected.get() || closed.get()) {
            throw new IOException("Not connected");
        }

        int offset = 0;
        while (offset < data.length) {
            while (unackedPackets.size() >= sendWindow.get()) {
                processAcks();
            }

            int chunkSize = Math.min(MAX_PAYLOAD_SIZE, data.length - offset);
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(data, offset, chunk, 0, chunkSize);

            int seq = sendSequence.getAndIncrement();
            sendDataPacket(seq, chunk);

            offset += chunkSize;
        }
    }

    public void sendAndWait(byte[] data) throws IOException {
        send(data);
        waitForAllAcks();
    }

    public void close() throws IOException {
        if (closed.getAndSet(true)) {
            return;
        }

        waitForAllAcks();
        sendPacket(FLAG_FIN, new byte[0]);

        try {
            byte[] buffer = new byte[HEADER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.setSoTimeout(currentTimeout);
            socket.receive(packet);

            RudpPacket response = parsePacket(packet.getData(), packet.getLength());
            if ((response.flags & FLAG_FIN) != 0 && (response.flags & FLAG_ACK) != 0) {
                notifyStatus("Connection closed gracefully");
            }
        } catch (SocketTimeoutException e) {
            notifyStatus("Close timeout, connection terminated");
        }

        connected.set(false);
    }

    public void processIncoming() throws IOException {
        byte[] buffer = new byte[HEADER_SIZE + MAX_PAYLOAD_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.setSoTimeout(100);
            socket.receive(packet);
            handlePacket(packet.getData(), packet.getLength());
        } catch (SocketTimeoutException e) {
            checkRetransmissions();
        }
    }

    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    private void handlePacket(byte[] data, int length) throws IOException {
        RudpPacket packet = parsePacket(data, length);

        if ((packet.flags & FLAG_ACK) != 0) {
            handleAck(packet.ack);
        }

        if ((packet.flags & FLAG_DATA) != 0) {
            handleData(packet);
        }

        if ((packet.flags & FLAG_FIN) != 0) {
            sendPacket(FLAG_FIN | FLAG_ACK, new byte[0]);
            closed.set(true);
            connected.set(false);
            notifyStatus("Connection closed by peer");
        }

        if ((packet.flags & FLAG_RST) != 0) {
            closed.set(true);
            connected.set(false);
            notifyStatus("Connection reset by peer");
        }
    }

    private void handleAck(int ackNum) {
        UnackedPacket removed = unackedPackets.remove(ackNum);
        if (removed != null) {
            long rtt = System.currentTimeMillis() - removed.sentTime;
            currentTimeout = (int) Math.max(INITIAL_TIMEOUT_MS, Math.min(rtt * 2, MAX_TIMEOUT_MS));
        }
    }

    private void handleData(RudpPacket packet) throws IOException {
        int expectedSeq = receiveSequence.get();

        if (packet.seq == expectedSeq) {
            receiveSequence.incrementAndGet();
            sendAck(packet.seq);

            if (dataCallback != null && packet.payload.length > 0) {
                dataCallback.accept(packet.payload);
            }
        } else if (packet.seq < expectedSeq) {
            sendAck(packet.seq);
        }
    }

    private void sendDataPacket(int seq, byte[] payload) throws IOException {
        byte[] packetData = buildPacket(seq, receiveSequence.get(), FLAG_DATA, sendWindow.get(), payload);
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, remoteAddress);
        socket.send(packet);

        unackedPackets.put(seq, new UnackedPacket(packetData, System.currentTimeMillis(), 0));
    }

    private void sendPacket(int flags, byte[] payload) throws IOException {
        byte[] packetData = buildPacket(sendSequence.get(), receiveSequence.get(), flags, sendWindow.get(), payload);
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, remoteAddress);
        socket.send(packet);
    }

    private void sendAck(int ackSeq) throws IOException {
        byte[] packetData = buildPacket(sendSequence.get(), ackSeq, FLAG_ACK, sendWindow.get(), new byte[0]);
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, remoteAddress);
        socket.send(packet);
    }

    private void processAcks() throws IOException {
        byte[] buffer = new byte[HEADER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.setSoTimeout(50);
            socket.receive(packet);
            RudpPacket response = parsePacket(packet.getData(), packet.getLength());
            if ((response.flags & FLAG_ACK) != 0) {
                handleAck(response.ack);
            }
        } catch (SocketTimeoutException e) {
            checkRetransmissions();
        }
    }

    private void waitForAllAcks() throws IOException {
        long startTime = System.currentTimeMillis();
        while (!unackedPackets.isEmpty() && System.currentTimeMillis() - startTime < 30000) {
            processAcks();
        }
    }

    private void checkRetransmissions() throws IOException {
        long now = System.currentTimeMillis();

        for (Map.Entry<Integer, UnackedPacket> entry : unackedPackets.entrySet()) {
            UnackedPacket unacked = entry.getValue();
            int timeout = currentTimeout * (1 << unacked.retries);

            if (now - unacked.sentTime > timeout) {
                if (unacked.retries >= MAX_RETRIES) {
                    unackedPackets.remove(entry.getKey());
                    notifyStatus("Packet " + entry.getKey() + " dropped after max retries");
                } else {
                    DatagramPacket packet = new DatagramPacket(unacked.data, unacked.data.length, remoteAddress);
                    socket.send(packet);
                    unacked.sentTime = now;
                    unacked.retries++;
                }
            }
        }
    }

    private byte[] buildPacket(int seq, int ack, int flags, int window, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buffer.putInt(seq);
        buffer.putInt(ack);
        buffer.put((byte) flags);
        buffer.putShort((short) window);
        buffer.put(payload);
        return buffer.array();
    }

    private RudpPacket parsePacket(byte[] data, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        int seq = buffer.getInt();
        int ack = buffer.getInt();
        int flags = buffer.get() & 0xFF;
        int window = buffer.getShort() & 0xFFFF;

        byte[] payload = new byte[length - HEADER_SIZE];
        if (payload.length > 0) {
            buffer.get(payload);
        }

        return new RudpPacket(seq, ack, flags, window, payload);
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }

    private static class RudpPacket {
        final int seq;
        final int ack;
        final int flags;
        final int window;
        final byte[] payload;

        RudpPacket(int seq, int ack, int flags, int window, byte[] payload) {
            this.seq = seq;
            this.ack = ack;
            this.flags = flags;
            this.window = window;
            this.payload = payload;
        }
    }

    private static class UnackedPacket {
        final byte[] data;
        long sentTime;
        int retries;

        UnackedPacket(byte[] data, long sentTime, int retries) {
            this.data = data;
            this.sentTime = sentTime;
            this.retries = retries;
        }
    }
}
