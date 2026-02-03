/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.transfer
 * Created by: Ashish Kushwaha on 19-01-2026 22:30
 * File: FileMetadata.java
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class FileMetadata {

    private final String fileName;
    private final long fileSize;
    private final int totalChunks;

    public FileMetadata(String fileName, long fileSize) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.totalChunks = (int) Math.ceil((double) fileSize / TransferProtocol.CHUNK_SIZE);
    }

    private FileMetadata(String fileName, long fileSize, int totalChunks) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.totalChunks = totalChunks;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public byte[] toBytes() {
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + fileNameBytes.length + 8 + 4);
        buffer.putInt(fileNameBytes.length);
        buffer.put(fileNameBytes);
        buffer.putLong(fileSize);
        buffer.putInt(totalChunks);
        return buffer.array();
    }

    public static FileMetadata fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int fileNameLength = buffer.getInt();
        byte[] fileNameBytes = new byte[fileNameLength];
        buffer.get(fileNameBytes);
        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
        long fileSize = buffer.getLong();
        int totalChunks = buffer.getInt();
        return new FileMetadata(fileName, fileSize, totalChunks);
    }
}
