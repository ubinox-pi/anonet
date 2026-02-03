/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 02-02-2026 12:40
 * File: QrCodeGenerator.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.identity;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.Arrays;

public final class QrCodeGenerator {

    private static final int QUIET_ZONE = 4;
    private static final Color DARK_COLOR = Color.BLACK;
    private static final Color LIGHT_COLOR = Color.WHITE;

    private QrCodeGenerator() {}

    public static Image generateQrCode(AnonetId anonetId, int size) {
        String data = anonetId.toIdString();
        return generateQrCode(data, size);
    }

    public static Image generateQrCode(String data, int size) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("Data cannot be null or blank");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }

        QrMatrix matrix = generateQrMatrix(data);
        return matrixToImage(matrix, size);
    }

    private static QrMatrix generateQrMatrix(String data) {
        int version = determineVersion(data);
        int size = 17 + 4 * version;
        QrMatrix matrix = new QrMatrix(size);

        addFinderPatterns(matrix);
        addSeparators(matrix);
        addDarkModule(matrix, version);
        addTimingPatterns(matrix);

        if (version >= 2) {
            addAlignmentPatterns(matrix, version);
        }

        String encodedData = encodeData(data, version);
        addDataAndErrorCorrection(matrix, encodedData);
        applyMask(matrix, 0);

        return matrix;
    }

    private static int determineVersion(String data) {
        int dataLength = data.length();
        if (dataLength <= 25) return 1;
        if (dataLength <= 47) return 2;
        if (dataLength <= 77) return 3;
        if (dataLength <= 114) return 4;
        return 5;
    }

    private static void addFinderPatterns(QrMatrix matrix) {
        int[][] pattern = {
            {1,1,1,1,1,1,1},
            {1,0,0,0,0,0,1},
            {1,0,1,1,1,0,1},
            {1,0,1,1,1,0,1},
            {1,0,1,1,1,0,1},
            {1,0,0,0,0,0,1},
            {1,1,1,1,1,1,1}
        };

        addPattern(matrix, pattern, 0, 0);
        addPattern(matrix, pattern, matrix.size - 7, 0);
        addPattern(matrix, pattern, 0, matrix.size - 7);
    }

    private static void addPattern(QrMatrix matrix, int[][] pattern, int startRow, int startCol) {
        for (int i = 0; i < pattern.length; i++) {
            for (int j = 0; j < pattern[i].length; j++) {
                if (startRow + i < matrix.size && startCol + j < matrix.size) {
                    matrix.set(startRow + i, startCol + j, pattern[i][j] == 1);
                }
            }
        }
    }

    private static void addSeparators(QrMatrix matrix) {
        for (int i = 0; i < 8; i++) {
            matrix.set(7, i, false);
            matrix.set(i, 7, false);
            matrix.set(matrix.size - 8, i, false);
            matrix.set(matrix.size - 8 + i, 7, false);
            matrix.set(7, matrix.size - 8 + i, false);
            matrix.set(i, matrix.size - 8, false);
        }
    }

    private static void addDarkModule(QrMatrix matrix, int version) {
        matrix.set(4 * version + 9, 8, true);
    }

    private static void addTimingPatterns(QrMatrix matrix) {
        for (int i = 8; i < matrix.size - 8; i++) {
            matrix.set(6, i, i % 2 == 0);
            matrix.set(i, 6, i % 2 == 0);
        }
    }

    private static void addAlignmentPatterns(QrMatrix matrix, int version) {
        int[] positions = getAlignmentPatternPositions(version);
        for (int row : positions) {
            for (int col : positions) {
                if (!isInFinderPattern(row, col, matrix.size)) {
                    addAlignmentPattern(matrix, row, col);
                }
            }
        }
    }

    private static int[] getAlignmentPatternPositions(int version) {
        switch (version) {
            case 2: return new int[]{6, 18};
            case 3: return new int[]{6, 22};
            case 4: return new int[]{6, 26};
            case 5: return new int[]{6, 30};
            default: return new int[]{6, 18};
        }
    }

    private static boolean isInFinderPattern(int row, int col, int size) {
        return (row < 9 && col < 9) ||
               (row < 9 && col >= size - 8) ||
               (row >= size - 8 && col < 9);
    }

    private static void addAlignmentPattern(QrMatrix matrix, int centerRow, int centerCol) {
        int[][] pattern = {
            {1,1,1,1,1},
            {1,0,0,0,1},
            {1,0,1,0,1},
            {1,0,0,0,1},
            {1,1,1,1,1}
        };

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                int row = centerRow - 2 + i;
                int col = centerCol - 2 + j;
                if (row >= 0 && row < matrix.size && col >= 0 && col < matrix.size) {
                    matrix.set(row, col, pattern[i][j] == 1);
                }
            }
        }
    }

    private static String encodeData(String data, int version) {
        StringBuilder encoded = new StringBuilder();

        encoded.append("0100");

        String lengthBits = String.format("%8s", Integer.toBinaryString(data.length())).replace(' ', '0');
        encoded.append(lengthBits);

        for (char c : data.toCharArray()) {
            String charBits = String.format("%8s", Integer.toBinaryString((int) c)).replace(' ', '0');
            encoded.append(charBits);
        }

        int capacities[] = {152, 272, 440, 640, 864};
        int capacity = capacities[Math.min(version - 1, capacities.length - 1)];

        while (encoded.length() < capacity && encoded.length() % 4 != 0) {
            encoded.append("0");
        }

        while (encoded.length() < capacity) {
            encoded.append("1110110000010001");
            if (encoded.length() > capacity) {
                encoded.setLength(capacity);
            }
        }

        return encoded.toString();
    }

    private static void addDataAndErrorCorrection(QrMatrix matrix, String data) {
        boolean up = true;
        int dataIndex = 0;

        for (int col = matrix.size - 1; col > 0; col -= 2) {
            if (col == 6) col--;

            for (int i = 0; i < matrix.size; i++) {
                int row = up ? matrix.size - 1 - i : i;

                for (int c = 0; c < 2; c++) {
                    int currentCol = col - c;
                    if (!matrix.isReserved(row, currentCol)) {
                        boolean bit = false;
                        if (dataIndex < data.length()) {
                            bit = data.charAt(dataIndex) == '1';
                            dataIndex++;
                        }
                        matrix.set(row, currentCol, bit);
                    }
                }
            }
            up = !up;
        }
    }

    private static void applyMask(QrMatrix matrix, int maskPattern) {
        for (int row = 0; row < matrix.size; row++) {
            for (int col = 0; col < matrix.size; col++) {
                if (!matrix.isReserved(row, col)) {
                    boolean shouldFlip = false;
                    switch (maskPattern) {
                        case 0: shouldFlip = (row + col) % 2 == 0; break;
                        case 1: shouldFlip = row % 2 == 0; break;
                        case 2: shouldFlip = col % 3 == 0; break;
                        default: shouldFlip = (row + col) % 2 == 0; break;
                    }
                    if (shouldFlip) {
                        matrix.flip(row, col);
                    }
                }
            }
        }
    }

    private static Image matrixToImage(QrMatrix matrix, int size) {
        int moduleSize = size / (matrix.size + 2 * QUIET_ZONE);
        int actualSize = moduleSize * (matrix.size + 2 * QUIET_ZONE);

        WritableImage image = new WritableImage(actualSize, actualSize);
        PixelWriter writer = image.getPixelWriter();

        for (int y = 0; y < actualSize; y++) {
            for (int x = 0; x < actualSize; x++) {
                int moduleRow = (y / moduleSize) - QUIET_ZONE;
                int moduleCol = (x / moduleSize) - QUIET_ZONE;

                boolean isDark = false;
                if (moduleRow >= 0 && moduleRow < matrix.size &&
                    moduleCol >= 0 && moduleCol < matrix.size) {
                    isDark = matrix.get(moduleRow, moduleCol);
                }

                writer.setColor(x, y, isDark ? DARK_COLOR : LIGHT_COLOR);
            }
        }

        return image;
    }

    private static class QrMatrix {
        private final int size;
        private final boolean[][] data;
        private final boolean[][] reserved;

        public QrMatrix(int size) {
            this.size = size;
            this.data = new boolean[size][size];
            this.reserved = new boolean[size][size];
            markReservedAreas();
        }

        private void markReservedAreas() {
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    reserved[i][j] = true;
                    reserved[size - 9 + i][j] = true;
                    reserved[i][size - 9 + j] = true;
                }
            }

            for (int i = 8; i < size - 8; i++) {
                reserved[6][i] = true;
                reserved[i][6] = true;
            }
        }

        public void set(int row, int col, boolean value) {
            if (row >= 0 && row < size && col >= 0 && col < size) {
                data[row][col] = value;
            }
        }

        public boolean get(int row, int col) {
            return row >= 0 && row < size && col >= 0 && col < size && data[row][col];
        }

        public void flip(int row, int col) {
            if (row >= 0 && row < size && col >= 0 && col < size) {
                data[row][col] = !data[row][col];
            }
        }

        public boolean isReserved(int row, int col) {
            return row >= 0 && row < size && col >= 0 && col < size && reserved[row][col];
        }
    }
}
