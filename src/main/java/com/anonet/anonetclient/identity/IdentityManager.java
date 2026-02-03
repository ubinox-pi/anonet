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

import java.io.IOException;
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

/**
 * Manages the local user's identity - creation, persistence, and recovery.
 * Supports deterministic key generation from BIP39 seed phrases.
 */
public final class IdentityManager {

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
        if (identityExists()) {
            return loadIdentity();
        }
        return createAndPersistIdentity();
    }

    public LocalIdentity createFromSeedPhrase(SeedPhrase seedPhrase) {
        LocalIdentity identity = DeterministicIdentity.deriveFromSeedPhrase(seedPhrase);
        persistIdentityWithSeed(identity, seedPhrase);
        return identity;
    }

    public LocalIdentity restoreFromSeedPhrase(SeedPhrase seedPhrase) {
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

            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            return new LocalIdentity(publicKey, privateKey);
        } catch (IOException e) {
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

            Files.write(privateKeyPath, privateKeyBytes);
            Files.write(publicKeyPath, publicKeyBytes);
            Files.write(seedHashPath, seedHash);

        } catch (IOException e) {
            throw new IdentityException("Failed to persist identity", e);
        }
    }

    public void deleteIdentity() {
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

    private byte[] hashSeed(byte[] seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(seed);
        } catch (NoSuchAlgorithmException e) {
            throw new IdentityException("SHA-256 not available", e);
        }
    }
}
