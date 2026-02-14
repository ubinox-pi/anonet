/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 13:00
 * File: StatusBar.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;

public final class StatusBar extends HBox {

    private final Label dhtStatusLabel;
    private final Label lanStatusLabel;
    private final Label relayStatusLabel;
    private final Label onionStatusLabel;
    private final Label connectionStatusLabel;
    private final Label versionLabel;

    public StatusBar() {
        setSpacing(20);
        setPadding(new Insets(8, 15, 8, 15));
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: #0f3460;");

        dhtStatusLabel = createStatusLabel("DHT: Offline");
        lanStatusLabel = createStatusLabel("LAN: Scanning...");
        relayStatusLabel = createStatusLabel("Relay: Off");
        onionStatusLabel = createStatusLabel("Onion: Off");
        connectionStatusLabel = createStatusLabel("Connections: 0");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        versionLabel = createStatusLabel("ANONET v1.0.0");
        versionLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 10;");

        getChildren().addAll(dhtStatusLabel, lanStatusLabel, relayStatusLabel, onionStatusLabel, connectionStatusLabel, spacer, versionLabel);
    }

    private Label createStatusLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", 10));
        label.setStyle("-fx-text-fill: #a0a0a0;");
        return label;
    }

    public void setDhtStatus(boolean online, int peerCount) {
        Platform.runLater(() -> {
            if (online) {
                dhtStatusLabel.setText("🌐 DHT: Online (" + peerCount + " peers)");
                dhtStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
            } else {
                dhtStatusLabel.setText("⚫ DHT: Offline");
                dhtStatusLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 10;");
            }
        });
    }

    public void setLanStatus(int peerCount) {
        Platform.runLater(() -> {
            if (peerCount > 0) {
                lanStatusLabel.setText("📡 LAN: " + peerCount + " peer(s)");
                lanStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
            } else {
                lanStatusLabel.setText("📡 LAN: No peers");
                lanStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");
            }
        });
    }

    public void setConnectionStatus(int activeConnections) {
        Platform.runLater(() -> {
            connectionStatusLabel.setText("🔗 Connections: " + activeConnections);
            if (activeConnections > 0) {
                connectionStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
            } else {
                connectionStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");
            }
        });
    }

    public void setVersion(String version) {
        Platform.runLater(() -> versionLabel.setText("ANONET " + version));
    }

    public void setRelayStatus(boolean running, int connectedClients) {
        Platform.runLater(() -> {
            if (running) {
                relayStatusLabel.setText("Relay: Active (" + connectedClients + " clients)");
                relayStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
            } else {
                relayStatusLabel.setText("Relay: Off");
                relayStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");
            }
        });
    }

    public void setOnionStatus(boolean running, int activeCircuits) {
        Platform.runLater(() -> {
            if (running) {
                onionStatusLabel.setText("Onion: Active (" + activeCircuits + " circuits)");
                onionStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
            } else {
                onionStatusLabel.setText("Onion: Off");
                onionStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");
            }
        });
    }
}
