/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 02-02-2026 12:20
 * File: DeterministicIdentity.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.identity;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;

/**
 * Derives deterministic EC keypairs from BIP39 seed phrases.
 * Same seed phrase always produces the same identity.
 */
public final class DeterministicIdentity {

    private static final AnonetLogger LOG = AnonetLogger.get(DeterministicIdentity.class);

    private static final String CURVE_NAME = "secp256r1";
    private static final String KEY_ALGORITHM = "EC";

    private DeterministicIdentity() {}

    public static LocalIdentity deriveFromSeedPhrase(SeedPhrase seedPhrase) {
        return deriveFromSeedPhrase(seedPhrase, "");
    }

    public static LocalIdentity deriveFromSeedPhrase(SeedPhrase seedPhrase, String passphrase) {
        LOG.debug("Deriving identity from seed phrase");
        byte[] seed = seedPhrase.toSeed(passphrase);
        byte[] privateKeyBytes = Arrays.copyOfRange(seed, 0, 32);
        return deriveFromPrivateKeyBytes(privateKeyBytes);
    }

    public static LocalIdentity deriveFromSeed(byte[] seed) {
        if (seed.length < 32) {
            throw new IdentityException("Seed must be at least 32 bytes");
        }
        byte[] privateKeyBytes = Arrays.copyOfRange(seed, 0, 32);
        return deriveFromPrivateKeyBytes(privateKeyBytes);
    }

    private static LocalIdentity deriveFromPrivateKeyBytes(byte[] privateKeyBytes) {
        try {
            BigInteger privateKeyValue = new BigInteger(1, privateKeyBytes);

            if (privateKeyValue.equals(BigInteger.ZERO)) {
                throw new IdentityException("Private key cannot be zero");
            }

            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);

            AlgorithmParameters parameters = AlgorithmParameters.getInstance(KEY_ALGORITHM);
            parameters.init(new ECGenParameterSpec(CURVE_NAME));
            ECParameterSpec ecParameterSpec = parameters.getParameterSpec(ECParameterSpec.class);

            // Ensure private key is within curve order
            BigInteger order = ecParameterSpec.getOrder();
            privateKeyValue = privateKeyValue.mod(order);
            if (privateKeyValue.equals(BigInteger.ZERO)) {
                privateKeyValue = BigInteger.ONE;
            }

            ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateKeyValue, ecParameterSpec);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            // Derive public key from private key using EC point multiplication
            ECPoint publicPoint = multiplyPoint(ecParameterSpec.getGenerator(), privateKeyValue, ecParameterSpec);
            ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(publicPoint, ecParameterSpec);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            KeyPair keyPair = new KeyPair(publicKey, privateKey);
            LOG.info("Deterministic identity derived successfully");
            return new LocalIdentity(keyPair);

        } catch (NoSuchAlgorithmException e) {
            throw new IdentityException("EC algorithm not available", e);
        } catch (InvalidKeySpecException e) {
            throw new IdentityException("Invalid key specification", e);
        } catch (InvalidParameterSpecException e) {
            throw new IdentityException("Invalid EC parameters", e);
        }
    }

    private static ECPoint multiplyPoint(ECPoint point, BigInteger scalar, ECParameterSpec spec) {
        if (scalar.equals(BigInteger.ZERO)) {
            return ECPoint.POINT_INFINITY;
        }

        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = point;

        while (!scalar.equals(BigInteger.ZERO)) {
            if (scalar.testBit(0)) {
                result = addPoints(result, addend, spec);
            }
            addend = doublePoint(addend, spec);
            scalar = scalar.shiftRight(1);
        }

        return result;
    }

    private static ECPoint addPoints(ECPoint p1, ECPoint p2, ECParameterSpec spec) {
        if (p1.equals(ECPoint.POINT_INFINITY)) {
            return p2;
        }
        if (p2.equals(ECPoint.POINT_INFINITY)) {
            return p1;
        }

        BigInteger p = ((java.security.spec.ECFieldFp) spec.getCurve().getField()).getP();

        if (p1.getAffineX().equals(p2.getAffineX())) {
            if (p1.getAffineY().equals(p2.getAffineY())) {
                return doublePoint(p1, spec);
            } else {
                return ECPoint.POINT_INFINITY;
            }
        }

        BigInteger x1 = p1.getAffineX();
        BigInteger y1 = p1.getAffineY();
        BigInteger x2 = p2.getAffineX();
        BigInteger y2 = p2.getAffineY();

        BigInteger dx = x2.subtract(x1).mod(p);
        BigInteger dy = y2.subtract(y1).mod(p);
        BigInteger slope = dy.multiply(dx.modInverse(p)).mod(p);

        BigInteger x3 = slope.multiply(slope).subtract(x1).subtract(x2).mod(p);
        BigInteger y3 = slope.multiply(x1.subtract(x3)).subtract(y1).mod(p);

        return new ECPoint(x3, y3);
    }

    private static ECPoint doublePoint(ECPoint point, ECParameterSpec spec) {
        if (point.equals(ECPoint.POINT_INFINITY)) {
            return ECPoint.POINT_INFINITY;
        }

        BigInteger p = ((java.security.spec.ECFieldFp) spec.getCurve().getField()).getP();
        BigInteger a = spec.getCurve().getA();

        BigInteger x = point.getAffineX();
        BigInteger y = point.getAffineY();

        if (y.equals(BigInteger.ZERO)) {
            return ECPoint.POINT_INFINITY;
        }

        BigInteger slope = x.multiply(x).multiply(BigInteger.valueOf(3)).add(a)
                .multiply(y.multiply(BigInteger.valueOf(2)).modInverse(p)).mod(p);

        BigInteger x3 = slope.multiply(slope).subtract(x.multiply(BigInteger.valueOf(2))).mod(p);
        BigInteger y3 = slope.multiply(x.subtract(x3)).subtract(y).mod(p);

        return new ECPoint(x3, y3);
    }
}
