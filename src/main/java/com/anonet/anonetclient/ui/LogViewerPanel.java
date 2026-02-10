/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 15:00
 * File: LogViewerPanel.java
 *
 * This source codeclaudes intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.ui;

import com.anonet.anonetclient.logging.AnonetLogger;
import com.anonet.anonetclient.logging.LogEntry;
import com.anonet.anonetclient.logging.LogExporter;
import com.anonet.anonetclient.logging.LogLevel;
import com.anonet.anonetclient.logging.LogStorage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class LogViewerPanel extends VBox {

    private static final AnonetLogger LOG = AnonetLogger.get(LogViewerPanel.class);

    private final ObservableList<LogEntry> displayedLogs;
    private final ListView<LogEntry> logListView;
    private TextField searchField;
    private ComboBox<String> sourceFilter;
    private CheckBox traceCheck;
    private CheckBox debugCheck;
    private CheckBox infoCheck;
    private CheckBox warnCheck;
    private CheckBox errorCheck;
    private CheckBox autoScrollCheck;
    private Label countLabel;
    private final LogStorage storage;

    public LogViewerPanel() {
        this.storage = AnonetLogger.getStorage();
        this.displayedLogs = FXCollections.observableArrayList();

        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #1a1a2e;");

        HBox headerBox = createHeader();
        HBox filterBox = createFilterBar();
        logListView = createLogListView();
        HBox controlBox = createControlBar();

        VBox.setVgrow(logListView, Priority.ALWAYS);

        getChildren().addAll(headerBox, filterBox, logListView, controlBox);

        storage.addListener(this::onNewLogEntry);
        refreshLogs();

        LOG.info("Log viewer panel initialized");
    }

    private HBox createHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("📋 System Logs");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setStyle("-fx-text-fill: #ffffff;");

        countLabel = new Label("0 entries");
        countLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(title, spacer, countLabel);
        return header;
    }

    private HBox createFilterBar() {
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(5));
        filterBar.setStyle("-fx-background-color: #16213e; -fx-background-radius: 5;");

        Label levelLabel = new Label("Levels:");
        levelLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        traceCheck = createLevelCheckbox("TRACE", "#808080");
        debugCheck = createLevelCheckbox("DEBUG", "#00d4ff");
        infoCheck = createLevelCheckbox("INFO", "#16c79a");
        warnCheck = createLevelCheckbox("WARN", "#f39c12");
        errorCheck = createLevelCheckbox("ERROR", "#e94560");

        debugCheck.setSelected(true);
        infoCheck.setSelected(true);
        warnCheck.setSelected(true);
        errorCheck.setSelected(true);

        Label sourceLabel = new Label("Source:");
        sourceLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        sourceFilter = new ComboBox<>();
        sourceFilter.getItems().add("All");
        sourceFilter.setValue("All");
        sourceFilter.setPrefWidth(120);
        sourceFilter.setStyle("-fx-background-color: #0f3460; -fx-font-size: 11;");
        sourceFilter.setOnAction(e -> refreshLogs());

        searchField = new TextField();
        searchField.setPromptText("🔍 Search logs...");
        searchField.setPrefWidth(150);
        searchField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 11;");
        searchField.textProperty().addListener((obs, old, newVal) -> refreshLogs());

        filterBar.getChildren().addAll(
            levelLabel, traceCheck, debugCheck, infoCheck, warnCheck, errorCheck,
            sourceLabel, sourceFilter, searchField
        );
        return filterBar;
    }

    private CheckBox createLevelCheckbox(String label, String color) {
        CheckBox cb = new CheckBox(label);
        cb.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10;");
        cb.setOnAction(e -> refreshLogs());
        return cb;
    }

    private ListView<LogEntry> createLogListView() {
        ListView<LogEntry> listView = new ListView<>(displayedLogs);
        listView.setCellFactory(lv -> new LogEntryCell());
        listView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        listView.setPrefHeight(300);

        Label placeholder = new Label("No logs to display");
        placeholder.setStyle("-fx-text-fill: #a0a0a0;");
        listView.setPlaceholder(placeholder);

        return listView;
    }

    private HBox createControlBar() {
        HBox controlBar = new HBox(10);
        controlBar.setAlignment(Pos.CENTER_LEFT);

        autoScrollCheck = new CheckBox("Auto-scroll");
        autoScrollCheck.setSelected(true);
        autoScrollCheck.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearButton = new Button("🗑️ Clear");
        clearButton.setStyle("-fx-background-color: #e94560; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-cursor: hand;");
        clearButton.setOnAction(e -> clearLogs());

        Button exportButton = new Button("💾 Export");
        exportButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-cursor: hand;");
        exportButton.setOnAction(e -> exportLogs());

        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-cursor: hand;");
        refreshButton.setOnAction(e -> {
            updateSourceFilter();
            refreshLogs();
        });

        controlBar.getChildren().addAll(autoScrollCheck, spacer, refreshButton, exportButton, clearButton);
        return controlBar;
    }

    private void onNewLogEntry(LogEntry entry) {
        Platform.runLater(() -> {
            if (matchesFilter(entry)) {
                displayedLogs.add(entry);
                updateCountLabel();
                if (autoScrollCheck.isSelected() && !displayedLogs.isEmpty()) {
                    logListView.scrollTo(displayedLogs.size() - 1);
                }
            }
            updateSourceFilter();
        });
    }

    private void refreshLogs() {
        List<LogEntry> all = storage.getAll();
        displayedLogs.clear();
        for (LogEntry entry : all) {
            if (matchesFilter(entry)) {
                displayedLogs.add(entry);
            }
        }
        updateCountLabel();
    }

    private boolean matchesFilter(LogEntry entry) {
        LogLevel level = entry.getLevel();
        boolean levelMatch = switch (level) {
            case TRACE -> traceCheck.isSelected();
            case DEBUG -> debugCheck.isSelected();
            case INFO -> infoCheck.isSelected();
            case WARN -> warnCheck.isSelected();
            case ERROR -> errorCheck.isSelected();
        };
        if (!levelMatch) return false;

        String selectedSource = sourceFilter.getValue();
        if (selectedSource != null && !selectedSource.equals("All")) {
            if (!entry.getSource().equals(selectedSource)) {
                return false;
            }
        }

        String searchText = searchField.getText();
        if (searchText != null && !searchText.isEmpty()) {
            String lower = searchText.toLowerCase();
            return entry.getMessage().toLowerCase().contains(lower) ||
                   entry.getSource().toLowerCase().contains(lower);
        }

        return true;
    }

    private void updateSourceFilter() {
        String current = sourceFilter.getValue();
        List<String> sources = storage.getUniqueSources();
        sourceFilter.getItems().clear();
        sourceFilter.getItems().add("All");
        sourceFilter.getItems().addAll(sources);
        if (current != null && sourceFilter.getItems().contains(current)) {
            sourceFilter.setValue(current);
        } else {
            sourceFilter.setValue("All");
        }
    }

    private void updateCountLabel() {
        int displayed = displayedLogs.size();
        int total = storage.size();
        countLabel.setText(displayed + " / " + total + " entries");
    }

    private void clearLogs() {
        storage.clear();
        displayedLogs.clear();
        updateCountLabel();
        LOG.info("Logs cleared by user");
    }

    private void exportLogs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Logs");
        chooser.setInitialFileName("anonet_logs.log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Log Files", "*.log"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                LogExporter exporter = new LogExporter();
                exporter.exportToFile(displayedLogs, file.toPath());
                LOG.info("Logs exported to: %s", file.getAbsolutePath());
            } catch (Exception e) {
                LOG.error("Failed to export logs", e);
            }
        }
    }

    private static class LogEntryCell extends ListCell<LogEntry> {
        @Override
        protected void updateItem(LogEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                setText(entry.format());
                String color = switch (entry.getLevel()) {
                    case TRACE -> "#808080";
                    case DEBUG -> "#00d4ff";
                    case INFO -> "#16c79a";
                    case WARN -> "#f39c12";
                    case ERROR -> "#e94560";
                };
                setStyle("-fx-text-fill: " + color + "; -fx-font-family: 'Monospaced'; -fx-font-size: 11; -fx-background-color: transparent;");
            }
        }
    }
}
