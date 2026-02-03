/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.crypto.session
 * Created by: Ashish Kushwaha on 19-01-2026 22:10
 * File: SignedEphemeralKey.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.crypto.session;

import java.util.Arrays;

public final class SignedEphemeralKey {

    private final byte[] ephemeralPublicKey;
    private final byte[] signature;
    private final byte[] identityPublicKey;

    public SignedEphemeralKey(byte[] ephemeralPublicKey, byte[] signature, byte[] identityPublicKey) {
        this.ephemeralPublicKey = Arrays.copyOf(ephemeralPublicKey, ephemeralPublicKey.length);
        this.signature = Arrays.copyOf(signature, signature.length);
        this.identityPublicKey = Arrays.copyOf(identityPublicKey, identityPublicKey.length);
    }

    public byte[] getEphemeralPublicKey() {
        return Arrays.copyOf(ephemeralPublicKey, ephemeralPublicKey.length);
    }

    public byte[] getSignature() {
        return Arrays.copyOf(signature, signature.length);
    }

    public byte[] getIdentityPublicKey() {
        return Arrays.copyOf(identityPublicKey, identityPublicKey.length);
    }
}
