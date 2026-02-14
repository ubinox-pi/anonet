/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 19-01-2026 23:45
 * File: StunLikeClient.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.publicnet;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class StunLikeClient {

    private static final AnonetLogger LOG = AnonetLogger.get(StunLikeClient.class);

    private static final int STUN_TIMEOUT_MS = 3000;
    private static final int STUN_RETRIES = 3;
    private static final String STUN_REQUEST_PREFIX = "ANONET_STUN_REQ";
    private static final String STUN_RESPONSE_PREFIX = "ANONET_STUN_RES|";

    private static final List<StunServer> PUBLIC_STUN_SERVERS = List.of(
            new StunServer("stun.l.google.com", 19302),
            new StunServer("stun1.l.google.com", 19302),
            new StunServer("stun2.l.google.com", 19302)
    );

    public record ExternalAddress(String publicIp, int publicPort) {
        public InetSocketAddress toSocketAddress() {
            try {
                return new InetSocketAddress(InetAddress.getByName(publicIp), publicPort);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private record StunServer(String host, int port) {}

    public static ExternalAddress discoverExternalAddress(DatagramSocket socket) {
        LOG.debug("Discovering external address");
        ExternalAddress fromAnonetServer = discoverViaAnonetServer(socket);
        if (fromAnonetServer != null) {
            return fromAnonetServer;
        }

        return discoverViaSimpleEcho(socket);
    }

    private static ExternalAddress discoverViaAnonetServer(DatagramSocket socket) {
        return null;
    }

    private static ExternalAddress discoverViaSimpleEcho(DatagramSocket socket) {
        for (StunServer server : PUBLIC_STUN_SERVERS) {
            try {
                ExternalAddress address = performStunBinding(socket, server);
                if (address != null) {
                    LOG.info("External address discovered: %s:%d", address.publicIp(), address.publicPort());
                    return address;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static ExternalAddress performStunBinding(DatagramSocket socket, StunServer server) {
        try {
            InetAddress serverAddress = InetAddress.getByName(server.host());
            byte[] request = createStunBindingRequest();

            DatagramPacket requestPacket = new DatagramPacket(
                    request, request.length, serverAddress, server.port());

            int originalTimeout = socket.getSoTimeout();
            socket.setSoTimeout(STUN_TIMEOUT_MS);

            for (int i = 0; i < STUN_RETRIES; i++) {
                try {
                    socket.send(requestPacket);

                    byte[] responseBuffer = new byte[256];
                    DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                    socket.receive(responsePacket);

                    ExternalAddress address = parseStunBindingResponse(
                            responseBuffer, responsePacket.getLength());
                    if (address != null) {
                        socket.setSoTimeout(originalTimeout);
                        return address;
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }

            socket.setSoTimeout(originalTimeout);
        } catch (IOException ignored) {
        }
        return null;
    }

    private static byte[] createStunBindingRequest() {
        byte[] request = new byte[20];

        request[0] = 0x00;
        request[1] = 0x01;

        request[2] = 0x00;
        request[3] = 0x00;

        request[4] = 0x21;
        request[5] = 0x12;
        request[6] = (byte) 0xa4;
        request[7] = 0x42;

        for (int i = 8; i < 20; i++) {
            request[i] = (byte) (Math.random() * 256);
        }

        return request;
    }

    private static ExternalAddress parseStunBindingResponse(byte[] response, int length) {
        if (length < 20) {
            return null;
        }

        int messageType = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
        if (messageType != 0x0101) {
            return null;
        }

        int messageLength = ((response[2] & 0xFF) << 8) | (response[3] & 0xFF);
        int offset = 20;

        while (offset + 4 <= length && offset < 20 + messageLength) {
            int attrType = ((response[offset] & 0xFF) << 8) | (response[offset + 1] & 0xFF);
            int attrLength = ((response[offset + 2] & 0xFF) << 8) | (response[offset + 3] & 0xFF);
            offset += 4;

            if ((attrType == 0x0001 || attrType == 0x0020) && attrLength >= 8) {
                int family = response[offset + 1] & 0xFF;
                if (family == 0x01) {
                    int port;
                    String ip;

                    if (attrType == 0x0020) {
                        port = (((response[offset + 2] & 0xFF) ^ 0x21) << 8) |
                                ((response[offset + 3] & 0xFF) ^ 0x12);
                        ip = String.format("%d.%d.%d.%d",
                                (response[offset + 4] & 0xFF) ^ 0x21,
                                (response[offset + 5] & 0xFF) ^ 0x12,
                                (response[offset + 6] & 0xFF) ^ 0xa4,
                                (response[offset + 7] & 0xFF) ^ 0x42);
                    } else {
                        port = ((response[offset + 2] & 0xFF) << 8) | (response[offset + 3] & 0xFF);
                        ip = String.format("%d.%d.%d.%d",
                                response[offset + 4] & 0xFF,
                                response[offset + 5] & 0xFF,
                                response[offset + 6] & 0xFF,
                                response[offset + 7] & 0xFF);
                    }

                    return new ExternalAddress(ip, port);
                }
            }

            offset += attrLength;
            int padding = (4 - (attrLength % 4)) % 4;
            offset += padding;
        }

        return null;
    }

    public static List<Integer> discoverPortCandidates(int localPort) {
        List<Integer> candidates = new ArrayList<>();
        candidates.add(localPort);

        candidates.add(51821);
        candidates.add(51822);
        candidates.add(51823);

        if (localPort > 1024 && localPort < 65535) {
            candidates.add(localPort + 1);
            candidates.add(localPort - 1);
        }

        return candidates.stream().distinct().toList();
    }
}
