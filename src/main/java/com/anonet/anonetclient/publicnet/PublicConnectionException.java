/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 19-01-2026 23:45
 * File: PublicConnectionException.java
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

public class PublicConnectionException extends RuntimeException {

    public enum FailureReason {
        STUN_FAILED,
        HOLE_PUNCH_FAILED,
        SYMMETRIC_NAT,
        TIMEOUT,
        AUTHENTICATION_FAILED,
        NETWORK_ERROR,
        PEER_UNREACHABLE
    }

    private final FailureReason reason;

    public PublicConnectionException(String message, FailureReason reason) {
        super(message);
        this.reason = reason;
    }

    public PublicConnectionException(String message, FailureReason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public FailureReason getReason() {
        return reason;
    }
}
