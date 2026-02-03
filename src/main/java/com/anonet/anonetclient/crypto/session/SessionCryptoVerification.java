/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.crypto.session
 * Created by: Ashish Kushwaha on 19-01-2026 22:10
 * File: SessionCryptoVerification.java
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

import com.anonet.anonetclient.identity.LocalIdentity;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;

public final class SessionCryptoVerification {

    private SessionCryptoVerification() {
    }

    public static VerificationResult runAllTests() {
        StringBuilder results = new StringBuilder();
        int passed = 0;
        int failed = 0;

        results.append("=== PHASE 3 SESSION CRYPTOGRAPHY VERIFICATION ===\n\n");

        if (testKeyAgreementProducesSameKeys()) {
            results.append("✓ Key Agreement: Peers derive identical session keys\n");
            passed++;
        } else {
            results.append("✗ Key Agreement: FAILED\n");
            failed++;
        }

        if (testEncryptDecryptRoundtrip()) {
            results.append("✓ Encryption/Decryption: Roundtrip successful\n");
            passed++;
        } else {
            results.append("✗ Encryption/Decryption: FAILED\n");
            failed++;
        }

        if (testTamperedCiphertextFails()) {
            results.append("✓ Tamper Detection: Modified ciphertext rejected\n");
            passed++;
        } else {
            results.append("✗ Tamper Detection: FAILED\n");
            failed++;
        }

        if (testInvalidSignatureFails()) {
            results.append("✓ Signature Verification: Invalid signature rejected\n");
            passed++;
        } else {
            results.append("✗ Signature Verification: FAILED\n");
            failed++;
        }

        if (testReplayProtection()) {
            results.append("✓ Replay Protection: Duplicate messages rejected\n");
            passed++;
        } else {
            results.append("✗ Replay Protection: FAILED\n");
            failed++;
        }

        if (testForwardSecrecy()) {
            results.append("✓ Forward Secrecy: Different sessions produce different keys\n");
            passed++;
        } else {
            results.append("✗ Forward Secrecy: FAILED\n");
            failed++;
        }

        results.append("\n=== RESULTS: ").append(passed).append("/").append(passed + failed).append(" tests passed ===\n");

        return new VerificationResult(passed, failed, results.toString());
    }

    private static boolean testKeyAgreementProducesSameKeys() {
        try {
            LocalIdentity peerA = createTestIdentity();
            LocalIdentity peerB = createTestIdentity();

            SessionKeyAgreement agreementA = new SessionKeyAgreement(peerA);
            SessionKeyAgreement agreementB = new SessionKeyAgreement(peerB);

            SignedEphemeralKey signedKeyA = agreementA.generateSignedEphemeralKey();
            SignedEphemeralKey signedKeyB = agreementB.generateSignedEphemeralKey();

            SessionKeys keysA = agreementA.completeKeyAgreement(signedKeyB);
            SessionKeys keysB = agreementB.completeKeyAgreement(signedKeyA);

            byte[] nonceA = keysA.computeNonce(0);
            byte[] nonceB = keysB.computeNonce(0);

            boolean match = Arrays.equals(nonceA, nonceB);

            keysA.destroy();
            keysB.destroy();

            return match;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean testEncryptDecryptRoundtrip() {
        try {
            LocalIdentity peerA = createTestIdentity();
            LocalIdentity peerB = createTestIdentity();

            SessionKeyAgreement agreementA = new SessionKeyAgreement(peerA);
            SessionKeyAgreement agreementB = new SessionKeyAgreement(peerB);

            SignedEphemeralKey signedKeyA = agreementA.generateSignedEphemeralKey();
            SignedEphemeralKey signedKeyB = agreementB.generateSignedEphemeralKey();

            SessionKeys keysA = agreementA.completeKeyAgreement(signedKeyB);
            SessionKeys keysB = agreementB.completeKeyAgreement(signedKeyA);

            SecureChannel channelA = new SecureChannel(keysA);
            SecureChannel channelB = new SecureChannel(keysB);

            String originalMessage = "Hello, secure world! This is a test message.";
            byte[] plaintext = originalMessage.getBytes(StandardCharsets.UTF_8);

            SecureChannel.EncryptedMessage encrypted = channelA.encrypt(plaintext);
            byte[] decrypted = channelB.decrypt(encrypted);

            boolean success = Arrays.equals(plaintext, decrypted);

            channelA.close();
            channelB.close();

            return success;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean testTamperedCiphertextFails() {
        try {
            LocalIdentity peerA = createTestIdentity();
            LocalIdentity peerB = createTestIdentity();

            SessionKeyAgreement agreementA = new SessionKeyAgreement(peerA);
            SessionKeyAgreement agreementB = new SessionKeyAgreement(peerB);

            SignedEphemeralKey signedKeyA = agreementA.generateSignedEphemeralKey();
            SignedEphemeralKey signedKeyB = agreementB.generateSignedEphemeralKey();

            SessionKeys keysA = agreementA.completeKeyAgreement(signedKeyB);
            SessionKeys keysB = agreementB.completeKeyAgreement(signedKeyA);

            SecureChannel channelA = new SecureChannel(keysA);
            SecureChannel channelB = new SecureChannel(keysB);

            byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);
            SecureChannel.EncryptedMessage encrypted = channelA.encrypt(plaintext);

            byte[] tamperedCiphertext = encrypted.getCiphertext();
            tamperedCiphertext[0] ^= 0xFF;
            SecureChannel.EncryptedMessage tamperedMessage =
                    new SecureChannel.EncryptedMessage(tamperedCiphertext, encrypted.getSequence());

            try {
                channelB.decrypt(tamperedMessage);
                return false;
            } catch (SessionCryptoException e) {
                channelA.close();
                channelB.close();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean testInvalidSignatureFails() {
        try {
            LocalIdentity peerA = createTestIdentity();
            LocalIdentity peerB = createTestIdentity();
            LocalIdentity attacker = createTestIdentity();

            SessionKeyAgreement agreementA = new SessionKeyAgreement(peerA);
            SessionKeyAgreement agreementAttacker = new SessionKeyAgreement(attacker);

            SignedEphemeralKey signedKeyA = agreementA.generateSignedEphemeralKey();
            SignedEphemeralKey attackerKey = agreementAttacker.generateSignedEphemeralKey();

            SignedEphemeralKey forgedKey = new SignedEphemeralKey(
                    attackerKey.getEphemeralPublicKey(),
                    attackerKey.getSignature(),
                    peerB.getPublicKey().getEncoded()
            );

            try {
                agreementA.completeKeyAgreement(forgedKey);
                return false;
            } catch (SessionCryptoException e) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean testReplayProtection() {
        try {
            LocalIdentity peerA = createTestIdentity();
            LocalIdentity peerB = createTestIdentity();

            SessionKeyAgreement agreementA = new SessionKeyAgreement(peerA);
            SessionKeyAgreement agreementB = new SessionKeyAgreement(peerB);

            SignedEphemeralKey signedKeyA = agreementA.generateSignedEphemeralKey();
            SignedEphemeralKey signedKeyB = agreementB.generateSignedEphemeralKey();

            SessionKeys keysA = agreementA.completeKeyAgreement(signedKeyB);
            SessionKeys keysB = agreementB.completeKeyAgreement(signedKeyA);

            SecureChannel channelA = new SecureChannel(keysA);
            SecureChannel channelB = new SecureChannel(keysB);

            byte[] plaintext = "Message".getBytes(StandardCharsets.UTF_8);
            SecureChannel.EncryptedMessage encrypted = channelA.encrypt(plaintext);

            channelB.decrypt(encrypted);

            try {
                channelB.decrypt(encrypted);
                return false;
            } catch (SessionCryptoException e) {
                channelA.close();
                channelB.close();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean testForwardSecrecy() {
        try {
            LocalIdentity peerA = createTestIdentity();
            LocalIdentity peerB = createTestIdentity();

            SessionKeyAgreement agreement1A = new SessionKeyAgreement(peerA);
            SessionKeyAgreement agreement1B = new SessionKeyAgreement(peerB);
            SignedEphemeralKey signed1A = agreement1A.generateSignedEphemeralKey();
            SignedEphemeralKey signed1B = agreement1B.generateSignedEphemeralKey();
            SessionKeys keys1A = agreement1A.completeKeyAgreement(signed1B);

            SessionKeyAgreement agreement2A = new SessionKeyAgreement(peerA);
            SessionKeyAgreement agreement2B = new SessionKeyAgreement(peerB);
            SignedEphemeralKey signed2A = agreement2A.generateSignedEphemeralKey();
            SignedEphemeralKey signed2B = agreement2B.generateSignedEphemeralKey();
            SessionKeys keys2A = agreement2A.completeKeyAgreement(signed2B);

            byte[] nonce1 = keys1A.computeNonce(0);
            byte[] nonce2 = keys2A.computeNonce(0);

            boolean different = !Arrays.equals(nonce1, nonce2);

            keys1A.destroy();
            keys2A.destroy();

            return different;
        } catch (Exception e) {
            return false;
        }
    }

    private static LocalIdentity createTestIdentity() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(256, SecureRandom.getInstanceStrong());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            return new LocalIdentity(keyPair.getPublic(), keyPair.getPrivate());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test identity", e);
        }
    }

    public static final class VerificationResult {

        private final int passed;
        private final int failed;
        private final String details;

        public VerificationResult(int passed, int failed, String details) {
            this.passed = passed;
            this.failed = failed;
            this.details = details;
        }

        public int getPassed() {
            return passed;
        }

        public int getFailed() {
            return failed;
        }

        public String getDetails() {
            return details;
        }

        public boolean isAllPassed() {
            return failed == 0;
        }
    }
}
