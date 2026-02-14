/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.transfer
 * Created by: Ashish Kushwaha on 19-01-2026 22:30
 * File: HandshakeHelper.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.transfer;

import com.anonet.anonetclient.crypto.session.SecureChannel;
import com.anonet.anonetclient.crypto.session.SessionKeyAgreement;
import com.anonet.anonetclient.crypto.session.SessionKeys;
import com.anonet.anonetclient.crypto.session.SignedEphemeralKey;
import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

public final class HandshakeHelper {

    private static final AnonetLogger LOG = AnonetLogger.get(HandshakeHelper.class);
    private static final int MAX_HANDSHAKE_SIZE = 8192;

    public record AuthenticatedChannel(SecureSocketChannel channel, String peerFingerprint) {}

    private HandshakeHelper() {
    }

    public static AuthenticatedChannel performSenderHandshake(Socket socket, LocalIdentity localIdentity) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        SessionKeyAgreement agreement = new SessionKeyAgreement(localIdentity);
        SignedEphemeralKey mySignedKey = agreement.generateSignedEphemeralKey();

        byte[] handshakeData = serializeSignedKey(mySignedKey);
        out.writeByte(TransferProtocol.MSG_HANDSHAKE_INIT);
        out.writeInt(handshakeData.length);
        out.write(handshakeData);
        out.flush();

        byte responseType = in.readByte();
        if (responseType != TransferProtocol.MSG_HANDSHAKE_RESPONSE) {
            throw new FileTransferException("Invalid handshake response type: " + responseType);
        }

        int responseLength = in.readInt();
        if (responseLength <= 0 || responseLength > MAX_HANDSHAKE_SIZE) {
            throw new FileTransferException("Invalid handshake response size: " + responseLength);
        }
        byte[] responseData = new byte[responseLength];
        in.readFully(responseData);

        SignedEphemeralKey peerSignedKey = deserializeSignedKey(responseData);
        SessionKeys sessionKeys = agreement.completeKeyAgreement(peerSignedKey);

        SecureChannel secureChannel = new SecureChannel(sessionKeys);
        String peerFingerprint = computeFingerprint(peerSignedKey.getIdentityPublicKey());
        LOG.info("Sender handshake completed");
        return new AuthenticatedChannel(new SecureSocketChannel(socket, secureChannel), peerFingerprint);
    }

    public static AuthenticatedChannel performReceiverHandshake(Socket socket, LocalIdentity localIdentity) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        byte initType = in.readByte();
        if (initType != TransferProtocol.MSG_HANDSHAKE_INIT) {
            throw new FileTransferException("Invalid handshake init type: " + initType);
        }

        int initLength = in.readInt();
        if (initLength <= 0 || initLength > MAX_HANDSHAKE_SIZE) {
            throw new FileTransferException("Invalid handshake init size: " + initLength);
        }
        byte[] initData = new byte[initLength];
        in.readFully(initData);

        SignedEphemeralKey peerSignedKey = deserializeSignedKey(initData);

        SessionKeyAgreement agreement = new SessionKeyAgreement(localIdentity);
        SignedEphemeralKey mySignedKey = agreement.generateSignedEphemeralKey();
        SessionKeys sessionKeys = agreement.completeKeyAgreement(peerSignedKey);

        byte[] responseData = serializeSignedKey(mySignedKey);
        out.writeByte(TransferProtocol.MSG_HANDSHAKE_RESPONSE);
        out.writeInt(responseData.length);
        out.write(responseData);
        out.flush();

        SecureChannel secureChannel = new SecureChannel(sessionKeys);
        String peerFingerprint = computeFingerprint(peerSignedKey.getIdentityPublicKey());
        LOG.info("Receiver handshake completed");
        return new AuthenticatedChannel(new SecureSocketChannel(socket, secureChannel), peerFingerprint);
    }

    private static String computeFingerprint(byte[] publicKeyBytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKeyBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new FileTransferException("SHA-256 not available", e);
        }
    }

    private static byte[] serializeSignedKey(SignedEphemeralKey signedKey) {
        byte[] ephemeralKey = signedKey.getEphemeralPublicKey();
        byte[] signature = signedKey.getSignature();
        byte[] identityKey = signedKey.getIdentityPublicKey();

        ByteBuffer buffer = ByteBuffer.allocate(4 + ephemeralKey.length + 4 + signature.length + 4 + identityKey.length);
        buffer.putInt(ephemeralKey.length);
        buffer.put(ephemeralKey);
        buffer.putInt(signature.length);
        buffer.put(signature);
        buffer.putInt(identityKey.length);
        buffer.put(identityKey);

        return buffer.array();
    }

    private static SignedEphemeralKey deserializeSignedKey(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int ephemeralKeyLength = buffer.getInt();
        byte[] ephemeralKey = new byte[ephemeralKeyLength];
        buffer.get(ephemeralKey);

        int signatureLength = buffer.getInt();
        byte[] signature = new byte[signatureLength];
        buffer.get(signature);

        int identityKeyLength = buffer.getInt();
        byte[] identityKey = new byte[identityKeyLength];
        buffer.get(identityKey);

        return new SignedEphemeralKey(ephemeralKey, signature, identityKey);
    }
}
