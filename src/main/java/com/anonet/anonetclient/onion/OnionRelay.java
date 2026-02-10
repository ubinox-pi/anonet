/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.onion
 * Created by: Ashish Kushwaha on 03-02-2026 14:40
 * File: OnionRelay.java
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

import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.onion.OnionProtocol.OnionCell;
import com.anonet.anonetclient.onion.OnionProtocol.Command;
import com.anonet.anonetclient.onion.OnionProtocol.RelayCell;
import com.anonet.anonetclient.onion.OnionProtocol.RelayCommand;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class OnionRelay implements Closeable {

    public static final int DEFAULT_PORT = 51823;
    public static final int MAX_CIRCUITS = 1000;

    private final LocalIdentity identity;
    private final int port;
    private final Map<Integer, RelayCircuit> circuits;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private final AtomicBoolean running;
    private Consumer<String> statusCallback;

    public OnionRelay(LocalIdentity identity, int port) {
        this.identity = identity;
        this.port = port;
        this.circuits = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
    }

    public OnionRelay(LocalIdentity identity) {
        this(identity, DEFAULT_PORT);
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return;
        }

        serverSocket = new ServerSocket(port);
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "OnionRelay-Worker");
            t.setDaemon(true);
            return t;
        });

        executor.submit(this::acceptLoop);
        notifyStatus("Onion relay started on port " + port);
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        for (RelayCircuit circuit : circuits.values()) {
            circuit.close();
        }
        circuits.clear();

        if (executor != null) {
            executor.shutdownNow();
        }

        notifyStatus("Onion relay stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getActiveCircuits() {
        return circuits.size();
    }

    @Override
    public void close() {
        stop();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleConnection(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    notifyStatus("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            socket.setSoTimeout(30000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            while (running.get() && !socket.isClosed()) {
                byte[] cellData = new byte[OnionProtocol.CELL_SIZE];
                in.readFully(cellData);
                OnionCell cell = OnionCell.fromBytes(cellData);

                processCell(socket, in, out, cell);
            }

        } catch (IOException e) {
            if (running.get()) {
                notifyStatus("Connection error: " + e.getMessage());
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void processCell(Socket socket, DataInputStream in, DataOutputStream out, OnionCell cell) throws IOException {
        int circuitId = cell.getCircuitId();

        switch (cell.getCommand()) {
            case CREATE -> handleCreate(socket, out, cell);
            case RELAY -> handleRelay(socket, out, cell);
            case DESTROY -> handleDestroy(circuitId);
            case PADDING -> {}
            default -> notifyStatus("Unknown command: " + cell.getCommand());
        }
    }

    private void handleCreate(Socket socket, DataOutputStream out, OnionCell cell) throws IOException {
        int circuitId = cell.getCircuitId();

        if (circuits.size() >= MAX_CIRCUITS) {
            sendCell(out, OnionProtocol.destroyCell(circuitId));
            return;
        }

        byte[] peerPublicKey = cell.getPayload();
        KeyPair keyPair = OnionCrypto.generateKeyPair();
        byte[] sharedSecret = OnionCrypto.performKeyAgreement(keyPair.getPrivate(), peerPublicKey);
        OnionCrypto crypto = new OnionCrypto(sharedSecret, false);

        RelayCircuit circuit = new RelayCircuit(circuitId, socket, crypto);
        circuits.put(circuitId, circuit);

        OnionCell createdCell = OnionProtocol.createdCell(circuitId, keyPair.getPublic().getEncoded());
        sendCell(out, createdCell);

        notifyStatus("Circuit " + circuitId + " created");
    }

    private void handleRelay(Socket socket, DataOutputStream out, OnionCell cell) throws IOException {
        int circuitId = cell.getCircuitId();
        RelayCircuit circuit = circuits.get(circuitId);

        if (circuit == null) {
            notifyStatus("Unknown circuit: " + circuitId);
            return;
        }

        byte[] decrypted = circuit.getCrypto().decryptForward(cell.getPayload());
        RelayCell relayCell = RelayCell.fromPayload(decrypted);

        switch (relayCell.getRelayCommand()) {
            case RELAY_EXTEND -> handleExtend(circuit, out, relayCell);
            case RELAY_DATA -> handleData(circuit, relayCell);
            case RELAY_END -> handleEnd(circuit, relayCell);
            default -> {}
        }
    }

    private void handleExtend(RelayCircuit circuit, DataOutputStream out, RelayCell relayCell) throws IOException {
        byte[] extendData = relayCell.getData();

        byte[] ipBytes = new byte[4];
        System.arraycopy(extendData, 0, ipBytes, 0, 4);
        int port = ((extendData[4] & 0xFF) << 8) | (extendData[5] & 0xFF);
        byte[] peerPublicKey = new byte[extendData.length - 6];
        System.arraycopy(extendData, 6, peerPublicKey, 0, peerPublicKey.length);

        try {
            InetSocketAddress nextHop = new InetSocketAddress(
                java.net.InetAddress.getByAddress(ipBytes), port);

            Socket nextSocket = new Socket();
            nextSocket.connect(nextHop, 10000);
            nextSocket.setSoTimeout(30000);

            DataOutputStream nextOut = new DataOutputStream(nextSocket.getOutputStream());
            DataInputStream nextIn = new DataInputStream(nextSocket.getInputStream());

            OnionCell createCell = OnionProtocol.createCell(circuit.getCircuitId(), peerPublicKey);
            sendCell(nextOut, createCell);

            byte[] responseData = new byte[OnionProtocol.CELL_SIZE];
            nextIn.readFully(responseData);
            OnionCell response = OnionCell.fromBytes(responseData);

            if (response.getCommand() == Command.CREATED) {
                circuit.setNextHop(nextSocket, nextIn, nextOut);

                RelayCell extendedRelay = new RelayCell(
                    RelayCommand.RELAY_EXTENDED, 0, response.getPayload());
                byte[] encrypted = circuit.getCrypto().encryptBackward(extendedRelay.toPayload());
                OnionCell relayResponse = new OnionCell(circuit.getCircuitId(), Command.RELAY, encrypted);
                sendCell(out, relayResponse);

                notifyStatus("Circuit " + circuit.getCircuitId() + " extended");
            }

        } catch (Exception e) {
            notifyStatus("Extend failed: " + e.getMessage());
        }
    }

    private void handleData(RelayCircuit circuit, RelayCell relayCell) throws IOException {
        if (circuit.getNextOut() != null) {
            OnionCell forwardCell = new OnionCell(
                circuit.getCircuitId(), Command.RELAY, relayCell.toPayload());
            sendCell(circuit.getNextOut(), forwardCell);
        }
    }

    private void handleEnd(RelayCircuit circuit, RelayCell relayCell) {
        circuit.close();
        circuits.remove(circuit.getCircuitId());
    }

    private void handleDestroy(int circuitId) {
        RelayCircuit circuit = circuits.remove(circuitId);
        if (circuit != null) {
            circuit.close();
            notifyStatus("Circuit " + circuitId + " destroyed");
        }
    }

    private void sendCell(DataOutputStream out, OnionCell cell) throws IOException {
        synchronized (out) {
            out.write(cell.toBytes());
            out.flush();
        }
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }

    private static class RelayCircuit {
        private final int circuitId;
        private final Socket inSocket;
        private final OnionCrypto crypto;
        private Socket nextSocket;
        private DataInputStream nextIn;
        private DataOutputStream nextOut;

        RelayCircuit(int circuitId, Socket inSocket, OnionCrypto crypto) {
            this.circuitId = circuitId;
            this.inSocket = inSocket;
            this.crypto = crypto;
        }

        int getCircuitId() {
            return circuitId;
        }

        OnionCrypto getCrypto() {
            return crypto;
        }

        DataOutputStream getNextOut() {
            return nextOut;
        }

        void setNextHop(Socket socket, DataInputStream in, DataOutputStream out) {
            this.nextSocket = socket;
            this.nextIn = in;
            this.nextOut = out;
        }

        void close() {
            try {
                if (nextSocket != null && !nextSocket.isClosed()) {
                    nextSocket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
