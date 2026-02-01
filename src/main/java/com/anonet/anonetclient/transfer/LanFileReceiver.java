/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.transfer
Created by: Ashish Kushwaha on 19-01-2026 22:30
File: LanFileReceiver.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.transfer;

import com.anonet.anonetclient.identity.LocalIdentity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public final class LanFileReceiver {

    private final LocalIdentity localIdentity;
    private final int listenPort;
    private final File downloadDirectory;
    private volatile boolean running;
    private volatile boolean cancelled;
    private ServerSocket serverSocket;
    private Consumer<TransferProgress> progressCallback;
    private Consumer<File> fileReceivedCallback;

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

    public void startListening() throws IOException {
        if (running) {
            return;
        }

        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs();
        }

        serverSocket = new ServerSocket(listenPort);
        serverSocket.setSoTimeout(0);
        running = true;
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
            socket.setSoTimeout(TransferProtocol.SOCKET_TIMEOUT_MS);

            reportProgress(0, 0, 0, 0, TransferProgress.TransferState.HANDSHAKING);

            secureChannel = HandshakeHelper.performReceiverHandshake(socket, localIdentity);

            SecureSocketChannel.ReceivedMessage metadataMessage = secureChannel.receiveMessage();
            if (metadataMessage.getMessageType() != TransferProtocol.MSG_FILE_METADATA) {
                throw new FileTransferException("Expected file metadata, got: " + metadataMessage.getMessageType());
            }

            FileMetadata metadata = FileMetadata.fromBytes(metadataMessage.getPayload());

            reportProgress(0, metadata.getFileSize(), 0, metadata.getTotalChunks(), TransferProgress.TransferState.TRANSFERRING);

            File outputFile = new File(downloadDirectory, metadata.getFileName());
            receiveFileChunks(secureChannel, metadata, outputFile);

            secureChannel.sendMessage(TransferProtocol.MSG_TRANSFER_ACK, new byte[0]);

            reportProgress(metadata.getFileSize(), metadata.getFileSize(), metadata.getTotalChunks(), metadata.getTotalChunks(), TransferProgress.TransferState.COMPLETED);

            if (fileReceivedCallback != null) {
                fileReceivedCallback.accept(outputFile);
            }

        } catch (IOException e) {
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
}
