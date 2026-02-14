package com.anonet.anonetclient.crypto.session;

import com.anonet.anonetclient.crypto.CryptoUtils;
import com.anonet.anonetclient.identity.LocalIdentity;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class SessionKeyAgreementTest {

    @Test
    void fullHandshakeProducesSameKeys() {
        KeyPair kp1 = CryptoUtils.generateKeyPair();
        KeyPair kp2 = CryptoUtils.generateKeyPair();
        LocalIdentity id1 = new LocalIdentity(kp1);
        LocalIdentity id2 = new LocalIdentity(kp2);

        SessionKeyAgreement ska1 = new SessionKeyAgreement(id1);
        SessionKeyAgreement ska2 = new SessionKeyAgreement(id2);

        SignedEphemeralKey sek1 = ska1.generateSignedEphemeralKey();
        SignedEphemeralKey sek2 = ska2.generateSignedEphemeralKey();

        SessionKeys keys1 = ska1.completeKeyAgreement(sek2);
        SessionKeys keys2 = ska2.completeKeyAgreement(sek1);

        assertNotNull(keys1);
        assertNotNull(keys2);
        assertArrayEquals(keys1.getNonceBase(), keys2.getNonceBase());
    }

    @Test
    void tamperedSignatureRejected() {
        KeyPair kp1 = CryptoUtils.generateKeyPair();
        KeyPair kp2 = CryptoUtils.generateKeyPair();
        LocalIdentity id1 = new LocalIdentity(kp1);
        LocalIdentity id2 = new LocalIdentity(kp2);

        SessionKeyAgreement ska1 = new SessionKeyAgreement(id1);
        SessionKeyAgreement ska2 = new SessionKeyAgreement(id2);

        ska1.generateSignedEphemeralKey();
        SignedEphemeralKey sek2 = ska2.generateSignedEphemeralKey();

        byte[] badSig = sek2.getSignature();
        badSig[0] ^= 0xFF;
        SignedEphemeralKey tampered = new SignedEphemeralKey(
                sek2.getEphemeralPublicKey(), badSig, sek2.getIdentityPublicKey()
        );

        assertThrows(SessionCryptoException.class, () -> ska1.completeKeyAgreement(tampered));
    }

    @Test
    void differentSessionsProduceDifferentKeys() {
        KeyPair kp1 = CryptoUtils.generateKeyPair();
        KeyPair kp2 = CryptoUtils.generateKeyPair();
        LocalIdentity id1 = new LocalIdentity(kp1);
        LocalIdentity id2 = new LocalIdentity(kp2);

        SessionKeyAgreement ska1a = new SessionKeyAgreement(id1);
        SessionKeyAgreement ska2a = new SessionKeyAgreement(id2);
        SignedEphemeralKey sek1a = ska1a.generateSignedEphemeralKey();
        SignedEphemeralKey sek2a = ska2a.generateSignedEphemeralKey();
        SessionKeys keysA = ska1a.completeKeyAgreement(sek2a);

        SessionKeyAgreement ska1b = new SessionKeyAgreement(id1);
        SessionKeyAgreement ska2b = new SessionKeyAgreement(id2);
        SignedEphemeralKey sek1b = ska1b.generateSignedEphemeralKey();
        SignedEphemeralKey sek2b = ska2b.generateSignedEphemeralKey();
        SessionKeys keysB = ska1b.completeKeyAgreement(sek2b);

        assertFalse(java.util.Arrays.equals(keysA.getNonceBase(), keysB.getNonceBase()));
    }
}
