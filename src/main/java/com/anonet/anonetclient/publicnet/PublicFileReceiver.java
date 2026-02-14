/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.publicnet
 * Created by: Ashish Kushwaha on 03-02-2026 10:45
 * File: PublicFileReceiver.java
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
import com.anonet.anonetclient.logging.AnonetLogger;
import com.anonet.anonetclient.transfer.FileMetadata;
import com.anonet.anonetclient.transfer.TransferProgress;
import com.anonet.anonetclient.transfer.TransferProtocol;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PublicFileReceiver {

    private static final AnonetLogger LOG = AnonetLogger.get(PublicFileReceiver.class);

    private static final int CHUNK_SIZE = TransferProtocol.CHUNK_SIZE;
    private static final byte MSG_METADATA = 0x01;
    private static final byte MSG_CHUNK = 0x02;
    private static final byte MSG_COMPLETE = 0x03;
    private static final byte MSG_ACK = 0x04;
    private static final byte MSG_ERROR = 0x05;

    private final LocalIdentity identity;
    private final UdpChannel channel;
    private final Path downloadDirectory;
    private Consumer<TransferProgress> progressCallback;
    private Consumer<String> statusCallback;
    private Consumer<Path> completionCallback;

    public PublicFileReceiver(LocalIdentity identity, UdpChannel channel, Path downloadDirectory) {
        this.identity = identity;
        this.channel = channel;
        this.downloadDirectory = downloadDirectory;
    }

    public void setProgressCallback(Consumer<TransferProgress> callback) {
        this.progressCallback = callback;
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
        channel.setStatusCallback(callback);
    }

    public void setCompletionCallback(Consumer<Path> callback) {
        this.completionCallback = callback;
    }

    public Path receiveFile() throws IOException, InterruptedException {
        notifyStatus("Waiting for file transfer...");

        byte[] metadataPacket = channel.receive(60, TimeUnit.SECONDS);
        if (metadataPacket == null || metadataPacket[0] != MSG_METADATA) {
            throw new IOException("Expected metadata packet");
        }

        byte[] metadataBytes = new byte[metadataPacket.length - 1];
        System.arraycopy(metadataPacket, 1, metadataBytes, 0, metadataBytes.length);
        FileMetadata metadata = FileMetadata.fromBytes(metadataBytes);

        notifyStatus("Receiving: " + metadata.getFileName() + " (" + formatSize(metadata.getFileSize()) + ")");
        LOG.info("Receiving file: %s (%s)", metadata.getFileName(), formatSize(metadata.getFileSize()));

        Path targetFile = downloadDirectory.resolve(metadata.getFileName());
        Files.createDirectories(downloadDirectory);

        sendAck();

        try (RandomAccessFile raf = new RandomAccessFile(targetFile.toFile(), "rw")) {
            raf.setLength(metadata.getFileSize());

            long receivedBytes = 0;
            boolean complete = false;

            while (!complete) {
                byte[] packet = channel.receive(30, TimeUnit.SECONDS);
                if (packet == null) {
                    throw new IOException("Transfer timeout");
                }

                byte msgType = packet[0];

                if (msgType == MSG_CHUNK) {
                    ByteBuffer buffer = ByteBuffer.wrap(packet, 1, packet.length - 1);
                    long chunkIndex = buffer.getLong();
                    byte[] chunkData = new byte[buffer.remaining()];
                    buffer.get(chunkData);

                    raf.seek(chunkIndex * CHUNK_SIZE);
                    raf.write(chunkData);

                    receivedBytes += chunkData.length;
                    notifyProgress(metadata.getFileName(), receivedBytes, metadata.getFileSize());

                } else if (msgType == MSG_COMPLETE) {
                    String expectedHash = new String(packet, 1, packet.length - 1);
                    String actualHash = computeFileHash(targetFile);

                    if (actualHash.equals(expectedHash)) {
                        complete = true;
                        sendAck();
                        LOG.info("File received and verified: %s", metadata.getFileName());
                        notifyStatus("File received and verified: " + metadata.getFileName());
                    } else {
                        LOG.error("File hash mismatch for %s", metadata.getFileName());
                        sendError("Hash mismatch");
                        throw new IOException("File hash mismatch");
                    }
                }
            }
        }

        if (completionCallback != null) {
            completionCallback.accept(targetFile);
        }

        return targetFile;
    }

    public void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                while (channel.isConnected()) {
                    receiveFile();
                }
            } catch (Exception e) {
                notifyStatus("Receiver stopped: " + e.getMessage());
            }
        }, "PublicFileReceiver");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void sendAck() throws IOException {
        channel.send(new byte[]{MSG_ACK});
    }

    private void sendError(String message) throws IOException {
        byte[] msgBytes = message.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(1 + msgBytes.length);
        buffer.put(MSG_ERROR);
        buffer.put(msgBytes);
        channel.send(buffer.array());
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

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
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
