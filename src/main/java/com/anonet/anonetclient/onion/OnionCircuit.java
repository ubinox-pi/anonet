/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.onion
 * Created by: Ashish Kushwaha on 03-02-2026 14:20
 * File: OnionCircuit.java
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

import com.anonet.anonetclient.onion.OnionProtocol.OnionCell;
import com.anonet.anonetclient.onion.OnionProtocol.RelayCell;
import com.anonet.anonetclient.onion.OnionProtocol.Command;
import com.anonet.anonetclient.onion.OnionProtocol.RelayCommand;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class OnionCircuit {

    public enum State {
        BUILDING,
        READY,
        EXTENDING,
        DESTROYED,
        FAILED
    }

    private final int circuitId;
    private final List<CircuitHop> hops;
    private volatile State state;
    private final AtomicInteger streamIdCounter;

    public OnionCircuit() {
        this.circuitId = new SecureRandom().nextInt(Integer.MAX_VALUE);
        this.hops = new ArrayList<>();
        this.state = State.BUILDING;
        this.streamIdCounter = new AtomicInteger(1);
    }

    public int getCircuitId() {
        return circuitId;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getHopCount() {
        return hops.size();
    }

    public void addHop(CircuitHop hop) {
        hops.add(hop);
    }

    public CircuitHop getHop(int index) {
        if (index < 0 || index >= hops.size()) {
            throw new OnionException("Invalid hop index: " + index);
        }
        return hops.get(index);
    }

    public CircuitHop getLastHop() {
        if (hops.isEmpty()) {
            return null;
        }
        return hops.get(hops.size() - 1);
    }

    public int nextStreamId() {
        return streamIdCounter.getAndIncrement();
    }

    public byte[] encryptOutbound(byte[] payload) {
        byte[] encrypted = payload;
        for (int i = hops.size() - 1; i >= 0; i--) {
            encrypted = hops.get(i).getCrypto().encryptForward(encrypted);
        }
        return encrypted;
    }

    public byte[] decryptInbound(byte[] payload) {
        byte[] decrypted = payload;
        for (CircuitHop hop : hops) {
            decrypted = hop.getCrypto().decryptBackward(decrypted);
        }
        return decrypted;
    }

    public OnionCell createRelayCell(RelayCommand relayCmd, int streamId, byte[] data) {
        RelayCell relayCell = new RelayCell(relayCmd, streamId, data);
        byte[] encrypted = encryptOutbound(relayCell.toPayload());
        return new OnionCell(circuitId, Command.RELAY, encrypted);
    }

    public RelayCell processRelayCell(OnionCell cell) {
        if (cell.getCommand() != Command.RELAY) {
            throw new OnionException("Expected RELAY cell, got " + cell.getCommand());
        }
        byte[] decrypted = decryptInbound(cell.getPayload());
        return RelayCell.fromPayload(decrypted);
    }

    public void destroy() {
        state = State.DESTROYED;
        hops.clear();
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public static class CircuitHop {
        private final InetSocketAddress address;
        private final byte[] publicKey;
        private final OnionCrypto crypto;
        private final String fingerprint;

        public CircuitHop(InetSocketAddress address, byte[] publicKey, OnionCrypto crypto, String fingerprint) {
            this.address = address;
            this.publicKey = publicKey;
            this.crypto = crypto;
            this.fingerprint = fingerprint;
        }

        public InetSocketAddress getAddress() {
            return address;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public OnionCrypto getCrypto() {
            return crypto;
        }

        public String getFingerprint() {
            return fingerprint;
        }
    }

    @Override
    public String toString() {
        return "OnionCircuit[id=" + circuitId + ", hops=" + hops.size() + ", state=" + state + "]";
    }
}
