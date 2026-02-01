/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 19-01-2026 23:45
 * File: PublicPeerEndpoint.java
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public final class PublicPeerEndpoint {

    private final String publicKeyFingerprint;
    private final String publicIpAddress;
    private final List<Integer> portCandidates;
    private final String publicKeyEncoded;

    public PublicPeerEndpoint(String publicKeyFingerprint, String publicIpAddress,
                               List<Integer> portCandidates, String publicKeyEncoded) {
        this.publicKeyFingerprint = publicKeyFingerprint;
        this.publicIpAddress = publicIpAddress;
        this.portCandidates = portCandidates != null ? new ArrayList<>(portCandidates) : List.of(51821);
        this.publicKeyEncoded = publicKeyEncoded;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public String getPublicIpAddress() {
        return publicIpAddress;
    }

    public List<Integer> getPortCandidates() {
        return portCandidates;
    }

    public int getPrimaryPort() {
        return portCandidates.isEmpty() ? 51821 : portCandidates.getFirst();
    }

    public String getPublicKeyEncoded() {
        return publicKeyEncoded;
    }

    public InetSocketAddress getPrimarySocketAddress() {
        try {
            InetAddress address = InetAddress.getByName(publicIpAddress);
            return new InetSocketAddress(address, getPrimaryPort());
        } catch (UnknownHostException e) {
            throw new PublicConnectionException("Invalid peer address: " + publicIpAddress,
                    PublicConnectionException.FailureReason.NETWORK_ERROR, e);
        }
    }

    public List<InetSocketAddress> getAllSocketAddresses() {
        List<InetSocketAddress> addresses = new ArrayList<>();
        try {
            InetAddress address = InetAddress.getByName(publicIpAddress);
            for (int port : portCandidates) {
                addresses.add(new InetSocketAddress(address, port));
            }
        } catch (UnknownHostException e) {
            throw new PublicConnectionException("Invalid peer address: " + publicIpAddress,
                    PublicConnectionException.FailureReason.NETWORK_ERROR, e);
        }
        return addresses;
    }

    public String getShortFingerprint() {
        if (publicKeyFingerprint != null && publicKeyFingerprint.length() >= 16) {
            return publicKeyFingerprint.substring(0, 16).toUpperCase();
        }
        return publicKeyFingerprint != null ? publicKeyFingerprint.toUpperCase() : "";
    }
}
