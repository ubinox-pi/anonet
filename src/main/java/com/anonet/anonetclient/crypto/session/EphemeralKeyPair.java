/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.crypto.session
 * Created by: Ashish Kushwaha on 19-01-2026 22:10
 * File: EphemeralKeyPair.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.crypto.session;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

public final class EphemeralKeyPair {

    private static final String KEY_ALGORITHM = "EC";
    private static final int KEY_SIZE = 256;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private EphemeralKeyPair(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public static EphemeralKeyPair generate() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE, SecureRandom.getInstanceStrong());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            return new EphemeralKeyPair(keyPair.getPublic(), keyPair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new SessionCryptoException("Failed to generate ephemeral key pair", e);
        }
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public byte[] getPublicKeyBytes() {
        return publicKey.getEncoded();
    }
}
