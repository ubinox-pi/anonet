/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 03-02-2026 10:30
 * File: PublicFileSender.java
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

import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.transfer.FileMetadata;
import com.anonet.anonetclient.transfer.TransferProgress;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PublicFileSender {

    private static final int CHUNK_SIZE = 1024;
    private static final byte MSG_METADATA = 0x01;
    private static final byte MSG_CHUNK = 0x02;
    private static final byte MSG_COMPLETE = 0x03;
    private static final byte MSG_ACK = 0x04;
    private static final byte MSG_ERROR = 0x05;

    private final LocalIdentity identity;
    private final UdpChannel channel;
    private Consumer<TransferProgress> progressCallback;
    private Consumer<String> statusCallback;

    public PublicFileSender(LocalIdentity identity, UdpChannel channel) {
        this.identity = identity;
        this.channel = channel;
    }

    public void setProgressCallback(Consumer<TransferProgress> callback) {
        this.progressCallback = callback;
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
        channel.setStatusCallback(callback);
    }

    public boolean sendFile(Path filePath) throws IOException {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IOException("File not found: " + filePath);
        }

        notifyStatus("Starting file transfer: " + filePath.getFileName());

        String fileName = filePath.getFileName().toString();
        long fileSize = Files.size(filePath);
        String fileHash = computeFileHash(filePath);

        FileMetadata metadata = new FileMetadata(fileName, fileSize);

        sendMetadata(metadata, fileHash);

        try {
            byte[] response = channel.receive(10, TimeUnit.SECONDS);
            if (response == null || response[0] != MSG_ACK) {
                notifyStatus("No acknowledgment received for metadata");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        notifyStatus("Metadata accepted, sending file data...");

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long totalChunks = (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
            byte[] buffer = new byte[CHUNK_SIZE];

            for (long chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                raf.seek(chunkIndex * CHUNK_SIZE);
                int bytesRead = raf.read(buffer);

                if (bytesRead > 0) {
                    byte[] chunkData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                    sendChunk(chunkIndex, chunkData);

                    long bytesTransferred = Math.min((chunkIndex + 1) * CHUNK_SIZE, fileSize);
                    notifyProgress(fileName, bytesTransferred, fileSize);
                }
            }
        }

        sendComplete(fileHash);

        try {
            byte[] finalResponse = channel.receive(30, TimeUnit.SECONDS);
            if (finalResponse != null && finalResponse[0] == MSG_ACK) {
                notifyStatus("File transfer completed successfully");
                return true;
            } else if (finalResponse != null && finalResponse[0] == MSG_ERROR) {
                String error = new String(finalResponse, 1, finalResponse.length - 1);
                notifyStatus("Transfer failed: " + error);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        notifyStatus("No final acknowledgment received");
        return false;
    }

    private void sendMetadata(FileMetadata metadata, String fileHash) throws IOException {
        byte[] metadataBytes = metadata.toBytes();
        byte[] hashBytes = fileHash.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(1 + metadataBytes.length + 2 + hashBytes.length);
        buffer.put(MSG_METADATA);
        buffer.put(metadataBytes);
        channel.sendAndWait(buffer.array());
    }

    private void sendChunk(long chunkIndex, byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 8 + data.length);
        buffer.put(MSG_CHUNK);
        buffer.putLong(chunkIndex);
        buffer.put(data);
        channel.send(buffer.array());
    }

    private void sendComplete(String fileHash) throws IOException {
        byte[] hashBytes = fileHash.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(1 + hashBytes.length);
        buffer.put(MSG_COMPLETE);
        buffer.put(hashBytes);
        channel.sendAndWait(buffer.array());
    }

    private String computeFileHash(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hash = digest.digest(fileBytes);

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IOException("Failed to compute file hash", e);
        }
    }

    private void notifyProgress(String fileName, long transferred, long total) {
        if (progressCallback != null) {
            int chunksTransferred = (int) (transferred / CHUNK_SIZE);
            int totalChunks = (int) ((total + CHUNK_SIZE - 1) / CHUNK_SIZE);
            TransferProgress progress = new TransferProgress(
                transferred, total, chunksTransferred, totalChunks,
                TransferProgress.TransferState.TRANSFERRING
            );
            progressCallback.accept(progress);
        }
    }

    private void notifyStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
    }
}
