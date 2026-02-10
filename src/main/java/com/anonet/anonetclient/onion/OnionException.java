/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.onion
 * Created by: Ashish Kushwaha on 03-02-2026 14:05
 * File: OnionException.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.onion;

public class OnionException extends RuntimeException {

    public OnionException(String message) {
        super(message);
    }

    public OnionException(String message, Throwable cause) {
        super(message, cause);
    }
}
