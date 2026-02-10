/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 13:00
 * File: SettingsDialog.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public final class SettingsDialog {

    private static final double DIALOG_WIDTH = 500;
    private static final double DIALOG_HEIGHT = 550;

    private final Stage dialogStage;
    private final Settings currentSettings;
    private final Consumer<Settings> onSave;

    private CheckBox enableRelayCheckbox;
    private CheckBox enableOnionCheckbox;
    private CheckBox autoStartDhtCheckbox;
    private CheckBox showNotificationsCheckbox;
    private ComboBox<String> themeComboBox;
    private Spinner<Integer> maxConnectionsSpinner;
    private Label downloadPathLabel;
    private Path downloadPath;

    public SettingsDialog(Stage owner, Settings currentSettings, Consumer<Settings> onSave) {
        this.currentSettings = currentSettings;
        this.onSave = onSave;
        this.downloadPath = currentSettings.getDownloadPath();

        dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Settings");
        dialogStage.setResizable(false);
    }

    public void show() {
        VBox layout = createLayout();
        dialogStage.setScene(new Scene(layout, DIALOG_WIDTH, DIALOG_HEIGHT));
        dialogStage.showAndWait();
    }

    private VBox createLayout() {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_LEFT);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("⚙️ Settings");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #ffffff;");

        VBox networkSection = createNetworkSection();
        VBox privacySection = createPrivacySection();
        VBox generalSection = createGeneralSection();
        HBox buttonBox = createButtonBox();

        layout.getChildren().addAll(title, networkSection, new Separator(), privacySection, new Separator(), generalSection, buttonBox);
        return layout;
    }

    private VBox createNetworkSection() {
        VBox section = new VBox(10);

        Label sectionTitle = new Label("Network");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        sectionTitle.setStyle("-fx-text-fill: #16c79a;");

        autoStartDhtCheckbox = new CheckBox("Auto-start DHT on launch");
        autoStartDhtCheckbox.setSelected(currentSettings.isAutoStartDht());
        autoStartDhtCheckbox.setStyle("-fx-text-fill: #ffffff;");

        enableRelayCheckbox = new CheckBox("Act as relay node for others");
        enableRelayCheckbox.setSelected(currentSettings.isEnableRelay());
        enableRelayCheckbox.setStyle("-fx-text-fill: #ffffff;");

        HBox maxConnectionsBox = new HBox(10);
        maxConnectionsBox.setAlignment(Pos.CENTER_LEFT);
        Label maxConnectionsLabel = new Label("Max simultaneous connections:");
        maxConnectionsLabel.setStyle("-fx-text-fill: #a0a0a0;");
        maxConnectionsSpinner = new Spinner<>();
        maxConnectionsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, currentSettings.getMaxConnections()));
        maxConnectionsSpinner.setPrefWidth(80);
        maxConnectionsSpinner.setStyle("-fx-background-color: #0f3460;");
        maxConnectionsBox.getChildren().addAll(maxConnectionsLabel, maxConnectionsSpinner);

        section.getChildren().addAll(sectionTitle, autoStartDhtCheckbox, enableRelayCheckbox, maxConnectionsBox);
        return section;
    }

    private VBox createPrivacySection() {
        VBox section = new VBox(10);

        Label sectionTitle = new Label("Privacy & Anonymity");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        sectionTitle.setStyle("-fx-text-fill: #16c79a;");

        enableOnionCheckbox = new CheckBox("Enable onion routing (3-hop anonymity)");
        enableOnionCheckbox.setSelected(currentSettings.isEnableOnion());
        enableOnionCheckbox.setStyle("-fx-text-fill: #ffffff;");

        Label onionNote = new Label("⚠️ Onion routing increases latency but hides your IP from recipients");
        onionNote.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");
        onionNote.setWrapText(true);

        section.getChildren().addAll(sectionTitle, enableOnionCheckbox, onionNote);
        return section;
    }

    private VBox createGeneralSection() {
        VBox section = new VBox(10);

        Label sectionTitle = new Label("General");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        sectionTitle.setStyle("-fx-text-fill: #16c79a;");

        showNotificationsCheckbox = new CheckBox("Show desktop notifications");
        showNotificationsCheckbox.setSelected(currentSettings.isShowNotifications());
        showNotificationsCheckbox.setStyle("-fx-text-fill: #ffffff;");

        HBox themeBox = new HBox(10);
        themeBox.setAlignment(Pos.CENTER_LEFT);
        Label themeLabel = new Label("Theme:");
        themeLabel.setStyle("-fx-text-fill: #a0a0a0;");
        themeComboBox = new ComboBox<>();
        themeComboBox.getItems().addAll("Dark (Default)", "Light", "System");
        themeComboBox.setValue(currentSettings.getTheme());
        themeComboBox.setStyle("-fx-background-color: #0f3460;");
        themeBox.getChildren().addAll(themeLabel, themeComboBox);

        HBox downloadBox = new HBox(10);
        downloadBox.setAlignment(Pos.CENTER_LEFT);
        Label downloadLabel = new Label("Download folder:");
        downloadLabel.setStyle("-fx-text-fill: #a0a0a0;");
        downloadPathLabel = new Label(shortenPath(downloadPath.toString()));
        downloadPathLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 11;");
        Button browseButton = new Button("Browse...");
        browseButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-cursor: hand;");
        browseButton.setOnAction(e -> browseDownloadFolder());
        downloadBox.getChildren().addAll(downloadLabel, downloadPathLabel, browseButton);

        section.getChildren().addAll(sectionTitle, showNotificationsCheckbox, themeBox, downloadBox);
        return section;
    }

    private void browseDownloadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Download Folder");
        chooser.setInitialDirectory(downloadPath.toFile());
        File selected = chooser.showDialog(dialogStage);
        if (selected != null) {
            downloadPath = selected.toPath();
            downloadPathLabel.setText(shortenPath(downloadPath.toString()));
        }
    }

    private String shortenPath(String path) {
        if (path.length() > 40) {
            return "..." + path.substring(path.length() - 37);
        }
        return path;
    }

    private HBox createButtonBox() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(20, 0, 0, 0));

        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-padding: 10 25; -fx-cursor: hand;");
        cancelButton.setOnAction(e -> dialogStage.close());

        Button saveButton = new Button("Save Settings");
        saveButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-padding: 10 25; -fx-cursor: hand;");
        saveButton.setOnAction(e -> saveSettings());

        box.getChildren().addAll(cancelButton, saveButton);
        return box;
    }

    private void saveSettings() {
        Settings newSettings = new Settings(
            autoStartDhtCheckbox.isSelected(),
            enableRelayCheckbox.isSelected(),
            enableOnionCheckbox.isSelected(),
            showNotificationsCheckbox.isSelected(),
            maxConnectionsSpinner.getValue(),
            themeComboBox.getValue(),
            downloadPath
        );

        if (onSave != null) {
            onSave.accept(newSettings);
        }
        dialogStage.close();
    }

    public static final class Settings {
        private final boolean autoStartDht;
        private final boolean enableRelay;
        private final boolean enableOnion;
        private final boolean showNotifications;
        private final int maxConnections;
        private final String theme;
        private final Path downloadPath;

        public Settings(boolean autoStartDht, boolean enableRelay, boolean enableOnion,
                       boolean showNotifications, int maxConnections, String theme, Path downloadPath) {
            this.autoStartDht = autoStartDht;
            this.enableRelay = enableRelay;
            this.enableOnion = enableOnion;
            this.showNotifications = showNotifications;
            this.maxConnections = maxConnections;
            this.theme = theme;
            this.downloadPath = downloadPath;
        }

        public static Settings defaults() {
            Path defaultDownload = Paths.get(System.getProperty("user.home"), ".anonet", "downloads");
            return new Settings(true, false, false, true, 5, "Dark (Default)", defaultDownload);
        }

        public boolean isAutoStartDht() { return autoStartDht; }
        public boolean isEnableRelay() { return enableRelay; }
        public boolean isEnableOnion() { return enableOnion; }
        public boolean isShowNotifications() { return showNotifications; }
        public int getMaxConnections() { return maxConnections; }
        public String getTheme() { return theme; }
        public Path getDownloadPath() { return downloadPath; }
    }
}
