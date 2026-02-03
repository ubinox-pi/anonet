/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.relay
 * Created by: Ashish Kushwaha on 03-02-2026 11:10
 * File: RelaySession.java
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

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;

public final class RelaySession implements Closeable {

    public enum State {
        CONNECTING,
        CONNECTED,
        WAITING_FOR_PEER,
        RELAYING,
        CLOSED
    }

    private final String sessionId;
    private final String localFingerprint;
    private final byte[] localPublicKey;
    private volatile String peerFingerprint;
    private volatile State state;
    private volatile Instant createdAt;
    private volatile Instant lastActivity;
    private Socket socket;

    public RelaySession(String localFingerprint, byte[] localPublicKey) {
        this.sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.localFingerprint = localFingerprint;
        this.localPublicKey = localPublicKey;
        this.state = State.CONNECTING;
        this.createdAt = Instant.now();
        this.lastActivity = Instant.now();
    }

    public RelaySession(String sessionId, String localFingerprint, byte[] localPublicKey) {
        this.sessionId = sessionId;
        this.localFingerprint = localFingerprint;
        this.localPublicKey = localPublicKey;
        this.state = State.CONNECTING;
        this.createdAt = Instant.now();
        this.lastActivity = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLocalFingerprint() {
        return localFingerprint;
    }

    public byte[] getLocalPublicKey() {
        return localPublicKey;
    }

    public String getPeerFingerprint() {
        return peerFingerprint;
    }

    public void setPeerFingerprint(String fingerprint) {
        this.peerFingerprint = fingerprint;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        this.lastActivity = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void updateActivity() {
        this.lastActivity = Instant.now();
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public boolean isActive() {
        return state == State.CONNECTED || state == State.RELAYING || state == State.WAITING_FOR_PEER;
    }

    public boolean isTimedOut(long timeoutMs) {
        return System.currentTimeMillis() - lastActivity.toEpochMilli() > timeoutMs;
    }

    @Override
    public void close() throws IOException {
        state = State.CLOSED;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public String toString() {
        return "RelaySession[id=" + sessionId + ", state=" + state +
               ", local=" + localFingerprint.substring(0, 8) + "]";
    }
}
