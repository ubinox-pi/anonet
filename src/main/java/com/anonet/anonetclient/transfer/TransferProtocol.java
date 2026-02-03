/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.transfer
 * Created by: Ashish Kushwaha on 19-01-2026 22:30
 * File: TransferProtocol.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.transfer;

public final class TransferProtocol {

    public static final int TRANSFER_PORT = 51821;
    public static final int CHUNK_SIZE = 65536;
    public static final int SOCKET_TIMEOUT_MS = 30000;
    public static final int CONNECT_TIMEOUT_MS = 10000;

    public static final byte MSG_HANDSHAKE_INIT = 0x01;
    public static final byte MSG_HANDSHAKE_RESPONSE = 0x02;
    public static final byte MSG_FILE_METADATA = 0x03;
    public static final byte MSG_FILE_CHUNK = 0x04;
    public static final byte MSG_TRANSFER_COMPLETE = 0x05;
    public static final byte MSG_TRANSFER_ACK = 0x06;
    public static final byte MSG_ERROR = 0x0F;

    private TransferProtocol() {
    }
}
