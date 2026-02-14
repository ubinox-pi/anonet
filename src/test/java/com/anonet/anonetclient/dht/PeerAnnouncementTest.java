package com.anonet.anonetclient.dht;

import com.anonet.anonetclient.crypto.CryptoUtils;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeerAnnouncementTest {

    @Test
    void createAndVerify() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        PeerAnnouncement announcement = PeerAnnouncement.create(
                "alice#A1B2C3D4", "a1b2c3d4", kp.getPublic(),
                List.of(51820, 51821), kp.getPrivate()
        );
        assertTrue(announcement.verify());
    }

    @Test
    void toBytesFromBytesRoundtrip() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        PeerAnnouncement original = PeerAnnouncement.create(
                "bob#E5F6A7B8", "e5f6a7b8", kp.getPublic(),
                List.of(51820), kp.getPrivate()
        );
        byte[] bytes = original.toBytes();
        PeerAnnouncement restored = PeerAnnouncement.fromBytes(bytes);

        assertEquals(original.getUsername(), restored.getUsername());
        assertEquals(original.getFingerprint(), restored.getFingerprint());
        assertEquals(original.getPortCandidates(), restored.getPortCandidates());
        assertEquals(original.getTimestamp(), restored.getTimestamp());
        assertTrue(restored.verify());
    }

    @Test
    void tamperedDataRejected() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        PeerAnnouncement announcement = PeerAnnouncement.create(
                "alice#A1B2C3D4", "a1b2c3d4", kp.getPublic(),
                List.of(51820), kp.getPrivate()
        );
        byte[] bytes = announcement.toBytes();
        bytes[10] ^= 0xFF;
        PeerAnnouncement tampered = PeerAnnouncement.fromBytes(bytes);
        assertFalse(tampered.verify());
    }

    @Test
    void getDhtKeyDeterministic() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        PeerAnnouncement a1 = PeerAnnouncement.create(
                "alice#A1B2C3D4", "a1b2c3d4", kp.getPublic(),
                List.of(51820), kp.getPrivate()
        );
        PeerAnnouncement a2 = PeerAnnouncement.create(
                "alice#A1B2C3D4", "a1b2c3d4", kp.getPublic(),
                List.of(51820), kp.getPrivate()
        );
        assertEquals(a1.getDhtKey(), a2.getDhtKey());
    }

    @Test
    void getPublicKeyReturnsCorrectKey() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        PeerAnnouncement announcement = PeerAnnouncement.create(
                "test#12345678", "12345678", kp.getPublic(),
                List.of(51820), kp.getPrivate()
        );
        assertEquals(kp.getPublic(), announcement.getPublicKey());
    }

    @Test
    void portCandidatesPreserved() {
        KeyPair kp = CryptoUtils.generateKeyPair();
        List<Integer> ports = List.of(51820, 51821, 51822);
        PeerAnnouncement announcement = PeerAnnouncement.create(
                "test#12345678", "12345678", kp.getPublic(),
                ports, kp.getPrivate()
        );
        assertEquals(ports, announcement.getPortCandidates());
    }
}
