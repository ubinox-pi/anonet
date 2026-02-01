/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.transfer
Created by: Ashish Kushwaha on 19-01-2026 22:30
File: TransferProgress.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.transfer;

public final class TransferProgress {

    private final long bytesTransferred;
    private final long totalBytes;
    private final int chunksTransferred;
    private final int totalChunks;
    private final TransferState state;

    public TransferProgress(long bytesTransferred, long totalBytes, int chunksTransferred, int totalChunks, TransferState state) {
        this.bytesTransferred = bytesTransferred;
        this.totalBytes = totalBytes;
        this.chunksTransferred = chunksTransferred;
        this.totalChunks = totalChunks;
        this.state = state;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public int getChunksTransferred() {
        return chunksTransferred;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public TransferState getState() {
        return state;
    }

    public double getPercentage() {
        if (totalBytes == 0) return 0;
        return (double) bytesTransferred / totalBytes * 100;
    }

    public enum TransferState {
        CONNECTING,
        HANDSHAKING,
        TRANSFERRING,
        COMPLETING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
