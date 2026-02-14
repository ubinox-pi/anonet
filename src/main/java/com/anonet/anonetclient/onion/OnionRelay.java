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
import com.anonet.anonetclient.logging.AnonetLogger;
import com.anonet.anonetclient.onion.OnionProtocol.OnionCell;
import com.anonet.anonetclient.onion.OnionProtocol.Command;
import com.anonet.anonetclient.onion.OnionProtocol.RelayCell;
import com.anonet.anonetclient.onion.OnionProtocol.RelayCommand;
import com.anonet.anonetclient.security.RateLimiter;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class OnionRelay implements Closeable {

    private static final AnonetLogger LOG = AnonetLogger.get(OnionRelay.class);

    public static final int DEFAULT_PORT = 51823;
    public static final int MAX_CIRCUITS = 1000;
    private static final int AUTH_CHALLENGE_SIZE = 32;
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final LocalIdentity identity;
    private final int port;
    private final Map<Integer, RelayCircuit> circuits;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private final AtomicBoolean running;
    private final RateLimiter rateLimiter;
    private Consumer<String> statusCallback;

    public OnionRelay(LocalIdentity identity, int port) {
        this.identity = identity;
        this.port = port;
        this.circuits = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.rateLimiter = new RateLimiter(10, 1);
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

        serverSocket = bindPort(port, 5);
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "OnionRelay-Worker");
            t.setDaemon(true);
            return t;
        });

        executor.submit(this::acceptLoop);
        LOG.info("Onion relay started on port %d", serverSocket.getLocalPort());
        notifyStatus("Onion relay started on port " + serverSocket.getLocalPort());
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

        rateLimiter.shutdown();
        LOG.info("Onion relay stopped");
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
                    LOG.error("Accept error: %s", e.getMessage());
                    notifyStatus("Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        if (!rateLimiter.tryAcquire(socket.getInetAddress())) {
            LOG.warn("Rate limit exceeded for %s", socket.getInetAddress().getHostAddress());
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }
        try {
            socket.setSoTimeout(30000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            if (!authenticateClient(in, out)) {
                LOG.warn("Onion auth failed for %s", socket.getInetAddress().getHostAddress());
                socket.close();
                return;
            }

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

    private boolean authenticateClient(DataInputStream in, DataOutputStream out) throws IOException {
        byte[] nonce = new byte[AUTH_CHALLENGE_SIZE];
        new SecureRandom().nextBytes(nonce);

        out.writeInt(nonce.length);
        out.write(nonce);
        out.flush();

        int sigLen = in.readInt();
        if (sigLen <= 0 || sigLen > 1024) {
            return false;
        }
        byte[] signature = new byte[sigLen];
        in.readFully(signature);

        int keyLen = in.readInt();
        if (keyLen <= 0 || keyLen > 1024) {
            return false;
        }
        byte[] publicKeyBytes = new byte[keyLen];
        in.readFully(publicKeyBytes);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(nonce);
            return verifier.verify(signature);
        } catch (Exception e) {
            LOG.warn("Onion auth verification error: %s", e.getMessage());
            return false;
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
            LOG.warn("Max circuits reached (%d), rejecting new circuit %d", MAX_CIRCUITS, circuitId);
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

        LOG.info("Circuit %d created", circuitId);
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
            LOG.error("Extend failed for circuit %d: %s", circuit.getCircuitId(), e.getMessage());
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

    public int getActualPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    private ServerSocket bindPort(int preferredPort, int maxRetries) throws IOException {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return new ServerSocket(preferredPort + i);
            } catch (BindException e) {
                if (i == maxRetries) throw e;
                LOG.warn("Port %d in use, trying %d", preferredPort + i, preferredPort + i + 1);
            }
        }
        throw new IOException("No available port");
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
