package com.anonet.anonetclient.crypto.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class SecureChannelTest {

    private SecureChannel senderChannel;
    private SecureChannel receiverChannel;

    @BeforeEach
    void setUp() {
        byte[] encKey = new byte[32];
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(encKey);
        new SecureRandom().nextBytes(nonce);

        SessionKeys senderKeys = new SessionKeys(encKey.clone(), nonce.clone());
        SessionKeys receiverKeys = new SessionKeys(encKey.clone(), nonce.clone());

        senderChannel = new SecureChannel(senderKeys);
        receiverChannel = new SecureChannel(receiverKeys);
    }

    @Test
    void encryptDecryptRoundtrip() {
        byte[] plaintext = "Hello, Anonet!".getBytes();
        SecureChannel.EncryptedMessage encrypted = senderChannel.encrypt(plaintext);
        byte[] decrypted = receiverChannel.decrypt(encrypted);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void multipleMessagesRoundtrip() {
        for (int i = 0; i < 10; i++) {
            byte[] plaintext = ("Message " + i).getBytes();
            SecureChannel.EncryptedMessage encrypted = senderChannel.encrypt(plaintext);
            byte[] decrypted = receiverChannel.decrypt(encrypted);
            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Test
    void tamperDetection() {
        byte[] plaintext = "sensitive data".getBytes();
        SecureChannel.EncryptedMessage encrypted = senderChannel.encrypt(plaintext);

        byte[] tamperedCiphertext = encrypted.getCiphertext();
        tamperedCiphertext[0] ^= 0xFF;
        SecureChannel.EncryptedMessage tampered = new SecureChannel.EncryptedMessage(
                tamperedCiphertext, encrypted.getSequence()
        );

        assertThrows(SessionCryptoException.class, () -> receiverChannel.decrypt(tampered));
    }

    @Test
    void nonceIncrements() {
        SecureChannel.EncryptedMessage msg1 = senderChannel.encrypt("first".getBytes());
        SecureChannel.EncryptedMessage msg2 = senderChannel.encrypt("second".getBytes());
        assertEquals(0, msg1.getSequence());
        assertEquals(1, msg2.getSequence());
    }

    @Test
    void closedChannelRejectsOperations() {
        senderChannel.close();
        assertTrue(senderChannel.isClosed());
        assertThrows(SessionCryptoException.class, () -> senderChannel.encrypt("data".getBytes()));
    }

    @Test
    void encryptedMessageSerialization() {
        byte[] plaintext = "test data".getBytes();
        SecureChannel.EncryptedMessage original = senderChannel.encrypt(plaintext);
        byte[] serialized = original.toBytes();
        SecureChannel.EncryptedMessage deserialized = SecureChannel.EncryptedMessage.fromBytes(serialized);
        assertArrayEquals(original.getCiphertext(), deserialized.getCiphertext());
        assertEquals(original.getSequence(), deserialized.getSequence());
    }
}
