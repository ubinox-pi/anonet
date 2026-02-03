/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.lan
 * Created by: Ashish Kushwaha on 19-01-2026 21:50
 * File: LanDiscoveryException.java
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

public class LanDiscoveryException extends RuntimeException {

    public LanDiscoveryException(String message) {
        super(message);
    }

    public LanDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
