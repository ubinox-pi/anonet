package com.anonet.anonetclient.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicIdentityTest {

    @Test
    void sameSeedProducesSameKeypair() {
        SeedPhrase sp = SeedPhrase.generate();
        LocalIdentity id1 = DeterministicIdentity.deriveFromSeedPhrase(sp);
        LocalIdentity id2 = DeterministicIdentity.deriveFromSeedPhrase(
                SeedPhrase.fromWords(sp.getMnemonic())
        );
        assertEquals(id1.getFingerprint(), id2.getFingerprint());
        assertArrayEquals(
                id1.getPublicKey().getEncoded(),
                id2.getPublicKey().getEncoded()
        );
    }

    @Test
    void differentSeedProducesDifferentKeypair() {
        SeedPhrase sp1 = SeedPhrase.generate();
        SeedPhrase sp2 = SeedPhrase.generate();
        LocalIdentity id1 = DeterministicIdentity.deriveFromSeedPhrase(sp1);
        LocalIdentity id2 = DeterministicIdentity.deriveFromSeedPhrase(sp2);
        assertNotEquals(id1.getFingerprint(), id2.getFingerprint());
    }

    @Test
    void derivedIdentityHasValidKeys() {
        SeedPhrase sp = SeedPhrase.generate();
        LocalIdentity identity = DeterministicIdentity.deriveFromSeedPhrase(sp);
        assertNotNull(identity.getPublicKey());
        assertNotNull(identity.getPrivateKey());
        assertEquals("EC", identity.getPublicKey().getAlgorithm());
        assertEquals("EC", identity.getPrivateKey().getAlgorithm());
    }

    @Test
    void fingerprintIs64HexChars() {
        SeedPhrase sp = SeedPhrase.generate();
        LocalIdentity identity = DeterministicIdentity.deriveFromSeedPhrase(sp);
        String fp = identity.getFingerprint();
        assertEquals(64, fp.length());
        assertTrue(fp.matches("[0-9a-f]+"));
    }

    @Test
    void deriveFromSeedBytesWorks() {
        SeedPhrase sp = SeedPhrase.generate();
        byte[] seed = sp.toSeed();
        LocalIdentity fromSeed = DeterministicIdentity.deriveFromSeed(seed);
        LocalIdentity fromPhrase = DeterministicIdentity.deriveFromSeedPhrase(sp);
        assertEquals(fromSeed.getFingerprint(), fromPhrase.getFingerprint());
    }

    @Test
    void shortSeedRejected() {
        assertThrows(IdentityException.class, () -> DeterministicIdentity.deriveFromSeed(new byte[16]));
    }
}
