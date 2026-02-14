/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.onion
 * Created by: Ashish Kushwaha on 03-02-2026 14:10
 * File: OnionCrypto.java
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

import com.anonet.anonetclient.crypto.session.HKDF;
import com.anonet.anonetclient.logging.AnonetLogger;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

public final class OnionCrypto {

    private static final AnonetLogger LOG = AnonetLogger.get(OnionCrypto.class);

    private static final String CURVE = "secp256r1";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_SIZE = 12;
    private static final int AES_KEY_SIZE = 32;
    private static final int DIGEST_SIZE = 20;
    private static final byte[] HKDF_SALT = "anonet-onion-v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKey forwardKey;
    private final SecretKey backwardKey;
    private final byte[] forwardDigest;
    private final byte[] backwardDigest;
    private long forwardCounter;
    private long backwardCounter;

    public OnionCrypto(byte[] sharedSecret, boolean isInitiator) {
        byte[] fwdKeyBytes = HKDF.deriveKey(sharedSecret, HKDF_SALT, "onion-forward-key".getBytes(StandardCharsets.UTF_8), AES_KEY_SIZE);
        byte[] bwdKeyBytes = HKDF.deriveKey(sharedSecret, HKDF_SALT, "onion-backward-key".getBytes(StandardCharsets.UTF_8), AES_KEY_SIZE);
        byte[] fwdDigestBytes = HKDF.deriveKey(sharedSecret, HKDF_SALT, "onion-forward-digest".getBytes(StandardCharsets.UTF_8), DIGEST_SIZE);
        byte[] bwdDigestBytes = HKDF.deriveKey(sharedSecret, HKDF_SALT, "onion-backward-digest".getBytes(StandardCharsets.UTF_8), DIGEST_SIZE);

        if (isInitiator) {
            this.forwardKey = new SecretKeySpec(fwdKeyBytes, "AES");
            this.backwardKey = new SecretKeySpec(bwdKeyBytes, "AES");
            this.forwardDigest = fwdDigestBytes;
            this.backwardDigest = bwdDigestBytes;
        } else {
            this.forwardKey = new SecretKeySpec(bwdKeyBytes, "AES");
            this.backwardKey = new SecretKeySpec(fwdKeyBytes, "AES");
            this.forwardDigest = bwdDigestBytes;
            this.backwardDigest = fwdDigestBytes;
        }

        this.forwardCounter = 0;
        this.backwardCounter = 0;
    }

    public byte[] encryptForward(byte[] plaintext) {
        return encrypt(plaintext, forwardKey, forwardCounter++);
    }

    public byte[] decryptForward(byte[] ciphertext) {
        return decrypt(ciphertext, forwardKey, forwardCounter++);
    }

    public byte[] encryptBackward(byte[] plaintext) {
        return encrypt(plaintext, backwardKey, backwardCounter++);
    }

    public byte[] decryptBackward(byte[] ciphertext) {
        return decrypt(ciphertext, backwardKey, backwardCounter++);
    }

    private byte[] encrypt(byte[] plaintext, SecretKey key, long counter) {
        try {
            byte[] nonce = computeNonce(counter);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            LOG.error("Encryption failed", e);
            throw new OnionException("Encryption failed", e);
        }
    }

    private byte[] decrypt(byte[] ciphertext, SecretKey key, long counter) {
        try {
            byte[] nonce = computeNonce(counter);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            LOG.error("Decryption failed", e);
            throw new OnionException("Decryption failed", e);
        }
    }

    private byte[] computeNonce(long counter) {
        ByteBuffer buffer = ByteBuffer.allocate(GCM_NONCE_SIZE);
        buffer.putLong(counter);
        buffer.putInt(0);
        return buffer.array();
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
            LOG.debug("Generated onion key pair");
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new OnionException("Key pair generation failed", e);
        }
    }

    public static byte[] performKeyAgreement(PrivateKey privateKey, byte[] peerPublicKeyBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(peerPublicKeyBytes);
            PublicKey peerPublicKey = keyFactory.generatePublic(keySpec);

            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(peerPublicKey, true);
            LOG.debug("Onion ECDH key agreement completed");
            return keyAgreement.generateSecret();
        } catch (Exception e) {
            throw new OnionException("Key agreement failed", e);
        }
    }

    public static byte[] computeDigest(byte[] data) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(data);
        } catch (Exception e) {
            throw new OnionException("Digest computation failed", e);
        }
    }
}
