package com.anonet.anonetclient.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {

    @Test
    void generateKeyPairReturnsValidKeyPair() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        assertNotNull(kp);
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertEquals("EC", kp.getPublic().getAlgorithm());
    }

    @Test
    void generateKeyPairProducesDifferentKeys() {
        KeyPair kp1 = CryptoUtils.generateKeyPair();
        KeyPair kp2 = CryptoUtils.generateKeyPair();
        assertNotEquals(kp1.getPublic(), kp2.getPublic());
    }

    @Test
    void computeFingerprintReturnsHexString() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        String fingerprint = CryptoUtils.computePublicKeyFingerprint(kp.getPublic());
        assertNotNull(fingerprint);
        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]+"));
    }

    @Test
    void computeFingerprintIsDeterministic() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        String fp1 = CryptoUtils.computePublicKeyFingerprint(kp.getPublic());
        String fp2 = CryptoUtils.computePublicKeyFingerprint(kp.getPublic());
        assertEquals(fp1, fp2);
    }

    @Test
    void formatFingerprintAddsColons() {
        String raw = "a1b2c3d4e5f6a7b8";
        String formatted = CryptoUtils.formatFingerprint(raw);
        assertEquals("A1B2:C3D4:E5F6:A7B8", formatted);
    }

    @Test
    void keyEncodingRoundtrip() throws Exception {
        KeyPair kp = CryptoUtils.generateKeyPair();
        byte[] encoded = kp.getPublic().getEncoded();
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);

        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("EC");
        PublicKey decoded = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
        assertEquals(kp.getPublic(), decoded);
    }
}
