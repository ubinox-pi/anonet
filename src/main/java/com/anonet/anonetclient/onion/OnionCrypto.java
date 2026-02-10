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

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public final class OnionCrypto {

    private static final String CURVE = "secp256r1";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_SIZE = 12;
    private static final int AES_KEY_SIZE = 32;

    private final SecretKey forwardKey;
    private final SecretKey backwardKey;
    private final byte[] forwardDigest;
    private final byte[] backwardDigest;
    private long forwardCounter;
    private long backwardCounter;

    public OnionCrypto(byte[] sharedSecret, boolean isInitiator) {
        byte[] keyMaterial = deriveKeyMaterial(sharedSecret);

        byte[] fwdKeyBytes = Arrays.copyOfRange(keyMaterial, 0, AES_KEY_SIZE);
        byte[] bwdKeyBytes = Arrays.copyOfRange(keyMaterial, AES_KEY_SIZE, AES_KEY_SIZE * 2);
        byte[] fwdDigestBytes = Arrays.copyOfRange(keyMaterial, AES_KEY_SIZE * 2, AES_KEY_SIZE * 2 + 20);
        byte[] bwdDigestBytes = Arrays.copyOfRange(keyMaterial, AES_KEY_SIZE * 2 + 20, AES_KEY_SIZE * 2 + 40);

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
            throw new OnionException("Decryption failed", e);
        }
    }

    private byte[] computeNonce(long counter) {
        ByteBuffer buffer = ByteBuffer.allocate(GCM_NONCE_SIZE);
        buffer.putLong(counter);
        buffer.putInt(0);
        return buffer.array();
    }

    private byte[] deriveKeyMaterial(byte[] sharedSecret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash1 = sha256.digest(concat(sharedSecret, new byte[]{0x01}));
            byte[] hash2 = sha256.digest(concat(sharedSecret, new byte[]{0x02}));
            byte[] hash3 = sha256.digest(concat(sharedSecret, new byte[]{0x03}));
            byte[] hash4 = sha256.digest(concat(sharedSecret, new byte[]{0x04}));
            return concat(hash1, hash2, hash3, hash4);
        } catch (Exception e) {
            throw new OnionException("Key derivation failed", e);
        }
    }

    private byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] arr : arrays) {
            totalLength += arr.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
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
