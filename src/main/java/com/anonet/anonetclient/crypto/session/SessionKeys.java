/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.crypto.session
Created by: Ashish Kushwaha on 19-01-2026 22:10
File: SessionKeys.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.crypto.session;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

public final class SessionKeys {

    private static final String AES_ALGORITHM = "AES";

    private final byte[] encryptionKey;
    private final byte[] nonceBase;
    private volatile boolean destroyed;

    public SessionKeys(byte[] encryptionKey, byte[] nonceBase) {
        if (encryptionKey.length != 32) {
            throw new SessionCryptoException("Encryption key must be 256 bits");
        }
        if (nonceBase.length != 12) {
            throw new SessionCryptoException("Nonce base must be 96 bits");
        }
        this.encryptionKey = Arrays.copyOf(encryptionKey, encryptionKey.length);
        this.nonceBase = Arrays.copyOf(nonceBase, nonceBase.length);
        this.destroyed = false;
    }

    public SecretKey getEncryptionKey() {
        checkDestroyed();
        return new SecretKeySpec(encryptionKey, AES_ALGORITHM);
    }

    public byte[] getNonceBase() {
        checkDestroyed();
        return Arrays.copyOf(nonceBase, nonceBase.length);
    }

    public byte[] computeNonce(long sequenceNumber) {
        checkDestroyed();
        byte[] nonce = Arrays.copyOf(nonceBase, 12);
        for (int i = 0; i < 8; i++) {
            nonce[11 - i] ^= (byte) (sequenceNumber >>> (i * 8));
        }
        return nonce;
    }

    public void destroy() {
        if (!destroyed) {
            Arrays.fill(encryptionKey, (byte) 0);
            Arrays.fill(nonceBase, (byte) 0);
            destroyed = true;
        }
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    private void checkDestroyed() {
        if (destroyed) {
            throw new SessionCryptoException("Session keys have been destroyed");
        }
    }
}
