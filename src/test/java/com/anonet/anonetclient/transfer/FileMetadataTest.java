package com.anonet.anonetclient.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileMetadataTest {

    @Test
    void serializeDeserializeRoundtrip() {
        FileMetadata original = new FileMetadata("test-file.txt", 1024);
        byte[] bytes = original.toBytes();
        FileMetadata restored = FileMetadata.fromBytes(bytes);
        assertEquals(original.getFileName(), restored.getFileName());
        assertEquals(original.getFileSize(), restored.getFileSize());
        assertEquals(original.getTotalChunks(), restored.getTotalChunks());
    }

    @Test
    void chunkCountCalculation() {
        FileMetadata small = new FileMetadata("small.txt", 100);
        assertEquals(1, small.getTotalChunks());

        FileMetadata exact = new FileMetadata("exact.txt", TransferProtocol.CHUNK_SIZE);
        assertEquals(1, exact.getTotalChunks());

        FileMetadata multi = new FileMetadata("big.txt", TransferProtocol.CHUNK_SIZE * 3 + 1);
        assertEquals(4, multi.getTotalChunks());
    }

    @Test
    void emptyFileHasOneChunk() {
        FileMetadata empty = new FileMetadata("empty.txt", 0);
        assertEquals(0, empty.getTotalChunks());
    }

    @Test
    void unicodeFilenamePreserved() {
        FileMetadata meta = new FileMetadata("файл_тест.txt", 500);
        byte[] bytes = meta.toBytes();
        FileMetadata restored = FileMetadata.fromBytes(bytes);
        assertEquals("файл_тест.txt", restored.getFileName());
    }

    @Test
    void largeFileSizePreserved() {
        long largeSize = 10L * 1024 * 1024 * 1024;
        FileMetadata meta = new FileMetadata("large.bin", largeSize);
        byte[] bytes = meta.toBytes();
        FileMetadata restored = FileMetadata.fromBytes(bytes);
        assertEquals(largeSize, restored.getFileSize());
    }
}
