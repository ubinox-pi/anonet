/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 13:00
 * File: ContactListPanel.java
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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.function.Consumer;

public final class ContactListPanel extends VBox {

    private final ContactManager contactManager;
    private final ObservableList<Contact> contactList;
    private final ListView<Contact> listView;
    private final TextField searchField;
    private Consumer<Contact> onContactSelected;
    private Consumer<Contact> onSendFile;

    public ContactListPanel(ContactManager contactManager) {
        this.contactManager = contactManager;
        this.contactList = FXCollections.observableArrayList();

        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #16213e; -fx-background-radius: 5;");

        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("👥 Contacts");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: #ffffff;");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button refreshButton = new Button("🔄");
        refreshButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #16c79a; -fx-cursor: hand;");
        refreshButton.setOnAction(e -> refreshContacts());

        headerBox.getChildren().addAll(title, refreshButton);

        searchField = new TextField();
        searchField.setPromptText("🔍 Search contacts...");
        searchField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #666666;");
        searchField.textProperty().addListener((obs, old, newVal) -> filterContacts(newVal));

        listView = new ListView<>(contactList);
        listView.setCellFactory(lv -> new ContactListCell());
        listView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        listView.setPrefHeight(250);
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && onContactSelected != null) {
                onContactSelected.accept(selected);
            }
        });

        Label emptyLabel = new Label("No contacts yet.\nAdd contacts to start sharing!");
        emptyLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-text-alignment: center;");
        listView.setPlaceholder(emptyLabel);

        getChildren().addAll(headerBox, searchField, listView);

        setupContactManagerListeners();
        refreshContacts();
    }

    private void setupContactManagerListeners() {
        contactManager.onContactAdded(contact -> Platform.runLater(this::refreshContacts));
        contactManager.onContactRemoved(contact -> Platform.runLater(this::refreshContacts));
        contactManager.onContactUpdated(contact -> Platform.runLater(this::refreshContacts));
    }

    public void refreshContacts() {
        List<Contact> sorted = contactManager.getSortedContacts();
        Platform.runLater(() -> contactList.setAll(sorted));
    }

    private void filterContacts(String query) {
        if (query == null || query.isBlank()) {
            refreshContacts();
        } else {
            List<Contact> filtered = contactManager.search(query);
            Platform.runLater(() -> contactList.setAll(filtered));
        }
    }

    public void setOnContactSelected(Consumer<Contact> handler) {
        this.onContactSelected = handler;
    }

    public void setOnSendFile(Consumer<Contact> handler) {
        this.onSendFile = handler;
    }

    public Contact getSelectedContact() {
        return listView.getSelectionModel().getSelectedItem();
    }

    private class ContactListCell extends ListCell<Contact> {
        @Override
        protected void updateItem(Contact contact, boolean empty) {
            super.updateItem(contact, empty);
            if (empty || contact == null) {
                setGraphic(null);
                setText(null);
                setContextMenu(null);
            } else {
                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPadding(new Insets(8));

                String statusIcon = contact.isOnline() ? "🟢" : "⚫";
                String favoriteIcon = contact.isFavorite() ? "⭐ " : "";

                Label statusLabel = new Label(statusIcon);
                statusLabel.setStyle("-fx-font-size: 10;");

                VBox infoBox = new VBox(2);
                Label nameLabel = new Label(favoriteIcon + contact.getDisplayName());
                nameLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 12;");

                Label usernameLabel = new Label(contact.getUsername());
                usernameLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");

                infoBox.getChildren().addAll(nameLabel, usernameLabel);
                HBox.setHgrow(infoBox, Priority.ALWAYS);

                Button sendButton = new Button("📤");
                sendButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand; -fx-padding: 3 8;");
                sendButton.setOnAction(e -> {
                    if (onSendFile != null) {
                        onSendFile.accept(contact);
                    }
                });

                cell.getChildren().addAll(statusLabel, infoBox, sendButton);
                setGraphic(cell);
                setStyle("-fx-background-color: transparent;");

                ContextMenu contextMenu = createContextMenu(contact);
                setContextMenu(contextMenu);
            }
        }

        private ContextMenu createContextMenu(Contact contact) {
            ContextMenu menu = new ContextMenu();

            MenuItem sendItem = new MenuItem("📤 Send File");
            sendItem.setOnAction(e -> {
                if (onSendFile != null) onSendFile.accept(contact);
            });

            MenuItem favoriteItem = new MenuItem(contact.isFavorite() ? "⭐ Remove from Favorites" : "⭐ Add to Favorites");
            favoriteItem.setOnAction(e -> toggleFavorite(contact));

            MenuItem copyItem = new MenuItem("📋 Copy Username");
            copyItem.setOnAction(e -> copyToClipboard(contact.getUsername()));

            MenuItem deleteItem = new MenuItem("🗑️ Delete Contact");
            deleteItem.setOnAction(e -> deleteContact(contact));

            menu.getItems().addAll(sendItem, favoriteItem, copyItem, deleteItem);
            return menu;
        }
    }

    private void toggleFavorite(Contact contact) {
        try {
            contactManager.setFavorite(contact, !contact.isFavorite());
            refreshContacts();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void deleteContact(Contact contact) {
        try {
            contactManager.removeContact(contact);
            refreshContacts();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }
}
