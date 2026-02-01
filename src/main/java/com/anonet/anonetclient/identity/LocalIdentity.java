/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.identity
Created by: Ashish Kushwaha on 19-01-2026 14:30
File: LocalIdentity.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.identity;

import com.anonet.anonetclient.crypto.CryptoUtils;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public final class LocalIdentity {

    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final String fingerprint;

    public LocalIdentity(KeyPair keyPair) {
        this.publicKey = keyPair.getPublic();
        this.privateKey = keyPair.getPrivate();
        this.fingerprint = CryptoUtils.computePublicKeyFingerprint(publicKey);
    }

    public LocalIdentity(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.fingerprint = CryptoUtils.computePublicKeyFingerprint(publicKey);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getFormattedFingerprint() {
        return CryptoUtils.formatFingerprint(fingerprint);
    }
}
