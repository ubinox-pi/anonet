/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.transfer
 * Created by: Ashish Kushwaha on 19-01-2026 22:30
 * File: LanFileReceiver.java
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

import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.logging.AnonetLogger;
import com.anonet.anonetclient.security.RateLimiter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

public final class LanFileReceiver {

    private static final AnonetLogger LOG = AnonetLogger.get(LanFileReceiver.class);

    private final LocalIdentity localIdentity;
    private final int listenPort;
    private final File downloadDirectory;
    private volatile boolean running;
    private volatile boolean cancelled;
    private ServerSocket serverSocket;
    private Consumer<TransferProgress> progressCallback;
    private Consumer<File> fileReceivedCallback;
    private Function<String, Boolean> trustChecker;
    private RateLimiter rateLimiter;
    private int actualPort;

    public LanFileReceiver(LocalIdentity localIdentity, int listenPort, File downloadDirectory) {
        this.localIdentity = localIdentity;
        this.listenPort = listenPort;
        this.downloadDirectory = downloadDirectory;
        this.running = false;
        this.cancelled = false;
    }

    public void setProgressCallback(Consumer<TransferProgress> callback) {
        this.progressCallback = callback;
    }

    public void setFileReceivedCallback(Consumer<File> callback) {
        this.fileReceivedCallback = callback;
    }

    public void setTrustChecker(Function<String, Boolean> trustChecker) {
        this.trustChecker = trustChecker;
    }

    public void setRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public int getActualPort() {
        return actualPort;
    }

    public void startListening() throws IOException {
        if (running) {
            return;
        }

        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs();
        }

        serverSocket = bindPort(listenPort, 5);
        serverSocket.setSoTimeout(0);
        actualPort = serverSocket.getLocalPort();
        running = true;
        LOG.info("File receiver listening on port %d", actualPort);
    }

    public void acceptAndReceive() throws FileTransferException {
        if (!running || serverSocket == null) {
            throw new FileTransferException("Receiver not started");
        }

        Socket socket = null;
        SecureSocketChannel secureChannel = null;

        try {
            reportProgress(0, 0, 0, 0, TransferProgress.TransferState.CONNECTING);

            socket = serverSocket.accept();
            if (rateLimiter != null && !rateLimiter.tryAcquire(socket.getInetAddress())) {
                LOG.warn("Rate limit exceeded for %s", socket.getInetAddress().getHostAddress());
                socket.close();
                return;
            }
            socket.setSoTimeout(TransferProtocol.SOCKET_TIMEOUT_MS);

            reportProgress(0, 0, 0, 0, TransferProgress.TransferState.HANDSHAKING);

            HandshakeHelper.AuthenticatedChannel authChannel = HandshakeHelper.performReceiverHandshake(socket, localIdentity);
            secureChannel = authChannel.channel();
            String peerFingerprint = authChannel.peerFingerprint();
            LOG.info("Peer fingerprint: %s", peerFingerprint.substring(0, Math.min(16, peerFingerprint.length())));

            if (trustChecker != null && !trustChecker.apply(peerFingerprint)) {
                LOG.warn("Untrusted peer rejected: %s", peerFingerprint.substring(0, Math.min(16, peerFingerprint.length())));
                reportProgress(0, 0, 0, 0, TransferProgress.TransferState.FAILED);
                return;
            }

            SecureSocketChannel.ReceivedMessage metadataMessage = secureChannel.receiveMessage();
            if (metadataMessage.getMessageType() != TransferProtocol.MSG_FILE_METADATA) {
                throw new FileTransferException("Expected file metadata, got: " + metadataMessage.getMessageType());
            }

            FileMetadata metadata = FileMetadata.fromBytes(metadataMessage.getPayload());
            LOG.info("Receiving file: %s (%d bytes)", metadata.getFileName(), metadata.getFileSize());

            reportProgress(0, metadata.getFileSize(), 0, metadata.getTotalChunks(), TransferProgress.TransferState.TRANSFERRING);

            String safeName = Path.of(metadata.getFileName()).getFileName().toString();
            if (safeName.isEmpty() || safeName.startsWith(".")) {
                safeName = "received_" + System.currentTimeMillis();
            }
            if (safeName.length() > 255) {
                safeName = safeName.substring(0, 255);
            }
            File outputFile = new File(downloadDirectory, safeName);
            receiveFileChunks(secureChannel, metadata, outputFile);

            secureChannel.sendMessage(TransferProtocol.MSG_TRANSFER_ACK, new byte[0]);

            reportProgress(metadata.getFileSize(), metadata.getFileSize(), metadata.getTotalChunks(), metadata.getTotalChunks(), TransferProgress.TransferState.COMPLETED);
            LOG.info("File received successfully: %s", metadata.getFileName());

            if (fileReceivedCallback != null) {
                fileReceivedCallback.accept(outputFile);
            }

        } catch (IOException e) {
            LOG.error("File receive failed: %s", e.getMessage());
            reportProgress(0, 0, 0, 0, TransferProgress.TransferState.FAILED);
            throw new FileTransferException("File receive failed: " + e.getMessage(), e);
        } finally {
            if (secureChannel != null) {
                secureChannel.close();
            } else if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void receiveFileChunks(SecureSocketChannel channel, FileMetadata metadata, File outputFile) throws IOException {
        long bytesReceived = 0;
        int chunksReceived = 0;

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            while (bytesReceived < metadata.getFileSize() && !cancelled) {
                SecureSocketChannel.ReceivedMessage message = channel.receiveMessage();

                if (message.getMessageType() == TransferProtocol.MSG_TRANSFER_COMPLETE) {
                    break;
                }

                if (message.getMessageType() != TransferProtocol.MSG_FILE_CHUNK) {
                    throw new FileTransferException("Unexpected message type: " + message.getMessageType());
                }

                FileChunk chunk = FileChunk.fromBytes(message.getPayload());

                if (chunk.getChunkIndex() != chunksReceived) {
                    throw new FileTransferException("Chunk out of order. Expected: " + chunksReceived + ", got: " + chunk.getChunkIndex());
                }

                fos.write(chunk.getData());
                bytesReceived += chunk.getDataLength();
                chunksReceived++;

                reportProgress(bytesReceived, metadata.getFileSize(), chunksReceived, metadata.getTotalChunks(), TransferProgress.TransferState.TRANSFERRING);
            }
        }

        if (cancelled) {
            outputFile.delete();
            throw new FileTransferException("Transfer was cancelled");
        }

        if (bytesReceived != metadata.getFileSize()) {
            outputFile.delete();
            throw new FileTransferException("File size mismatch. Expected: " + metadata.getFileSize() + ", received: " + bytesReceived);
        }

        reportProgress(bytesReceived, metadata.getFileSize(), chunksReceived, metadata.getTotalChunks(), TransferProgress.TransferState.COMPLETING);
    }

    public void stop() {
        running = false;
        cancelled = true;
        LOG.info("File receiver stopped");
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getListenPort() {
        return listenPort;
    }

    private void reportProgress(long bytesTransferred, long totalBytes, int chunksTransferred, int totalChunks, TransferProgress.TransferState state) {
        if (progressCallback != null) {
            TransferProgress progress = new TransferProgress(bytesTransferred, totalBytes, chunksTransferred, totalChunks, state);
            progressCallback.accept(progress);
        }
    }

    private ServerSocket bindPort(int preferredPort, int maxRetries) throws IOException {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return new ServerSocket(preferredPort + i);
            } catch (BindException e) {
                if (i == maxRetries) throw e;
                LOG.warn("Port %d in use, trying %d", preferredPort + i, preferredPort + i + 1);
            }
        }
        throw new IOException("No available port");
    }
}
