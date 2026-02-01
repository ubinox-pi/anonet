package com.anonet.anonetclient.crypto.session;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.crypto.session
Created by: Ashish Kushwaha on 19-01-2026 22:10
File: HKDF.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/
public final class HKDF {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private HKDF() {
    }

    public static byte[] deriveKey(byte[] inputKeyMaterial, byte[] salt, byte[] info, int outputLength) {
        byte[] prk = extract(salt, inputKeyMaterial);
        return expand(prk, info, outputLength);
    }

    private static byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
        byte[] effectiveSalt = (salt == null || salt.length == 0) ? new byte[HASH_LENGTH] : salt;
        return hmacSha256(effectiveSalt, inputKeyMaterial);
    }

    private static byte[] expand(byte[] prk, byte[] info, int outputLength) {
        int iterations = (int) Math.ceil((double) outputLength / HASH_LENGTH);
        if (iterations > 255) {
            throw new SessionCryptoException("Output length too large for HKDF");
        }

        byte[] output = new byte[outputLength];
        byte[] previous = new byte[0];
        int offset = 0;

        for (int i = 1; i <= iterations; i++) {
            byte[] input = concatenate(previous, info, new byte[]{(byte) i});
            previous = hmacSha256(prk, input);
            int copyLength = Math.min(HASH_LENGTH, outputLength - offset);
            System.arraycopy(previous, 0, output, offset, copyLength);
            offset += copyLength;
        }

        return output;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SessionCryptoException("HMAC-SHA256 operation failed", e);
        }
    }

    private static byte[] concatenate(byte[]... arrays) {
        int totalLength = Arrays.stream(arrays).mapToInt(a -> a.length).sum();
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
