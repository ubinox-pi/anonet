package com.anonet.anonetclient.crypto.session;

/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.crypto.session
Created by: Ashish Kushwaha on 19-01-2026 22:10
File: SessionCryptoException.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/
public class SessionCryptoException extends RuntimeException {

    public SessionCryptoException(String message) {
        super(message);
    }

    public SessionCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
