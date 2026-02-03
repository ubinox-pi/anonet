/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.dht
 * Created by: Ashish Kushwaha on 02-02-2026 19:05
 * File: PeerAnnouncement.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.dht;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class PeerAnnouncement {

    private static final int MAX_PORT_CANDIDATES = 5;
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final String username;
    private final String fingerprint;
    private final byte[] publicKeyBytes;
    private final List<Integer> portCandidates;
    private final long timestamp;
    private final byte[] signature;

    private PeerAnnouncement(String username, String fingerprint, byte[] publicKeyBytes,
                             List<Integer> portCandidates, long timestamp, byte[] signature) {
        this.username = username;
        this.fingerprint = fingerprint;
        this.publicKeyBytes = publicKeyBytes;
        this.portCandidates = new ArrayList<>(portCandidates);
        this.timestamp = timestamp;
        this.signature = signature;
    }

    public static PeerAnnouncement create(String username, String fingerprint,
                                          PublicKey publicKey, List<Integer> portCandidates,
                                          PrivateKey privateKey) {
        byte[] pubKeyBytes = publicKey.getEncoded();
        long timestamp = System.currentTimeMillis();

        byte[] dataToSign = buildSignableData(username, fingerprint, pubKeyBytes, portCandidates, timestamp);
        byte[] signature = sign(dataToSign, privateKey);

        return new PeerAnnouncement(username, fingerprint, pubKeyBytes, portCandidates, timestamp, signature);
    }

    public String getUsername() {
        return username;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public byte[] getPublicKeyBytes() {
        return publicKeyBytes.clone();
    }

    public PublicKey getPublicKey() {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new DhtException("Failed to decode public key", e);
        }
    }

    public List<Integer> getPortCandidates() {
        return new ArrayList<>(portCandidates);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired(long maxAgeMs) {
        return System.currentTimeMillis() - timestamp > maxAgeMs;
    }

    public boolean verify() {
        try {
            byte[] dataToVerify = buildSignableData(username, fingerprint, publicKeyBytes, portCandidates, timestamp);
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(getPublicKey());
            sig.update(dataToVerify);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public byte[] toBytes() {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] fingerprintBytes = fingerprint.getBytes(StandardCharsets.UTF_8);

        int size = 2 + usernameBytes.length +
                   2 + fingerprintBytes.length +
                   2 + publicKeyBytes.length +
                   1 + portCandidates.size() * 2 +
                   8 +
                   2 + signature.length;

        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.putShort((short) usernameBytes.length);
        buffer.put(usernameBytes);

        buffer.putShort((short) fingerprintBytes.length);
        buffer.put(fingerprintBytes);

        buffer.putShort((short) publicKeyBytes.length);
        buffer.put(publicKeyBytes);

        buffer.put((byte) portCandidates.size());
        for (int port : portCandidates) {
            buffer.putShort((short) port);
        }

        buffer.putLong(timestamp);

        buffer.putShort((short) signature.length);
        buffer.put(signature);

        return buffer.array();
    }

    public static PeerAnnouncement fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int usernameLen = buffer.getShort() & 0xFFFF;
        byte[] usernameBytes = new byte[usernameLen];
        buffer.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.UTF_8);

        int fingerprintLen = buffer.getShort() & 0xFFFF;
        byte[] fingerprintBytes = new byte[fingerprintLen];
        buffer.get(fingerprintBytes);
        String fingerprint = new String(fingerprintBytes, StandardCharsets.UTF_8);

        int pubKeyLen = buffer.getShort() & 0xFFFF;
        byte[] publicKeyBytes = new byte[pubKeyLen];
        buffer.get(publicKeyBytes);

        int numPorts = buffer.get() & 0xFF;
        List<Integer> portCandidates = new ArrayList<>(numPorts);
        for (int i = 0; i < numPorts; i++) {
            portCandidates.add(buffer.getShort() & 0xFFFF);
        }

        long timestamp = buffer.getLong();

        int sigLen = buffer.getShort() & 0xFFFF;
        byte[] signature = new byte[sigLen];
        buffer.get(signature);

        return new PeerAnnouncement(username, fingerprint, publicKeyBytes, portCandidates, timestamp, signature);
    }

    public NodeId getDhtKey() {
        return NodeId.fromString(username);
    }

    public NodeId getFingerprintKey() {
        return NodeId.fromString(fingerprint);
    }

    private static byte[] buildSignableData(String username, String fingerprint, byte[] publicKeyBytes,
                                            List<Integer> portCandidates, long timestamp) {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] fingerprintBytes = fingerprint.getBytes(StandardCharsets.UTF_8);

        int size = usernameBytes.length + fingerprintBytes.length + publicKeyBytes.length +
                   portCandidates.size() * 2 + 8;
        ByteBuffer buffer = ByteBuffer.allocate(size);

        buffer.put(usernameBytes);
        buffer.put(fingerprintBytes);
        buffer.put(publicKeyBytes);
        for (int port : portCandidates) {
            buffer.putShort((short) port);
        }
        buffer.putLong(timestamp);

        return buffer.array();
    }

    private static byte[] sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (Exception e) {
            throw new DhtException("Failed to sign announcement", e);
        }
    }

    @Override
    public String toString() {
        return "PeerAnnouncement[user=" + username + ", ports=" + portCandidates.size() + "]";
    }
}
