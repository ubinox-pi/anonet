/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.transfer
Created by: Ashish Kushwaha on 19-01-2026 22:30
File: LanFileSender.java
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
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

public final class LanFileSender {

    private final LocalIdentity localIdentity;
    private final InetAddress peerAddress;
    private final int peerPort;
    private final File fileToSend;
    private volatile boolean cancelled;
    private Consumer<TransferProgress> progressCallback;

    public LanFileSender(LocalIdentity localIdentity, InetAddress peerAddress, int peerPort, File fileToSend) {
        this.localIdentity = localIdentity;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.fileToSend = fileToSend;
        this.cancelled = false;
    }

    public void setProgressCallback(Consumer<TransferProgress> callback) {
        this.progressCallback = callback;
    }

    public void send() throws FileTransferException {
        if (!fileToSend.exists() || !fileToSend.isFile()) {
            throw new FileTransferException("File does not exist: " + fileToSend.getAbsolutePath());
        }

        Socket socket = null;
        SecureSocketChannel secureChannel = null;

        try {
            reportProgress(0, fileToSend.length(), 0, 0, TransferProgress.TransferState.CONNECTING);

            socket = new Socket();
            socket.setSoTimeout(TransferProtocol.SOCKET_TIMEOUT_MS);
            socket.connect(new InetSocketAddress(peerAddress, peerPort), TransferProtocol.CONNECT_TIMEOUT_MS);

            reportProgress(0, fileToSend.length(), 0, 0, TransferProgress.TransferState.HANDSHAKING);

            secureChannel = HandshakeHelper.performSenderHandshake(socket, localIdentity);

            FileMetadata metadata = new FileMetadata(fileToSend.getName(), fileToSend.length());
            secureChannel.sendMessage(TransferProtocol.MSG_FILE_METADATA, metadata.toBytes());

            reportProgress(0, metadata.getFileSize(), 0, metadata.getTotalChunks(), TransferProgress.TransferState.TRANSFERRING);

            sendFileChunks(secureChannel, metadata);

            secureChannel.sendMessage(TransferProtocol.MSG_TRANSFER_COMPLETE, new byte[0]);

            reportProgress(metadata.getFileSize(), metadata.getFileSize(), metadata.getTotalChunks(), metadata.getTotalChunks(), TransferProgress.TransferState.COMPLETING);

            SecureSocketChannel.ReceivedMessage ackMessage = secureChannel.receiveMessage();
            if (ackMessage.getMessageType() != TransferProtocol.MSG_TRANSFER_ACK) {
                throw new FileTransferException("Did not receive transfer acknowledgment");
            }

            reportProgress(metadata.getFileSize(), metadata.getFileSize(), metadata.getTotalChunks(), metadata.getTotalChunks(), TransferProgress.TransferState.COMPLETED);

        } catch (IOException e) {
            reportProgress(0, fileToSend.length(), 0, 0, TransferProgress.TransferState.FAILED);
            throw new FileTransferException("File transfer failed: " + e.getMessage(), e);
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

    private void sendFileChunks(SecureSocketChannel channel, FileMetadata metadata) throws IOException {
        byte[] buffer = new byte[TransferProtocol.CHUNK_SIZE];
        int chunkIndex = 0;
        long bytesTransferred = 0;

        try (FileInputStream fis = new FileInputStream(fileToSend)) {
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1 && !cancelled) {
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                FileChunk chunk = new FileChunk(chunkIndex, chunkData);
                channel.sendMessage(TransferProtocol.MSG_FILE_CHUNK, chunk.toBytes());

                bytesTransferred += bytesRead;
                chunkIndex++;

                reportProgress(bytesTransferred, metadata.getFileSize(), chunkIndex, metadata.getTotalChunks(), TransferProgress.TransferState.TRANSFERRING);
            }
        }

        if (cancelled) {
            throw new FileTransferException("Transfer was cancelled");
        }
    }

    public void cancel() {
        this.cancelled = true;
    }

    private void reportProgress(long bytesTransferred, long totalBytes, int chunksTransferred, int totalChunks, TransferProgress.TransferState state) {
        if (progressCallback != null) {
            TransferProgress progress = new TransferProgress(bytesTransferred, totalBytes, chunksTransferred, totalChunks, state);
            progressCallback.accept(progress);
        }
    }
}
