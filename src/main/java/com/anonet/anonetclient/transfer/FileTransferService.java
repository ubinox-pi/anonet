/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.transfer
 * Created by: Ashish Kushwaha on 19-01-2026 22:30
 * File: FileTransferService.java
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
import com.anonet.anonetclient.lan.LanPeer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class FileTransferService {

    private final LocalIdentity localIdentity;
    private final Path downloadDirectory;
    private final ExecutorService executorService;
    private LanFileReceiver receiver;
    private volatile boolean receiverRunning;

    public FileTransferService(LocalIdentity localIdentity, Path downloadDirectory) {
        this.localIdentity = localIdentity;
        this.downloadDirectory = downloadDirectory;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "FileTransfer");
            thread.setDaemon(true);
            return thread;
        });
        this.receiverRunning = false;
    }

    public void startReceiver(Consumer<TransferProgress> progressCallback, Consumer<File> fileReceivedCallback) {
        if (receiverRunning) {
            return;
        }

        receiver = new LanFileReceiver(localIdentity, TransferProtocol.TRANSFER_PORT, downloadDirectory.toFile());
        receiver.setProgressCallback(progressCallback);
        receiver.setFileReceivedCallback(fileReceivedCallback);

        executorService.submit(() -> {
            try {
                receiver.startListening();
                receiverRunning = true;
                while (receiverRunning && receiver.isRunning()) {
                    try {
                        receiver.acceptAndReceive();
                    } catch (FileTransferException e) {
                        if (receiverRunning) {
                            System.err.println("Transfer error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to start receiver: " + e.getMessage());
            }
        });
    }

    public void stopReceiver() {
        receiverRunning = false;
        if (receiver != null) {
            receiver.stop();
        }
    }

    public void sendFile(LanPeer peer, File file, Consumer<TransferProgress> progressCallback, Runnable onComplete, Consumer<String> onError) {
        executorService.submit(() -> {
            LanFileSender sender = new LanFileSender(
                    localIdentity,
                    peer.getIpAddress(),
                    TransferProtocol.TRANSFER_PORT,
                    file
            );
            sender.setProgressCallback(progressCallback);

            try {
                sender.send();
                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (FileTransferException e) {
                if (onError != null) {
                    onError.accept(e.getMessage());
                }
            }
        });
    }

    public void shutdown() {
        stopReceiver();
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isReceiverRunning() {
        return receiverRunning;
    }

    public Path getDownloadDirectory() {
        return downloadDirectory;
    }
}
