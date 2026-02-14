/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.onion
 * Created by: Ashish Kushwaha on 03-02-2026 14:30
 * File: CircuitBuilder.java
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

import com.anonet.anonetclient.dht.DhtClient;
import com.anonet.anonetclient.dht.DhtContact;
import com.anonet.anonetclient.dht.NodeId;
import com.anonet.anonetclient.dht.PeerAnnouncement;
import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.logging.AnonetLogger;
import com.anonet.anonetclient.onion.OnionCircuit.CircuitHop;
import com.anonet.anonetclient.onion.OnionProtocol.OnionCell;
import com.anonet.anonetclient.onion.OnionProtocol.Command;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CircuitBuilder {

    private static final AnonetLogger LOG = AnonetLogger.get(CircuitBuilder.class);

    public static final int DEFAULT_HOP_COUNT = 3;
    public static final int CONNECT_TIMEOUT_MS = 10000;
    public static final int READ_TIMEOUT_MS = 30000;
    public static final String RELAY_DHT_KEY = "anonet-onion-relays";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private static final int MAX_RELAY_FAILURES = 3;

    private final DhtClient dhtClient;
    private final LocalIdentity localIdentity;
    private final List<RelayInfo> knownRelays;
    private final Map<String, Integer> failCounts;
    private Consumer<String> statusCallback;

    public CircuitBuilder(DhtClient dhtClient, LocalIdentity localIdentity) {
        this.dhtClient = dhtClient;
        this.localIdentity = localIdentity;
        this.knownRelays = new ArrayList<>();
        this.failCounts = new ConcurrentHashMap<>();
    }

    public CircuitBuilder(DhtClient dhtClient) {
        this(dhtClient, null);
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public CompletableFuture<OnionCircuit> buildCircuit() {
        return buildCircuit(DEFAULT_HOP_COUNT);
    }

    public CompletableFuture<OnionCircuit> buildCircuit(int hopCount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                refreshRelayList();

                if (knownRelays.size() < hopCount) {
                    LOG.warn("Not enough relays: %d available, %d needed", knownRelays.size(), hopCount);
                    throw new OnionException("Not enough relays available: " + knownRelays.size() + " < " + hopCount);
                }

                List<RelayInfo> selectedRelays = selectRelays(hopCount);
                OnionCircuit circuit = new OnionCircuit();

                notifyStatus("Building circuit with " + hopCount + " hops...");

                Socket guardSocket = connectToRelay(selectedRelays.get(0));
                CircuitHop guardHop = createHop(selectedRelays.get(0), guardSocket, circuit.getCircuitId(), true);
                circuit.addHop(guardHop);
                notifyStatus("Guard hop established");

                for (int i = 1; i < selectedRelays.size(); i++) {
                    RelayInfo relay = selectedRelays.get(i);
                    CircuitHop hop = extendCircuit(circuit, guardSocket, relay);
                    circuit.addHop(hop);
                    notifyStatus("Hop " + (i + 1) + " established");
                }

                circuit.setState(OnionCircuit.State.READY);
                LOG.info("Circuit ready with %d hops, circuitId=%d", circuit.getHopCount(), circuit.getCircuitId());
                notifyStatus("Circuit ready with " + circuit.getHopCount() + " hops");

                return circuit;

            } catch (Exception e) {
                LOG.error("Failed to build circuit", e);
                throw new OnionException("Failed to build circuit", e);
            }
        });
    }

    public void refreshRelayList() {
        try {
            List<DhtContact> dhtPeers = dhtClient.getKnownPeers();
            int added = 0;
            for (DhtContact peer : dhtPeers) {
                InetSocketAddress relayAddr = new InetSocketAddress(peer.getIp(), OnionRelay.DEFAULT_PORT);
                String fingerprint = peer.getNodeId().toHex();
                RelayInfo relay = new RelayInfo(fingerprint, relayAddr, new byte[0], 0, false);
                if (!knownRelays.contains(relay)) {
                    knownRelays.add(relay);
                    added++;
                }
            }
            if (added > 0) {
                notifyStatus("Discovered " + added + " potential onion relays from DHT (" + knownRelays.size() + " total)");
                LOG.info("Refreshed relay list: %d new, %d total", added, knownRelays.size());
            }
        } catch (Exception e) {
            LOG.warn("Failed to refresh relay list: %s", e.getMessage());
            notifyStatus("Failed to refresh relay list: " + e.getMessage());
        }
    }

    public void addRelay(RelayInfo relay) {
        if (!knownRelays.contains(relay)) {
            knownRelays.add(relay);
        }
    }

    public List<RelayInfo> getKnownRelays() {
        return new ArrayList<>(knownRelays);
    }

    private List<RelayInfo> selectRelays(int count) {
        List<RelayInfo> shuffled = new ArrayList<>(knownRelays);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private Socket connectToRelay(RelayInfo relay) throws IOException {
        try {
            Socket socket = new Socket();
            socket.connect(relay.getAddress(), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            if (localIdentity != null) {
                authenticateWithRelay(socket);
            }

            failCounts.remove(relay.getFingerprint());
            return socket;
        } catch (IOException e) {
            int fails = failCounts.merge(relay.getFingerprint(), 1, Integer::sum);
            if (fails >= MAX_RELAY_FAILURES) {
                knownRelays.remove(relay);
                failCounts.remove(relay.getFingerprint());
                LOG.warn("Relay %s removed after %d consecutive failures", relay.getFingerprint().substring(0, 8), fails);
            }
            throw e;
        }
    }

    private void authenticateWithRelay(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        int nonceLen = in.readInt();
        if (nonceLen <= 0 || nonceLen > 64) {
            throw new IOException("Invalid auth challenge size: " + nonceLen);
        }
        byte[] nonce = new byte[nonceLen];
        in.readFully(nonce);

        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(localIdentity.getPrivateKey());
            signer.update(nonce);
            byte[] signature = signer.sign();

            out.writeInt(signature.length);
            out.write(signature);
            out.writeInt(localIdentity.getPublicKey().getEncoded().length);
            out.write(localIdentity.getPublicKey().getEncoded());
            out.flush();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to authenticate with relay: " + e.getMessage(), e);
        }
    }

    private CircuitHop createHop(RelayInfo relay, Socket socket, int circuitId, boolean isFirst) throws IOException {
        KeyPair keyPair = OnionCrypto.generateKeyPair();
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();

        OnionCell createCell = OnionProtocol.createCell(circuitId, publicKeyBytes);
        sendCell(socket, createCell);

        OnionCell response = receiveCell(socket);
        if (response.getCommand() != Command.CREATED) {
            throw new OnionException("Expected CREATED, got " + response.getCommand());
        }

        byte[] peerPublicKey = response.getPayload();
        byte[] sharedSecret = OnionCrypto.performKeyAgreement(keyPair.getPrivate(), peerPublicKey);
        OnionCrypto crypto = new OnionCrypto(sharedSecret, true);

        return new CircuitHop(relay.getAddress(), peerPublicKey, crypto, relay.getFingerprint());
    }

    private CircuitHop extendCircuit(OnionCircuit circuit, Socket socket, RelayInfo relay) throws IOException {
        KeyPair keyPair = OnionCrypto.generateKeyPair();
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();

        byte[] extendPayload = buildExtendPayload(relay.getAddress(), publicKeyBytes);
        OnionCell relayCell = circuit.createRelayCell(
            OnionProtocol.RelayCommand.RELAY_EXTEND,
            0,
            extendPayload
        );
        sendCell(socket, relayCell);

        OnionCell response = receiveCell(socket);
        if (response.getCommand() != Command.RELAY) {
            throw new OnionException("Expected RELAY, got " + response.getCommand());
        }

        OnionProtocol.RelayCell relayResponse = circuit.processRelayCell(response);
        if (relayResponse.getRelayCommand() != OnionProtocol.RelayCommand.RELAY_EXTENDED) {
            throw new OnionException("Expected RELAY_EXTENDED, got " + relayResponse.getRelayCommand());
        }

        byte[] peerPublicKey = relayResponse.getData();
        byte[] sharedSecret = OnionCrypto.performKeyAgreement(keyPair.getPrivate(), peerPublicKey);
        OnionCrypto crypto = new OnionCrypto(sharedSecret, true);

        return new CircuitHop(relay.getAddress(), peerPublicKey, crypto, relay.getFingerprint());
    }

    private byte[] buildExtendPayload(InetSocketAddress address, byte[] publicKey) {
        byte[] ipBytes = address.getAddress().getAddress();
        int port = address.getPort();

        byte[] payload = new byte[ipBytes.length + 2 + publicKey.length];
        System.arraycopy(ipBytes, 0, payload, 0, ipBytes.length);
        payload[ipBytes.length] = (byte) (port >> 8);
        payload[ipBytes.length + 1] = (byte) port;
        System.arraycopy(publicKey, 0, payload, ipBytes.length + 2, publicKey.length);

        return payload;
    }

    private void sendCell(Socket socket, OnionCell cell) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        byte[] data = cell.toBytes();
        out.write(data);
        out.flush();
    }

    private OnionCell receiveCell(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] data = new byte[OnionProtocol.CELL_SIZE];
        in.readFully(data);
        return OnionCell.fromBytes(data);
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }

    public static class RelayInfo {
        private final String fingerprint;
        private final InetSocketAddress address;
        private final byte[] publicKey;
        private final int bandwidth;
        private final boolean isExit;

        public RelayInfo(String fingerprint, InetSocketAddress address, byte[] publicKey, int bandwidth, boolean isExit) {
            this.fingerprint = fingerprint;
            this.address = address;
            this.publicKey = publicKey;
            this.bandwidth = bandwidth;
            this.isExit = isExit;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public int getBandwidth() {
            return bandwidth;
        }

        public boolean isExit() {
            return isExit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelayInfo that = (RelayInfo) o;
            return fingerprint.equals(that.fingerprint);
        }

        @Override
        public int hashCode() {
            return fingerprint.hashCode();
        }
    }
}
