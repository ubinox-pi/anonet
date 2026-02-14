package com.anonet.anonetclient.dht;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeIdTest {

    @Test
    void xorDistanceSymmetric() {
        NodeId a = NodeId.random();
        NodeId b = NodeId.random();
        assertEquals(a.xorDistance(b), b.xorDistance(a));
    }

    @Test
    void xorDistanceWithSelfIsZero() {
        NodeId a = NodeId.random();
        NodeId dist = a.xorDistance(a);
        byte[] expected = new byte[NodeId.ID_LENGTH_BYTES];
        assertArrayEquals(expected, dist.getBytes());
    }

    @Test
    void fromStringDeterministic() {
        NodeId id1 = NodeId.fromString("test-input");
        NodeId id2 = NodeId.fromString("test-input");
        assertEquals(id1, id2);
    }

    @Test
    void fromStringDifferentInputsDifferentIds() {
        NodeId id1 = NodeId.fromString("alice");
        NodeId id2 = NodeId.fromString("bob");
        assertNotEquals(id1, id2);
    }

    @Test
    void fromFingerprintWorks() {
        NodeId id = NodeId.fromFingerprint("a1b2c3d4e5f6");
        assertNotNull(id);
        assertEquals(NodeId.ID_LENGTH_BYTES, id.getBytes().length);
    }

    @Test
    void toHexLength() {
        NodeId id = NodeId.random();
        String hex = id.toHex();
        assertEquals(NodeId.ID_LENGTH_BYTES * 2, hex.length());
        assertTrue(hex.matches("[0-9a-f]+"));
    }

    @Test
    void toShortHexLength() {
        NodeId id = NodeId.random();
        String shortHex = id.toShortHex();
        assertEquals(8, shortHex.length());
    }

    @Test
    void fromHexRoundtrip() {
        NodeId original = NodeId.random();
        String hex = original.toHex();
        NodeId restored = NodeId.fromHex(hex);
        assertEquals(original, restored);
    }

    @Test
    void invalidLengthRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NodeId(new byte[10]));
    }

    @Test
    void nullRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NodeId(null));
    }

    @Test
    void compareToWorks() {
        NodeId a = NodeId.random();
        NodeId b = NodeId.random();
        assertEquals(0, a.compareTo(a));
        assertTrue(a.compareTo(b) != 0 || a.equals(b));
    }
}
