/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 13:00
 * File: AddContactDialog.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.ui;

import com.anonet.anonetclient.contacts.Contact;
import com.anonet.anonetclient.contacts.ContactManager;
import com.anonet.anonetclient.dht.DhtClient;
import com.anonet.anonetclient.dht.PeerAnnouncement;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AddContactDialog {

    private static final double DIALOG_WIDTH = 450;
    private static final double DIALOG_HEIGHT = 400;

    private final Stage dialogStage;
    private final ContactManager contactManager;
    private final DhtClient dhtClient;
    private final Consumer<Contact> onContactAdded;

    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private VBox resultBox;

    public AddContactDialog(Stage owner, ContactManager contactManager, DhtClient dhtClient, Consumer<Contact> onContactAdded) {
        this.contactManager = contactManager;
        this.dhtClient = dhtClient;
        this.onContactAdded = onContactAdded;

        dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Add Contact");
        dialogStage.setResizable(false);
    }

    public void show() {
        VBox layout = createLayout();
        dialogStage.setScene(new Scene(layout, DIALOG_WIDTH, DIALOG_HEIGHT));
        dialogStage.showAndWait();
    }

    private VBox createLayout() {
        VBox layout = new VBox(10);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Add New Contact");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        title.setStyle("-fx-text-fill: #ffffff;");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #16213e;");

        Tab searchTab = new Tab("Search by Username");
        searchTab.setContent(createSearchByUsernamePane());

        Tab pasteTab = new Tab("Paste ANONET ID");
        pasteTab.setContent(createPasteIdPane());

        tabPane.getTabs().addAll(searchTab, pasteTab);

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(30, 30);
        progressIndicator.setVisible(false);

        resultBox = new VBox(10);
        resultBox.setAlignment(Pos.CENTER);
        resultBox.setVisible(false);

        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-cursor: hand;");
        cancelButton.setOnAction(e -> dialogStage.close());

        layout.getChildren().addAll(title, tabPane, statusLabel, progressIndicator, resultBox, cancelButton);
        return layout;
    }

    private VBox createSearchByUsernamePane() {
        VBox pane = new VBox(15);
        pane.setAlignment(Pos.CENTER);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #16213e;");

        Label instruction = new Label("Enter the username to search in DHT network:");
        instruction.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("alice#A1B2C3D4");
        usernameField.setMaxWidth(300);
        usernameField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 13; -fx-padding: 10;");

        Button searchButton = new Button("🔍 Search DHT");
        searchButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        searchButton.setOnAction(e -> searchForUser(usernameField.getText().trim()));

        pane.getChildren().addAll(instruction, usernameField, searchButton);
        return pane;
    }

    private VBox createPasteIdPane() {
        VBox pane = new VBox(15);
        pane.setAlignment(Pos.CENTER);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #16213e;");

        Label instruction = new Label("Paste the ANONET ID or contact link:");
        instruction.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        TextArea idArea = new TextArea();
        idArea.setPromptText("anonet:alice#A1B2C3D4\nor\nanonet://add/alice#A1B2C3D4");
        idArea.setPrefRowCount(3);
        idArea.setMaxWidth(350);
        idArea.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 12;");

        Button addButton = new Button("➕ Add Contact");
        addButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        addButton.setOnAction(e -> parseAndAddContact(idArea.getText().trim()));

        pane.getChildren().addAll(instruction, idArea, addButton);
        return pane;
    }

    private void searchForUser(String username) {
        if (username.isEmpty()) {
            showStatus("Please enter a username", true);
            return;
        }

        if (dhtClient == null || !dhtClient.isRunning()) {
            showStatus("DHT network not available", true);
            return;
        }

        showProgress(true);
        showStatus("Searching DHT network...", false);

        CompletableFuture<Optional<PeerAnnouncement>> future = dhtClient.lookup(username);
        future.thenAccept(result -> Platform.runLater(() -> {
            showProgress(false);
            if (result.isPresent()) {
                showFoundContact(result.get());
            } else {
                showStatus("User not found in DHT network", true);
            }
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                showProgress(false);
                showStatus("Search failed: " + e.getMessage(), true);
            });
            return null;
        });
    }

    private void showFoundContact(PeerAnnouncement announcement) {
        resultBox.getChildren().clear();

        Label foundLabel = new Label("✓ Contact Found!");
        foundLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        foundLabel.setStyle("-fx-text-fill: #16c79a;");

        Label usernameLabel = new Label("Username: " + announcement.getUsername());
        usernameLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12;");

        Label fingerprintLabel = new Label("Fingerprint: " + announcement.getFingerprint().substring(0, 16) + "...");
        fingerprintLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");

        Button addButton = new Button("✓ Add to Contacts");
        addButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        addButton.setOnAction(e -> addContactFromAnnouncement(announcement));

        resultBox.getChildren().addAll(foundLabel, usernameLabel, fingerprintLabel, addButton);
        resultBox.setVisible(true);
    }

    private void addContactFromAnnouncement(PeerAnnouncement announcement) {
        try {
            String username = announcement.getUsername();
            String displayName = username.contains("#") ? username.substring(0, username.indexOf('#')) : username;

            Contact contact = contactManager.addContact(
                displayName,
                username,
                announcement.getFingerprint(),
                announcement.getPublicKey()
            );

            showStatus("✓ Contact added successfully!", false);
            statusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 11;");

            if (onContactAdded != null) {
                onContactAdded.accept(contact);
            }

            CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> Platform.runLater(dialogStage::close));

        } catch (Exception e) {
            showStatus("Failed to add contact: " + e.getMessage(), true);
        }
    }

    private void parseAndAddContact(String input) {
        if (input.isEmpty()) {
            showStatus("Please paste an ANONET ID or link", true);
            return;
        }

        String username = null;
        if (input.startsWith("anonet:")) {
            username = input.substring(7);
        } else if (input.startsWith("anonet://add/")) {
            username = input.substring(13);
        } else if (input.contains("#")) {
            username = input;
        }

        if (username == null || username.isEmpty()) {
            showStatus("Invalid format. Expected: anonet:username#XXXXXXXX", true);
            return;
        }

        searchForUser(username);
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e94560; -fx-font-size: 11;" : "-fx-text-fill: #a0a0a0; -fx-font-size: 11;");
    }

    private void showProgress(boolean show) {
        progressIndicator.setVisible(show);
        resultBox.setVisible(!show && resultBox.getChildren().size() > 0);
    }
}
