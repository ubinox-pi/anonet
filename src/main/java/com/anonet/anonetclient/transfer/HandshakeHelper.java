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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

public final class HandshakeHelper {

    private HandshakeHelper() {
    }

    public static SecureSocketChannel performSenderHandshake(Socket socket, LocalIdentity localIdentity) throws IOException {
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
        byte[] responseData = new byte[responseLength];
        in.readFully(responseData);

        SignedEphemeralKey peerSignedKey = deserializeSignedKey(responseData);
        SessionKeys sessionKeys = agreement.completeKeyAgreement(peerSignedKey);

        SecureChannel secureChannel = new SecureChannel(sessionKeys);
        return new SecureSocketChannel(socket, secureChannel);
    }

    public static SecureSocketChannel performReceiverHandshake(Socket socket, LocalIdentity localIdentity) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        byte initType = in.readByte();
        if (initType != TransferProtocol.MSG_HANDSHAKE_INIT) {
            throw new FileTransferException("Invalid handshake init type: " + initType);
        }

        int initLength = in.readInt();
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
        return new SecureSocketChannel(socket, secureChannel);
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
