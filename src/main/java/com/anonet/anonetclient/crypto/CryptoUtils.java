/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.crypto
 * Created by: Ashish Kushwaha on 19-01-2026 14:30
 * File: CryptoUtils.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.crypto;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;

public final class CryptoUtils {

    private static final AnonetLogger LOG = AnonetLogger.get(CryptoUtils.class);
    private static final String KEY_ALGORITHM = "EC";
    private static final int EC_KEY_SIZE = 256;
    private static final String HASH_ALGORITHM = "SHA-256";

    private CryptoUtils() {
    }

    public static KeyPair generateKeyPair() {
        LOG.debug("Generating EC P-256 key pair");
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(EC_KEY_SIZE, SecureRandom.getInstanceStrong());
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Failed to generate key pair", e);
            throw new CryptoException("Failed to generate key pair", e);
        }
    }

    public static String computePublicKeyFingerprint(PublicKey publicKey) {
        LOG.trace("Computing public key fingerprint");
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(publicKey.getEncoded());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("Failed to compute fingerprint", e);
        }
    }

    public static String formatFingerprint(String fingerprint) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < fingerprint.length(); i += 4) {
            if (i > 0) {
                formatted.append(":");
            }
            int end = Math.min(i + 4, fingerprint.length());
            formatted.append(fingerprint, i, end);
        }
        return formatted.toString().toUpperCase();
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
