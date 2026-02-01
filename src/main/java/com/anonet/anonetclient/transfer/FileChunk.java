/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.transfer
Created by: Ashish Kushwaha on 19-01-2026 22:30
File: FileChunk.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.transfer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class FileChunk {

    private final int chunkIndex;
    private final byte[] data;

    public FileChunk(int chunkIndex, byte[] data) {
        this.chunkIndex = chunkIndex;
        this.data = Arrays.copyOf(data, data.length);
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public int getDataLength() {
        return data.length;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + data.length);
        buffer.putInt(chunkIndex);
        buffer.putInt(data.length);
        buffer.put(data);
        return buffer.array();
    }

    public static FileChunk fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int chunkIndex = buffer.getInt();
        int dataLength = buffer.getInt();
        byte[] data = new byte[dataLength];
        buffer.get(data);
        return new FileChunk(chunkIndex, data);
    }
}
