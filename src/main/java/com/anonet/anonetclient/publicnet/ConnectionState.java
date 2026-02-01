/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 19-01-2026 23:45
 * File: ConnectionState.java
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

public enum ConnectionState {
    DISCOVERED,
    ATTEMPTING_P2P,
    HOLE_PUNCHING,
    AUTHENTICATING,
    CONNECTED,
    FAILED_NAT,
    FAILED_AUTH,
    DISCONNECTED
}
