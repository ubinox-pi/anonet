package com.anonet.anonetclient.crypto.session;

import com.anonet.anonetclient.identity.LocalIdentity;

import javax.crypto.KeyAgreement;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.crypto.session
Created by: Ashish Kushwaha on 19-01-2026 22:10
File: SessionKeyAgreement.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/
public final class SessionKeyAgreement {

    private static final String KEY_ALGORITHM = "EC";
    private static final String KEY_AGREEMENT_ALGORITHM = "ECDH";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final byte[] HKDF_SALT = "ANONET_SESSION_V1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO_ENCRYPTION = "ANONET_ENC_KEY".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO_NONCE = "ANONET_NONCE_BASE".getBytes(StandardCharsets.UTF_8);

    private final LocalIdentity localIdentity;
    private EphemeralKeyPair ephemeralKeyPair;

    public SessionKeyAgreement(LocalIdentity localIdentity) {
        this.localIdentity = localIdentity;
    }

    public SignedEphemeralKey generateSignedEphemeralKey() {
        ephemeralKeyPair = EphemeralKeyPair.generate();
        byte[] ephemeralPublicKeyBytes = ephemeralKeyPair.getPublicKeyBytes();
        byte[] signature = signData(ephemeralPublicKeyBytes, localIdentity.getPrivateKey());
        byte[] identityPublicKeyBytes = localIdentity.getPublicKey().getEncoded();
        return new SignedEphemeralKey(ephemeralPublicKeyBytes, signature, identityPublicKeyBytes);
    }

    public SessionKeys completeKeyAgreement(SignedEphemeralKey peerSignedKey) {
        if (ephemeralKeyPair == null) {
            throw new SessionCryptoException("Must call generateSignedEphemeralKey first");
        }

        PublicKey peerIdentityPublicKey = decodePublicKey(peerSignedKey.getIdentityPublicKey());
        boolean validSignature = verifySignature(
                peerSignedKey.getEphemeralPublicKey(),
                peerSignedKey.getSignature(),
                peerIdentityPublicKey
        );

        if (!validSignature) {
            throw new SessionCryptoException("Peer signature verification failed - authentication error");
        }

        PublicKey peerEphemeralPublicKey = decodePublicKey(peerSignedKey.getEphemeralPublicKey());
        byte[] sharedSecret = performKeyAgreement(ephemeralKeyPair.getPrivateKey(), peerEphemeralPublicKey);

        byte[] combinedInfo = combinePublicKeys(
                ephemeralKeyPair.getPublicKeyBytes(),
                peerSignedKey.getEphemeralPublicKey()
        );

        byte[] encryptionKey = HKDF.deriveKey(sharedSecret, HKDF_SALT,
                concatenate(HKDF_INFO_ENCRYPTION, combinedInfo), 32);
        byte[] nonceBase = HKDF.deriveKey(sharedSecret, HKDF_SALT,
                concatenate(HKDF_INFO_NONCE, combinedInfo), 12);

        Arrays.fill(sharedSecret, (byte) 0);

        ephemeralKeyPair = null;

        return new SessionKeys(encryptionKey, nonceBase);
    }

    private byte[] signData(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new SessionCryptoException("Failed to sign data", e);
        }
    }

    private boolean verifySignature(byte[] data, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new SessionCryptoException("Failed to verify signature", e);
        }
    }

    private PublicKey decodePublicKey(byte[] encodedKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new SessionCryptoException("Failed to decode public key", e);
        }
    }

    private byte[] performKeyAgreement(PrivateKey privateKey, PublicKey peerPublicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(peerPublicKey, true);
            return keyAgreement.generateSecret();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SessionCryptoException("ECDH key agreement failed", e);
        }
    }

    private byte[] combinePublicKeys(byte[] key1, byte[] key2) {
        byte[] combined = new byte[key1.length + key2.length];
        if (compareBytes(key1, key2) < 0) {
            System.arraycopy(key1, 0, combined, 0, key1.length);
            System.arraycopy(key2, 0, combined, key1.length, key2.length);
        } else {
            System.arraycopy(key2, 0, combined, 0, key2.length);
            System.arraycopy(key1, 0, combined, key2.length, key1.length);
        }
        return combined;
    }

    private int compareBytes(byte[] a, byte[] b) {
        int minLength = Math.min(a.length, b.length);
        for (int i = 0; i < minLength; i++) {
            int cmp = Byte.compareUnsigned(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    private byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
