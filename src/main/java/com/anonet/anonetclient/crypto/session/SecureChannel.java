package com.anonet.anonetclient.crypto.session;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.crypto.session
Created by: Ashish Kushwaha on 19-01-2026 22:10
File: SecureChannel.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/
public final class SecureChannel {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int NONCE_LENGTH = 12;

    private final SessionKeys sessionKeys;
    private final AtomicLong sendSequence;
    private final AtomicLong receiveSequence;
    private volatile boolean closed;

    public SecureChannel(SessionKeys sessionKeys) {
        this.sessionKeys = sessionKeys;
        this.sendSequence = new AtomicLong(0);
        this.receiveSequence = new AtomicLong(0);
        this.closed = false;
    }

    public EncryptedMessage encrypt(byte[] plaintext) {
        checkClosed();
        long sequence = sendSequence.getAndIncrement();
        byte[] nonce = sessionKeys.computeNonce(sequence);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKeys.getEncryptionKey(), gcmSpec);
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedMessage(ciphertext, sequence);
        } catch (GeneralSecurityException e) {
            throw new SessionCryptoException("Encryption failed", e);
        }
    }

    public byte[] decrypt(EncryptedMessage message) {
        checkClosed();

        long expectedSequence = receiveSequence.get();
        if (message.getSequence() < expectedSequence) {
            throw new SessionCryptoException("Replay attack detected: sequence " + message.getSequence() + " already processed");
        }
        if (message.getSequence() > expectedSequence + 1000) {
            throw new SessionCryptoException("Sequence number too far ahead: possible attack");
        }

        byte[] nonce = sessionKeys.computeNonce(message.getSequence());

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, sessionKeys.getEncryptionKey(), gcmSpec);
            byte[] plaintext = cipher.doFinal(message.getCiphertext());

            receiveSequence.set(message.getSequence() + 1);

            return plaintext;
        } catch (AEADBadTagException e) {
            throw new SessionCryptoException("Authentication failed: message integrity compromised", e);
        } catch (GeneralSecurityException e) {
            throw new SessionCryptoException("Decryption failed", e);
        }
    }

    public void close() {
        if (!closed) {
            closed = true;
            sessionKeys.destroy();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private void checkClosed() {
        if (closed) {
            throw new SessionCryptoException("Secure channel is closed");
        }
    }

    public static final class EncryptedMessage {

        private final byte[] ciphertext;
        private final long sequence;

        public EncryptedMessage(byte[] ciphertext, long sequence) {
            this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
            this.sequence = sequence;
        }

        public byte[] getCiphertext() {
            return Arrays.copyOf(ciphertext, ciphertext.length);
        }

        public long getSequence() {
            return sequence;
        }

        public byte[] toBytes() {
            byte[] result = new byte[8 + ciphertext.length];
            for (int i = 0; i < 8; i++) {
                result[i] = (byte) (sequence >>> (56 - i * 8));
            }
            System.arraycopy(ciphertext, 0, result, 8, ciphertext.length);
            return result;
        }

        public static EncryptedMessage fromBytes(byte[] data) {
            if (data.length < 8) {
                throw new SessionCryptoException("Invalid encrypted message format");
            }
            long sequence = 0;
            for (int i = 0; i < 8; i++) {
                sequence = (sequence << 8) | (data[i] & 0xFF);
            }
            byte[] ciphertext = Arrays.copyOfRange(data, 8, data.length);
            return new EncryptedMessage(ciphertext, sequence);
        }
    }
}
