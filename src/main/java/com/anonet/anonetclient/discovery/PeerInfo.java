/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.discovery
 * Created by: Ashish Kushwaha on 19-01-2026 23:00
 * File: PeerInfo.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.discovery;

import java.util.List;

public record PeerInfo(
        String username,
        String publicKeyFingerprint,
        String publicKeyEncoded,
        String ipAddress,
        List<Integer> portCandidates,
        boolean canSend,
        boolean canReceive,
        String status,
        long lastSeen
) {
    public String shortFingerprint() {
        if (publicKeyFingerprint != null && publicKeyFingerprint.length() >= 16) {
            return publicKeyFingerprint.substring(0, 16).toUpperCase();
        }
        return publicKeyFingerprint != null ? publicKeyFingerprint.toUpperCase() : "";
    }

    public boolean isOnline() {
        return "ONLINE".equals(status);
    }

    public int primaryPort() {
        return portCandidates != null && !portCandidates.isEmpty() ? portCandidates.getFirst() : 51821;
    }
}
