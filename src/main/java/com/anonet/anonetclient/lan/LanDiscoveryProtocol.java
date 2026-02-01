/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.lan
Created by: Ashish Kushwaha on 19-01-2026 21:50
File: LanDiscoveryProtocol.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.lan;

import java.nio.charset.StandardCharsets;

public final class LanDiscoveryProtocol {

    public static final int DISCOVERY_PORT = 51820;
    public static final String PROTOCOL_MAGIC = "ANONET_DISCOVERY";
    public static final String FIELD_SEPARATOR = "|";
    public static final int BROADCAST_INTERVAL_MS = 3000;
    public static final int PEER_TIMEOUT_MS = 10000;
    public static final int BUFFER_SIZE = 512;

    private LanDiscoveryProtocol() {
    }

    public static byte[] createDiscoveryMessage(String fingerprint) {
        String message = PROTOCOL_MAGIC + FIELD_SEPARATOR + fingerprint;
        return message.getBytes(StandardCharsets.UTF_8);
    }

    public static DiscoveryMessage parseDiscoveryMessage(byte[] data, int length) {
        String message = new String(data, 0, length, StandardCharsets.UTF_8);
        String[] parts = message.split("\\" + FIELD_SEPARATOR);

        if (parts.length != 2) {
            return null;
        }

        if (!PROTOCOL_MAGIC.equals(parts[0])) {
            return null;
        }

        String fingerprint = parts[1].trim();
        if (fingerprint.isEmpty()) {
            return null;
        }

        return new DiscoveryMessage(fingerprint);
    }

    public static final class DiscoveryMessage {

        private final String fingerprint;

        public DiscoveryMessage(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        public String getFingerprint() {
            return fingerprint;
        }
    }
}
