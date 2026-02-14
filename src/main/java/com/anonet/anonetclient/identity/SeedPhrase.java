/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 02-02-2026 12:00
 * File: SeedPhrase.java
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

import com.anonet.anonetclient.logging.AnonetLogger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BIP39-compatible mnemonic seed phrase generation and validation.
 * Generates 12-word mnemonic phrases for deterministic identity recovery.
 */
public final class SeedPhrase {

    private static final AnonetLogger LOG = AnonetLogger.get(SeedPhrase.class);

    private static final int ENTROPY_BITS = 128;
    private static final int ENTROPY_BYTES = ENTROPY_BITS / 8;
    private static final int CHECKSUM_BITS = 4;
    private static final int WORD_COUNT = 12;
    private static final int BITS_PER_WORD = 11;
    private static final int PBKDF2_ITERATIONS = 2048;
    private static final int SEED_LENGTH_BYTES = 64;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512";
    private static final String SALT_PREFIX = "anonet-identity";

    private final List<String> words;
    private final byte[] entropy;

    private SeedPhrase(List<String> words, byte[] entropy) {
        this.words = List.copyOf(words);
        this.entropy = entropy.clone();
    }

    public static SeedPhrase generate() {
        LOG.info("Generating new BIP39 seed phrase");
        SecureRandom random = new SecureRandom();
        byte[] entropy = new byte[ENTROPY_BYTES];
        random.nextBytes(entropy);
        return fromEntropy(entropy);
    }

    public static SeedPhrase fromEntropy(byte[] entropy) {
        if (entropy.length != ENTROPY_BYTES) {
            throw new IllegalArgumentException("Entropy must be " + ENTROPY_BYTES + " bytes");
        }

        byte[] checksum = sha256(entropy);
        int checksumByte = checksum[0] & 0xFF;

        int totalBits = ENTROPY_BITS + CHECKSUM_BITS;
        StringBuilder bitString = new StringBuilder();
        for (byte b : entropy) {
            bitString.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }

        bitString.append(String.format("%8s", Integer.toBinaryString(checksumByte)).replace(' ', '0').substring(0, CHECKSUM_BITS));

        List<String> words = new ArrayList<>();
        String[] wordlist = BIP39Wordlist.WORDS;
        for (int i = 0; i < WORD_COUNT; i++) {
            int start = i * BITS_PER_WORD;
            int end = start + BITS_PER_WORD;
            int index = Integer.parseInt(bitString.substring(start, end), 2);
            words.add(wordlist[index]);
        }

        return new SeedPhrase(words, entropy);
    }

    public static SeedPhrase fromWords(String mnemonic) {
        List<String> words = Arrays.stream(mnemonic.toLowerCase().trim().split("\\s+"))
                .collect(Collectors.toList());
        return fromWords(words);
    }

    public static SeedPhrase fromWords(List<String> words) {
        LOG.debug("Parsing seed phrase from words");
        if (words.size() != WORD_COUNT) {
            throw new IllegalArgumentException("Mnemonic must be " + WORD_COUNT + " words");
        }

        String[] wordlist = BIP39Wordlist.WORDS;
        List<String> wordlistList = Arrays.asList(wordlist);

        StringBuilder bitString = new StringBuilder();
        for (String word : words) {
            int index = wordlistList.indexOf(word.toLowerCase());
            if (index == -1) {
                throw new IllegalArgumentException("Invalid word: " + word);
            }
            bitString.append(String.format("%11s", Integer.toBinaryString(index)).replace(' ', '0'));
        }

        String entropyBits = bitString.substring(0, ENTROPY_BITS);
        String checksumBits = bitString.substring(ENTROPY_BITS);

        byte[] entropy = new byte[ENTROPY_BYTES];
        for (int i = 0; i < ENTROPY_BYTES; i++) {
            int start = i * 8;
            entropy[i] = (byte) Integer.parseInt(entropyBits.substring(start, start + 8), 2);
        }

        byte[] hash = sha256(entropy);
        int expectedChecksum = (hash[0] & 0xFF) >> (8 - CHECKSUM_BITS);
        int actualChecksum = Integer.parseInt(checksumBits, 2);

        if (expectedChecksum != actualChecksum) {
            throw new IllegalArgumentException("Invalid mnemonic checksum");
        }

        return new SeedPhrase(words, entropy);
    }

    public byte[] toSeed() {
        return toSeed("");
    }

    public byte[] toSeed(String passphrase) {
        LOG.debug("Deriving seed with PBKDF2");
        String mnemonic = String.join(" ", words);
        String salt = SALT_PREFIX + passphrase;

        try {
            PBEKeySpec spec = new PBEKeySpec(
                    mnemonic.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    PBKDF2_ITERATIONS,
                    SEED_LENGTH_BYTES * 8
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IdentityException("Failed to derive seed", e);
        }
    }

    public List<String> getWords() {
        return words;
    }

    public String getMnemonic() {
        return String.join(" ", words);
    }

    public byte[] getEntropy() {
        return entropy.clone();
    }

    public static boolean isValid(String mnemonic) {
        try {
            fromWords(mnemonic);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Override
    public String toString() {
        return words.get(0) + " ... " + words.get(words.size() - 1) + " (" + WORD_COUNT + " words)";
    }
}
