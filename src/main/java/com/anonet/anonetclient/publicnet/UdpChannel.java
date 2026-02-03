/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 03-02-2026 10:15
 * File: UdpChannel.java
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

import com.anonet.anonetclient.crypto.session.SecureChannel;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class UdpChannel implements Closeable {

    private final DatagramSocket socket;
    private final InetSocketAddress remoteAddress;
    private final ReliableUdp reliableUdp;
    private final BlockingQueue<byte[]> receiveQueue;
    private final AtomicBoolean closed;
    private SecureChannel secureChannel;
    private Thread receiveThread;
    private Consumer<String> statusCallback;

    public UdpChannel(DatagramSocket socket, InetSocketAddress remoteAddress) {
        this.socket = socket;
        this.remoteAddress = remoteAddress;
        this.reliableUdp = new ReliableUdp(socket, remoteAddress);
        this.receiveQueue = new LinkedBlockingQueue<>();
        this.closed = new AtomicBoolean(false);

        reliableUdp.setDataCallback(this::onDataReceived);
    }

    public static UdpChannel create(InetSocketAddress remoteAddress) throws SocketException {
        DatagramSocket socket = new DatagramSocket();
        return new UdpChannel(socket, remoteAddress);
    }

    public static UdpChannel createBound(int localPort, InetSocketAddress remoteAddress) throws SocketException {
        DatagramSocket socket = new DatagramSocket(localPort);
        return new UdpChannel(socket, remoteAddress);
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
        reliableUdp.setStatusCallback(callback);
    }

    public void setSecureChannel(SecureChannel secureChannel) {
        this.secureChannel = secureChannel;
    }

    public boolean connect() throws IOException {
        boolean connected = reliableUdp.connect();
        if (connected) {
            startReceiveThread();
        }
        return connected;
    }

    public boolean accept() throws IOException {
        boolean accepted = reliableUdp.accept();
        if (accepted) {
            startReceiveThread();
        }
        return accepted;
    }

    public void send(byte[] data) throws IOException {
        if (closed.get()) {
            throw new IOException("Channel is closed");
        }

        byte[] toSend = data;
        if (secureChannel != null) {
            toSend = secureChannel.encrypt(data).toBytes();
        }

        reliableUdp.send(toSend);
    }

    public void sendAndWait(byte[] data) throws IOException {
        if (closed.get()) {
            throw new IOException("Channel is closed");
        }

        byte[] toSend = data;
        if (secureChannel != null) {
            toSend = secureChannel.encrypt(data).toBytes();
        }

        reliableUdp.sendAndWait(toSend);
    }

    public byte[] receive() throws InterruptedException {
        byte[] data = receiveQueue.take();
        return decryptIfNeeded(data);
    }

    public byte[] receive(long timeout, TimeUnit unit) throws InterruptedException {
        byte[] data = receiveQueue.poll(timeout, unit);
        if (data == null) {
            return null;
        }
        return decryptIfNeeded(data);
    }

    public byte[] receiveAll(long timeout, TimeUnit unit) throws InterruptedException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] first = receiveQueue.poll(timeout, unit);
        if (first == null) {
            return null;
        }
        buffer.write(decryptIfNeeded(first), 0, first.length);

        while (true) {
            byte[] next = receiveQueue.poll(100, TimeUnit.MILLISECONDS);
            if (next == null) {
                break;
            }
            byte[] decrypted = decryptIfNeeded(next);
            buffer.write(decrypted, 0, decrypted.length);
        }

        return buffer.toByteArray();
    }

    public boolean isConnected() {
        return reliableUdp.isConnected() && !closed.get();
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        if (closed.getAndSet(true)) {
            return;
        }

        if (receiveThread != null) {
            receiveThread.interrupt();
        }

        reliableUdp.close();

        if (!socket.isClosed()) {
            socket.close();
        }

        notifyStatus("UDP channel closed");
    }

    private void startReceiveThread() {
        receiveThread = new Thread(() -> {
            while (!closed.get() && reliableUdp.isConnected()) {
                try {
                    reliableUdp.processIncoming();
                } catch (IOException e) {
                    if (!closed.get()) {
                        notifyStatus("Receive error: " + e.getMessage());
                    }
                }
            }
        }, "UdpChannel-Receiver");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void onDataReceived(byte[] data) {
        receiveQueue.offer(data);
    }

    private byte[] decryptIfNeeded(byte[] data) {
        if (secureChannel != null && data != null) {
            SecureChannel.EncryptedMessage encrypted = SecureChannel.EncryptedMessage.fromBytes(data);
            return secureChannel.decrypt(encrypted);
        }
        return data;
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }
}
