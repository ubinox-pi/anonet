/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.relay
 * Created by: Ashish Kushwaha on 03-02-2026 11:30
 * File: RelayNode.java
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

import com.anonet.anonetclient.logging.AnonetLogger;
import com.anonet.anonetclient.relay.RelayProtocol.MessageType;
import com.anonet.anonetclient.relay.RelayProtocol.RelayMessage;
import com.anonet.anonetclient.security.RateLimiter;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RelayNode implements Closeable {

    private static final AnonetLogger LOG = AnonetLogger.get(RelayNode.class);

    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000;
    private static final int MAX_CONNECTIONS = 100;
    private static final int AUTH_CHALLENGE_SIZE = 32;
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService connectionExecutor;
    private ScheduledExecutorService maintenanceExecutor;
    private final Map<String, ConnectedClient> clients;
    private final Map<String, RelayPair> relayPairs;
    private final AtomicBoolean running;
    private final RateLimiter rateLimiter;
    private Consumer<String> statusCallback;

    public RelayNode(int port) {
        this.port = port;
        this.clients = new ConcurrentHashMap<>();
        this.relayPairs = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.rateLimiter = new RateLimiter(10, 1);
    }

    public RelayNode() {
        this(RelayProtocol.RELAY_PORT);
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return;
        }

        serverSocket = bindPort(port, 5);
        connectionExecutor = Executors.newFixedThreadPool(MAX_CONNECTIONS, r -> {
            Thread t = new Thread(r, "RelayNode-Worker");
            t.setDaemon(true);
            return t;
        });

        maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RelayNode-Maintenance");
            t.setDaemon(true);
            return t;
        });

        connectionExecutor.submit(this::acceptLoop);
        maintenanceExecutor.scheduleAtFixedRate(this::maintenance, 60, 60, TimeUnit.SECONDS);

        LOG.info("Relay node started on port %d", serverSocket.getLocalPort());
        notifyStatus("Relay node started on port " + serverSocket.getLocalPort());
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

        for (ConnectedClient client : clients.values()) {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
        clients.clear();
        relayPairs.clear();

        if (connectionExecutor != null) {
            connectionExecutor.shutdownNow();
        }
        if (maintenanceExecutor != null) {
            maintenanceExecutor.shutdownNow();
        }

        rateLimiter.shutdown();
        LOG.info("Relay node stopped");
        notifyStatus("Relay node stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getConnectedClients() {
        return clients.size();
    }

    public int getActiveRelays() {
        return relayPairs.size();
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                connectionExecutor.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                if (running.get()) {
                    notifyStatus("Accept error: " + e.getMessage());
                }
            } catch (IOException e) {
                notifyStatus("Accept error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        if (!rateLimiter.tryAcquire(socket.getInetAddress())) {
            LOG.warn("Rate limit exceeded for %s", socket.getInetAddress().getHostAddress());
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }
        ConnectedClient client = null;
        try {
            socket.setSoTimeout(30000);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            if (!authenticateClient(input, output)) {
                LOG.warn("Authentication failed for %s", socket.getInetAddress().getHostAddress());
                socket.close();
                return;
            }

            int length = input.readInt();
            byte[] data = new byte[length];
            input.readFully(data);
            RelayMessage hello = RelayMessage.fromBytes(data);

            if (hello.type != MessageType.HELLO) {
                throw new IOException("Expected HELLO, got " + hello.type);
            }

            client = parseHello(hello, socket, input, output);
            clients.put(client.fingerprint, client);

            RelayMessage helloAck = RelayProtocol.createHelloAck(client.sessionId);
            sendMessage(output, helloAck);

            notifyStatus("Client connected: " + client.fingerprint.substring(0, 8));
            LOG.info("Relay client connected: %s", client.fingerprint.substring(0, 8));

            while (running.get() && !socket.isClosed()) {
                try {
                    length = input.readInt();
                    data = new byte[length];
                    input.readFully(data);
                    RelayMessage message = RelayMessage.fromBytes(data);
                    handleMessage(client, message);
                } catch (SocketException e) {
                    break;
                }
            }

        } catch (IOException e) {
            if (running.get()) {
                notifyStatus("Client error: " + e.getMessage());
            }
        } finally {
            if (client != null) {
                clients.remove(client.fingerprint);
                removeFromRelayPairs(client.fingerprint);
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private boolean authenticateClient(DataInputStream input, DataOutputStream output) throws IOException {
        byte[] nonce = new byte[AUTH_CHALLENGE_SIZE];
        new SecureRandom().nextBytes(nonce);

        RelayMessage challenge = RelayProtocol.createAuthChallenge(nonce);
        sendMessage(output, challenge);

        int length = input.readInt();
        if (length <= 0 || length > RelayProtocol.MAX_MESSAGE_SIZE) {
            return false;
        }
        byte[] data = new byte[length];
        input.readFully(data);
        RelayMessage response = RelayMessage.fromBytes(data);

        if (response.type != MessageType.AUTH_RESPONSE) {
            return false;
        }

        try {
            byte[] signature = RelayProtocol.parseAuthResponseSignature(response.payload);
            byte[] publicKeyBytes = RelayProtocol.parseAuthResponsePublicKey(response.payload);

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(nonce);
            return verifier.verify(signature);
        } catch (Exception e) {
            LOG.warn("Auth verification error: %s", e.getMessage());
            return false;
        }
    }

    private void handleMessage(ConnectedClient sender, RelayMessage message) throws IOException {
        sender.lastActivity = System.currentTimeMillis();

        switch (message.type) {
            case REQUEST -> handleRequest(sender, message);
            case DATA -> handleData(sender, message);
            case CLOSE -> handleClose(sender, message);
            case PING -> {
                RelayMessage pong = RelayProtocol.createPong(message.sessionId);
                sendMessage(sender.output, pong);
            }
            default -> {}
        }
    }

    private void handleRequest(ConnectedClient sender, RelayMessage message) throws IOException {
        String targetFingerprint = new String(message.payload);
        ConnectedClient target = clients.get(targetFingerprint);

        if (target == null) {
            LOG.debug("Relay request rejected: peer %s not found", targetFingerprint.substring(0, 8));
            RelayMessage reject = RelayProtocol.createReject(message.sessionId, "Peer not found");
            sendMessage(sender.output, reject);
            return;
        }

        String pairId = sender.fingerprint + ":" + targetFingerprint;
        RelayPair pair = new RelayPair(sender, target);
        relayPairs.put(pairId, pair);

        RelayMessage accept = RelayProtocol.createAccept(message.sessionId);
        sendMessage(sender.output, accept);
        sendMessage(target.output, accept);

        notifyStatus("Relay established: " + sender.fingerprint.substring(0, 8) +
                    " <-> " + targetFingerprint.substring(0, 8));
        LOG.info("Relay established: %s <-> %s", sender.fingerprint.substring(0, 8), targetFingerprint.substring(0, 8));
    }

    private void handleData(ConnectedClient sender, RelayMessage message) throws IOException {
        for (RelayPair pair : relayPairs.values()) {
            ConnectedClient other = pair.getOther(sender);
            if (other != null) {
                RelayMessage forwardedData = RelayProtocol.createData(message.sessionId, message.payload);
                sendMessage(other.output, forwardedData);
                return;
            }
        }
    }

    private void handleClose(ConnectedClient sender, RelayMessage message) {
        removeFromRelayPairs(sender.fingerprint);
    }

    private void removeFromRelayPairs(String fingerprint) {
        relayPairs.entrySet().removeIf(entry ->
            entry.getValue().client1.fingerprint.equals(fingerprint) ||
            entry.getValue().client2.fingerprint.equals(fingerprint)
        );
    }

    private ConnectedClient parseHello(RelayMessage hello, Socket socket,
                                        DataInputStream input, DataOutputStream output) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(hello.payload);
        int fpLen = buffer.getShort() & 0xFFFF;
        byte[] fpBytes = new byte[fpLen];
        buffer.get(fpBytes);
        String fingerprint = new String(fpBytes);

        byte[] publicKey = new byte[buffer.remaining()];
        buffer.get(publicKey);

        String sessionId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        return new ConnectedClient(sessionId, fingerprint, publicKey, socket, input, output);
    }

    private void sendMessage(DataOutputStream output, RelayMessage message) throws IOException {
        byte[] data = message.toBytes();
        synchronized (output) {
            output.writeInt(data.length);
            output.write(data);
            output.flush();
        }
    }

    private void maintenance() {
        long now = System.currentTimeMillis();

        clients.entrySet().removeIf(entry -> {
            ConnectedClient client = entry.getValue();
            if (now - client.lastActivity > SESSION_TIMEOUT_MS) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
                notifyStatus("Client timed out: " + client.fingerprint.substring(0, 8));
                LOG.debug("Client timed out: %s", client.fingerprint.substring(0, 8));
                return true;
            }
            return false;
        });
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

    private static class ConnectedClient implements Closeable {
        final String sessionId;
        final String fingerprint;
        final byte[] publicKey;
        final Socket socket;
        final DataInputStream input;
        final DataOutputStream output;
        volatile long lastActivity;

        ConnectedClient(String sessionId, String fingerprint, byte[] publicKey,
                       Socket socket, DataInputStream input, DataOutputStream output) {
            this.sessionId = sessionId;
            this.fingerprint = fingerprint;
            this.publicKey = publicKey;
            this.socket = socket;
            this.input = input;
            this.output = output;
            this.lastActivity = System.currentTimeMillis();
        }

        @Override
        public void close() throws IOException {
            if (!socket.isClosed()) {
                socket.close();
            }
        }
    }

    private static class RelayPair {
        final ConnectedClient client1;
        final ConnectedClient client2;

        RelayPair(ConnectedClient client1, ConnectedClient client2) {
            this.client1 = client1;
            this.client2 = client2;
        }

        ConnectedClient getOther(ConnectedClient client) {
            if (client == client1) return client2;
            if (client == client2) return client1;
            return null;
        }
    }
}
