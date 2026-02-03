/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 19-01-2026 14:30
 * File: LocalIdentity.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.identity;

import com.anonet.anonetclient.crypto.CryptoUtils;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

public final class LocalIdentity {

    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final String fingerprint;

    public LocalIdentity(KeyPair keyPair) {
        this(keyPair.getPublic(), keyPair.getPrivate());
    }

    public LocalIdentity(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.fingerprint = computeFingerprint(publicKey);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getFormattedFingerprint() {
        return CryptoUtils.formatFingerprint(fingerprint);
    }

    public String getShortFingerprint() {
        return fingerprint.substring(0, 8).toUpperCase();
    }

    private static String computeFingerprint(PublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IdentityException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
