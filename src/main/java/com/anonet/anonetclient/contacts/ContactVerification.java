/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.contacts
 * Created by: Ashish Kushwaha on 10-02-2026 12:00
 * File: ContactVerification.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.contacts;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

public final class ContactVerification {

    private static final AnonetLogger LOG = AnonetLogger.get(ContactVerification.class);

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String HASH_ALGORITHM = "SHA-256";

    public boolean verifyFingerprint(PublicKey publicKey, String expectedFingerprint) {
        String computed = computeFingerprint(publicKey);
        return computed.equalsIgnoreCase(expectedFingerprint.replace(":", ""));
    }

    public boolean verifySignature(PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            boolean valid = sig.verify(signature);
            LOG.debug("Signature verification: %s", valid ? "valid" : "invalid");
            return valid;
        } catch (Exception e) {
            LOG.warn("Signature verification failed: %s", e.getMessage());
            return false;
        }
    }

    public boolean verifyDiscriminatorMatch(PublicKey publicKey, String username) {
        if (!username.contains("#")) {
            return true;
        }

        String discriminator = username.substring(username.indexOf('#') + 1);
        String fingerprint = computeFingerprint(publicKey);

        String expectedDiscriminator = fingerprint.length() >= 8 ? fingerprint.substring(0, 8) : fingerprint;
        return discriminator.equalsIgnoreCase(expectedDiscriminator);
    }

    public String computeFingerprint(PublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(publicKey.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public String formatFingerprint(String rawFingerprint) {
        if (rawFingerprint.contains(":")) {
            return rawFingerprint;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawFingerprint.length(); i += 4) {
            if (i > 0) sb.append(":");
            int end = Math.min(i + 4, rawFingerprint.length());
            sb.append(rawFingerprint.substring(i, end));
        }
        return sb.toString();
    }

    public String computeDiscriminator(PublicKey publicKey) {
        String fingerprint = computeFingerprint(publicKey);
        return fingerprint.length() >= 8 ? fingerprint.substring(0, 8) : fingerprint;
    }

    public VerificationResult verifyContact(Contact contact) {
        String computedFingerprint = computeFingerprint(contact.getPublicKey());
        boolean fingerprintValid = computedFingerprint.equalsIgnoreCase(contact.getFingerprint().replace(":", ""));

        boolean discriminatorValid = verifyDiscriminatorMatch(contact.getPublicKey(), contact.getUsername());

        if (fingerprintValid && discriminatorValid) {
            LOG.info("Contact verified: %s", contact.getDisplayName());
            return new VerificationResult(true, "Contact verified successfully");
        } else if (!fingerprintValid) {
            LOG.warn("Fingerprint mismatch for contact: %s", contact.getDisplayName());
            return new VerificationResult(false, "Fingerprint mismatch - possible impersonation");
        } else {
            LOG.warn("Discriminator mismatch for contact: %s", contact.getDisplayName());
            return new VerificationResult(false, "Discriminator mismatch - username may be spoofed");
        }
    }

    public static final class VerificationResult {
        private final boolean valid;
        private final String message;

        public VerificationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
