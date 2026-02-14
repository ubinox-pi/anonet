/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.lan
 * Created by: Ashish Kushwaha on 19-01-2026 21:50
 * File: LanPeer.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.lan;

import java.net.InetAddress;
import java.time.Instant;

public final class LanPeer {

    private volatile InetAddress ipAddress;
    private final String fingerprint;
    private volatile Instant lastSeen;
    private volatile int dhtPort;

    public LanPeer(InetAddress ipAddress, String fingerprint) {
        this(ipAddress, fingerprint, 0);
    }

    public LanPeer(InetAddress ipAddress, String fingerprint, int dhtPort) {
        this.ipAddress = ipAddress;
        this.fingerprint = fingerprint;
        this.lastSeen = Instant.now();
        this.dhtPort = dhtPort;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public void updateAddress(InetAddress newAddress) {
        this.ipAddress = newAddress;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public int getDhtPort() {
        return dhtPort;
    }

    public void setDhtPort(int port) {
        this.dhtPort = port;
    }

    public String getShortFingerprint() {
        if (fingerprint.length() >= 16) {
            return fingerprint.substring(0, 16).toUpperCase();
        }
        return fingerprint.toUpperCase();
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }

    public String getDisplayAddress() {
        return ipAddress.getHostAddress();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LanPeer lanPeer = (LanPeer) obj;
        return fingerprint.equals(lanPeer.fingerprint);
    }

    @Override
    public int hashCode() {
        return fingerprint.hashCode();
    }
}
