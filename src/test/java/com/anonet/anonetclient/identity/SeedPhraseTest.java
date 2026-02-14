package com.anonet.anonetclient.identity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeedPhraseTest {

    @Test
    void generateProducesTwelveWords() {
        SeedPhrase sp = SeedPhrase.generate();
        assertEquals(12, sp.getWords().size());
    }

    @Test
    void generateProducesDifferentPhrases() {
        SeedPhrase sp1 = SeedPhrase.generate();
        SeedPhrase sp2 = SeedPhrase.generate();
        assertNotEquals(sp1.getMnemonic(), sp2.getMnemonic());
    }

    @Test
    void fromWordsRoundtrip() {
        SeedPhrase original = SeedPhrase.generate();
        String mnemonic = original.getMnemonic();
        SeedPhrase restored = SeedPhrase.fromWords(mnemonic);
        assertEquals(original.getMnemonic(), restored.getMnemonic());
        assertArrayEquals(original.getEntropy(), restored.getEntropy());
    }

    @Test
    void fromEntropyRoundtrip() {
        SeedPhrase original = SeedPhrase.generate();
        byte[] entropy = original.getEntropy();
        SeedPhrase restored = SeedPhrase.fromEntropy(entropy);
        assertEquals(original.getMnemonic(), restored.getMnemonic());
    }

    @Test
    void isValidAcceptsValidPhrase() {
        SeedPhrase sp = SeedPhrase.generate();
        assertTrue(SeedPhrase.isValid(sp.getMnemonic()));
    }

    @Test
    void isValidRejectsInvalidPhrase() {
        assertFalse(SeedPhrase.isValid("invalid words that are not a valid mnemonic phrase at all none"));
    }

    @Test
    void invalidWordCountRejected() {
        assertThrows(IllegalArgumentException.class, () -> SeedPhrase.fromWords("one two three"));
    }

    @Test
    void toSeedProduces64Bytes() {
        SeedPhrase sp = SeedPhrase.generate();
        byte[] seed = sp.toSeed();
        assertEquals(64, seed.length);
    }

    @Test
    void toSeedIsDeterministic() {
        SeedPhrase sp = SeedPhrase.generate();
        byte[] seed1 = sp.toSeed();
        SeedPhrase sp2 = SeedPhrase.fromWords(sp.getMnemonic());
        byte[] seed2 = sp2.toSeed();
        assertArrayEquals(seed1, seed2);
    }
}
