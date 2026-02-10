/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 13:00
 * File: TransferHistoryPanel.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TransferHistoryPanel extends VBox {

    private final ObservableList<TransferRecord> historyList;
    private final CopyOnWriteArrayList<TransferRecord> allRecords;
    private final ListView<TransferRecord> listView;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, HH:mm");
    private static final int MAX_HISTORY = 100;

    public TransferHistoryPanel() {
        this.historyList = FXCollections.observableArrayList();
        this.allRecords = new CopyOnWriteArrayList<>();

        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #16213e; -fx-background-radius: 5;");

        Label title = new Label("📋 Transfer History");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: #ffffff;");

        listView = new ListView<>(historyList);
        listView.setCellFactory(lv -> new TransferRecordCell());
        listView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        listView.setPrefHeight(200);
        VBox.setVgrow(listView, Priority.ALWAYS);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button clearButton = new Button("Clear History");
        clearButton.setStyle("-fx-background-color: #e94560; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        clearButton.setOnAction(e -> clearHistory());

        buttonBox.getChildren().add(clearButton);

        Label emptyLabel = new Label("No transfers yet");
        emptyLabel.setStyle("-fx-text-fill: #a0a0a0;");
        listView.setPlaceholder(emptyLabel);

        getChildren().addAll(title, listView, buttonBox);
    }

    public void addSentRecord(String fileName, long fileSize, String recipientUsername, boolean success) {
        TransferRecord record = new TransferRecord(
            TransferDirection.SENT,
            fileName,
            fileSize,
            recipientUsername,
            System.currentTimeMillis(),
            success ? TransferStatus.COMPLETED : TransferStatus.FAILED
        );
        addRecord(record);
    }

    public void addReceivedRecord(String fileName, long fileSize, String senderUsername, boolean success) {
        TransferRecord record = new TransferRecord(
            TransferDirection.RECEIVED,
            fileName,
            fileSize,
            senderUsername,
            System.currentTimeMillis(),
            success ? TransferStatus.COMPLETED : TransferStatus.FAILED
        );
        addRecord(record);
    }

    public void addRecord(TransferRecord record) {
        allRecords.add(0, record);
        while (allRecords.size() > MAX_HISTORY) {
            allRecords.remove(allRecords.size() - 1);
        }
        javafx.application.Platform.runLater(() -> {
            historyList.setAll(allRecords);
        });
    }

    public void clearHistory() {
        allRecords.clear();
        javafx.application.Platform.runLater(historyList::clear);
    }

    public int getHistoryCount() {
        return allRecords.size();
    }

    private static class TransferRecordCell extends ListCell<TransferRecord> {
        @Override
        protected void updateItem(TransferRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPadding(new Insets(5));

                String icon = record.direction == TransferDirection.SENT ? "📤" : "📥";
                String statusIcon = switch (record.status) {
                    case COMPLETED -> "✓";
                    case FAILED -> "✗";
                    case IN_PROGRESS -> "⏳";
                    case CANCELLED -> "⊘";
                };

                Label iconLabel = new Label(icon);
                iconLabel.setStyle("-fx-font-size: 16;");

                VBox infoBox = new VBox(2);
                Label fileLabel = new Label(record.fileName);
                fileLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11;");

                String sizeStr = formatFileSize(record.fileSize);
                String timeStr = formatTime(record.timestamp);
                Label detailLabel = new Label(sizeStr + " • " + record.peerUsername + " • " + timeStr);
                detailLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 9;");

                infoBox.getChildren().addAll(fileLabel, detailLabel);
                HBox.setHgrow(infoBox, Priority.ALWAYS);

                Label statusLabel = new Label(statusIcon);
                statusLabel.setStyle(record.status == TransferStatus.COMPLETED ?
                    "-fx-text-fill: #16c79a; -fx-font-size: 14;" :
                    "-fx-text-fill: #e94560; -fx-font-size: 14;");

                cell.getChildren().addAll(iconLabel, infoBox, statusLabel);
                setGraphic(cell);
                setStyle("-fx-background-color: transparent;");
            }
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }

        private String formatTime(long timestamp) {
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            return time.format(TIME_FORMAT);
        }
    }

    public enum TransferDirection {
        SENT, RECEIVED
    }

    public enum TransferStatus {
        IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    public static final class TransferRecord {
        private final TransferDirection direction;
        private final String fileName;
        private final long fileSize;
        private final String peerUsername;
        private final long timestamp;
        private final TransferStatus status;

        public TransferRecord(TransferDirection direction, String fileName, long fileSize,
                            String peerUsername, long timestamp, TransferStatus status) {
            this.direction = direction;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.peerUsername = peerUsername;
            this.timestamp = timestamp;
            this.status = status;
        }

        public TransferDirection getDirection() { return direction; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public String getPeerUsername() { return peerUsername; }
        public long getTimestamp() { return timestamp; }
        public TransferStatus getStatus() { return status; }
    }
}
