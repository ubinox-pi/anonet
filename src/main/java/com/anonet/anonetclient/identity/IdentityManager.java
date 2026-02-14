/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 19-01-2026 14:30
 * File: IdentityManager.java
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

import com.anonet.anonetclient.crypto.session.HKDF;
import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manages the local user's identity - creation, persistence, and recovery.
 * Supports deterministic key generation from BIP39 seed phrases.
 */
public final class IdentityManager {

    private static final AnonetLogger LOG = AnonetLogger.get(IdentityManager.class);

    private static final String ANONET_DIRECTORY = ".anonet";
    private static final String PRIVATE_KEY_FILE = "private.key";
    private static final String PUBLIC_KEY_FILE = "public.key";
    private static final String SEED_HASH_FILE = "seed.hash";
    private static final String KEY_ALGORITHM = "EC";
    private static final int EC_KEY_SIZE = 256;

    private final Path anonetDirectory;
    private final Path privateKeyPath;
    private final Path publicKeyPath;
    private final Path seedHashPath;

    public IdentityManager() {
        this.anonetDirectory = Path.of(System.getProperty("user.home"), ANONET_DIRECTORY);
        this.privateKeyPath = anonetDirectory.resolve(PRIVATE_KEY_FILE);
        this.publicKeyPath = anonetDirectory.resolve(PUBLIC_KEY_FILE);
        this.seedHashPath = anonetDirectory.resolve(SEED_HASH_FILE);
    }

    public LocalIdentity loadOrCreateIdentity() {
        LOG.info("Loading or creating identity");
        if (identityExists()) {
            LOG.info("Identity exists, loading from disk");
            return loadIdentity();
        }
        LOG.info("No identity found, creating new identity");
        return createAndPersistIdentity();
    }

    public LocalIdentity createFromSeedPhrase(SeedPhrase seedPhrase) {
        LOG.info("Creating identity from seed phrase");
        LocalIdentity identity = DeterministicIdentity.deriveFromSeedPhrase(seedPhrase);
        persistIdentityWithSeed(identity, seedPhrase);
        return identity;
    }

    public LocalIdentity restoreFromSeedPhrase(SeedPhrase seedPhrase) {
        LOG.info("Restoring identity from seed phrase");
        LocalIdentity identity = DeterministicIdentity.deriveFromSeedPhrase(seedPhrase);
        persistIdentityWithSeed(identity, seedPhrase);
        return identity;
    }

    public boolean identityExists() {
        return Files.exists(privateKeyPath) && Files.exists(publicKeyPath);
    }

    public boolean hasSeedHash() {
        return Files.exists(seedHashPath);
    }

    public boolean verifySeedPhrase(SeedPhrase seedPhrase) {
        if (!hasSeedHash()) {
            return false;
        }
        try {
            byte[] storedHash = Files.readAllBytes(seedHashPath);
            byte[] providedHash = hashSeed(seedPhrase.toSeed());
            return Arrays.equals(storedHash, providedHash);
        } catch (IOException e) {
            return false;
        }
    }

    private LocalIdentity loadIdentity() {
        try {
            byte[] privateKeyBytes = Files.readAllBytes(privateKeyPath);
            byte[] publicKeyBytes = Files.readAllBytes(publicKeyPath);

            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);

            if (privateKeyBytes.length > 0 && privateKeyBytes[0] != 0x30) {
                if (!hasSeedHash()) {
                    throw new IdentityException("Encrypted private key but no seed hash for decryption");
                }
                byte[] seedHash = Files.readAllBytes(seedHashPath);
                privateKeyBytes = decryptPrivateKey(privateKeyBytes, seedHash);
            } else if (privateKeyBytes.length > 0 && privateKeyBytes[0] == 0x30 && hasSeedHash()) {
                LOG.warn("Legacy unencrypted private key detected, re-encrypting");
                byte[] seedHash = Files.readAllBytes(seedHashPath);
                byte[] encrypted = encryptPrivateKey(privateKeyBytes, seedHash);
                Files.write(privateKeyPath, encrypted);
            }

            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            return new LocalIdentity(publicKey, privateKey);
        } catch (IOException e) {
            LOG.error("Failed to read identity files", e);
            throw new IdentityException("Failed to read identity files", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IdentityException("Key algorithm not supported", e);
        } catch (InvalidKeySpecException e) {
            throw new IdentityException("Invalid key specification", e);
        }
    }

    private LocalIdentity createAndPersistIdentity() {
        try {
            SeedPhrase seedPhrase = SeedPhrase.generate();
            LocalIdentity identity = DeterministicIdentity.deriveFromSeedPhrase(seedPhrase);
            persistIdentityWithSeed(identity, seedPhrase);
            return identity;
        } catch (Exception e) {
            throw new IdentityException("Failed to create identity", e);
        }
    }

    private void persistIdentityWithSeed(LocalIdentity identity, SeedPhrase seedPhrase) {
        try {
            Files.createDirectories(anonetDirectory);

            byte[] privateKeyBytes = identity.getPrivateKey().getEncoded();
            byte[] publicKeyBytes = identity.getPublicKey().getEncoded();
            byte[] seedHash = hashSeed(seedPhrase.toSeed());

            byte[] encryptedPrivateKey = encryptPrivateKey(privateKeyBytes, seedHash);

            Files.write(privateKeyPath, encryptedPrivateKey);
            Files.write(publicKeyPath, publicKeyBytes);
            Files.write(seedHashPath, seedHash);

            LOG.info("Identity persisted to %s", anonetDirectory);
        } catch (IOException e) {
            throw new IdentityException("Failed to persist identity", e);
        }
    }

    public void deleteIdentity() {
        LOG.warn("Deleting identity files");
        try {
            Files.deleteIfExists(privateKeyPath);
            Files.deleteIfExists(publicKeyPath);
            Files.deleteIfExists(seedHashPath);
        } catch (IOException e) {
            throw new IdentityException("Failed to delete identity", e);
        }
    }

    public Path getAnonetDirectory() {
        return anonetDirectory;
    }

    public String getIdentityInfo() {
        if (!identityExists()) {
            return "No identity found";
        }
        LocalIdentity identity = loadIdentity();
        return "Fingerprint: " + identity.getFormattedFingerprint() + "\n" +
               "Seed backup: " + (hasSeedHash() ? "Available" : "Not available");
    }

    private byte[] encryptPrivateKey(byte[] pkcs8Bytes, byte[] seedHash) {
        try {
            byte[] aesKey = HKDF.deriveKey(seedHash, "anonet-pk-enc".getBytes(StandardCharsets.UTF_8),
                    "private-key".getBytes(StandardCharsets.UTF_8), 32);
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), spec);
            byte[] ciphertext = cipher.doFinal(pkcs8Bytes);

            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IdentityException("Failed to encrypt private key", e);
        }
    }

    private byte[] decryptPrivateKey(byte[] encrypted, byte[] seedHash) {
        try {
            byte[] aesKey = HKDF.deriveKey(seedHash, "anonet-pk-enc".getBytes(StandardCharsets.UTF_8),
                    "private-key".getBytes(StandardCharsets.UTF_8), 32);
            byte[] nonce = Arrays.copyOfRange(encrypted, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, 12, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IdentityException("Failed to decrypt private key", e);
        }
    }

    private byte[] hashSeed(byte[] seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(seed);
        } catch (NoSuchAlgorithmException e) {
            throw new IdentityException("SHA-256 not available", e);
        }
    }
}
