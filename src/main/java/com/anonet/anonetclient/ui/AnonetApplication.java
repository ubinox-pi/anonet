/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 19-01-2026 14:30
 * File: AnonetApplication.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.ui;

import com.anonet.anonetclient.crypto.session.SessionCryptoVerification;
import com.anonet.anonetclient.discovery.DiscoveryClient;
import com.anonet.anonetclient.discovery.PeerInfo;
import com.anonet.anonetclient.identity.IdentityManager;
import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.identity.SeedPhrase;
import com.anonet.anonetclient.lan.LanDiscoveryService;
import com.anonet.anonetclient.lan.LanPeer;
import com.anonet.anonetclient.publicnet.ConnectionState;
import com.anonet.anonetclient.publicnet.NatTraversalService;
import com.anonet.anonetclient.publicnet.PublicPeerEndpoint;
import com.anonet.anonetclient.transfer.FileTransferService;
import com.anonet.anonetclient.transfer.TransferProgress;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AnonetApplication extends Application {

    private static final String APP_TITLE = "ANONET";
    private static final double WINDOW_WIDTH = 700;
    private static final double WINDOW_HEIGHT = 650;

    private final IdentityManager identityManager;
    private LocalIdentity localIdentity;
    private LanDiscoveryService lanDiscoveryService;
    private FileTransferService fileTransferService;
    private DiscoveryClient discoveryClient;
    private NatTraversalService natTraversalService;
    private ObservableList<LanPeer> lanPeerList;
    private ObservableList<PeerInfo> publicPeerList;
    private ListView<LanPeer> lanPeerListView;
    private ListView<PeerInfo> publicPeerListView;
    private Label lanPeerCountLabel;
    private Label publicPeerCountLabel;
    private Label cryptoStatusLabel;
    private Label transferStatusLabel;
    private Label publicStatusLabel;
    private Label p2pStatusLabel;
    private ProgressBar transferProgressBar;
    private Button sendFileButton;
    private Button connectButton;
    private Button goOnlineButton;
    private TextField usernameField;
    private PasswordField passwordField;
    private Stage primaryStage;
    private TabPane peerTabPane;
    private final Map<String, ConnectionState> peerConnectionStates;

    public AnonetApplication() {
        this.identityManager = new IdentityManager();
        this.peerConnectionStates = new HashMap<>();
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        localIdentity = identityManager.loadOrCreateIdentity();

        lanPeerList = FXCollections.observableArrayList();
        publicPeerList = FXCollections.observableArrayList();

        lanDiscoveryService = new LanDiscoveryService(localIdentity);
        lanDiscoveryService.setOnPeersChangedCallback(this::updateLanPeerList);

        initializeDiscoveryClient();
        initializeNatTraversalService();

        Path downloadDir = Path.of(System.getProperty("user.home"), ".anonet", "downloads");
        fileTransferService = new FileTransferService(localIdentity, downloadDir);

        VBox rootLayout = createRootLayout(localIdentity);
        Scene scene = new Scene(rootLayout, WINDOW_WIDTH, WINDOW_HEIGHT);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(550);
        primaryStage.setMinHeight(550);
        primaryStage.setOnCloseRequest(event -> shutdown());
        primaryStage.show();

        lanDiscoveryService.start();
        startFileReceiver();
        runCryptoVerification();
    }

    private void initializeDiscoveryClient() {
        String serverUrl = System.getProperty("anonet.discovery.server", "http://localhost:8080");
        discoveryClient = new DiscoveryClient(serverUrl, localIdentity);
    }

    private void initializeNatTraversalService() {
        natTraversalService = new NatTraversalService(localIdentity);
    }

    private void startFileReceiver() {
        fileTransferService.startReceiver(
                this::onReceiveProgress,
                this::onFileReceived
        );
        Platform.runLater(() -> {
            transferStatusLabel.setText("📥 Receiver: Listening on port 51821");
            transferStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
        });
    }

    private void onReceiveProgress(TransferProgress progress) {
        Platform.runLater(() -> {
            transferProgressBar.setProgress(progress.getPercentage() / 100.0);
            String stateText = switch (progress.getState()) {
                case CONNECTING -> "Incoming connection...";
                case HANDSHAKING -> "Establishing secure channel...";
                case TRANSFERRING -> String.format("Receiving: %.1f%%", progress.getPercentage());
                case COMPLETING -> "Verifying...";
                case COMPLETED -> "✓ Received!";
                case FAILED -> "✗ Failed";
                case CANCELLED -> "Cancelled";
            };
            transferStatusLabel.setText("📥 " + stateText);
        });
    }

    private void onFileReceived(File file) {
        Platform.runLater(() -> {
            transferStatusLabel.setText("📥 Received: " + file.getName());
            transferProgressBar.setProgress(0);
        });
    }

    private void runCryptoVerification() {
        CompletableFuture.runAsync(() -> {
            SessionCryptoVerification.VerificationResult result = SessionCryptoVerification.runAllTests();
            Platform.runLater(() -> {
                if (result.isAllPassed()) {
                    cryptoStatusLabel.setText("🔐 Crypto: " + result.getPassed() + "/" + result.getPassed() + " tests passed");
                    cryptoStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
                } else {
                    cryptoStatusLabel.setText("⚠ Crypto: " + result.getFailed() + " test(s) failed");
                    cryptoStatusLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 10;");
                }
            });
        });
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (natTraversalService != null) {
            natTraversalService.shutdown();
        }
        if (discoveryClient != null) {
            discoveryClient.shutdown();
        }
        if (fileTransferService != null) {
            fileTransferService.shutdown();
        }
        if (lanDiscoveryService != null) {
            lanDiscoveryService.stop();
        }
    }

    private void updateLanPeerList() {
        Platform.runLater(() -> {
            List<LanPeer> peers = lanDiscoveryService.getDiscoveredPeers();
            lanPeerList.setAll(peers);
            updateLanPeerCountLabel();
        });
    }

    private void updateLanPeerCountLabel() {
        int count = lanPeerList.size();
        String text = count == 0 ? "No peers discovered" : count + " peer(s) on LAN";
        lanPeerCountLabel.setText(text);
    }

    private void updatePublicStatus() {
        Platform.runLater(() -> {
            if (discoveryClient != null && discoveryClient.isOnline()) {
                String username = discoveryClient.getCurrentUsername();
                publicStatusLabel.setText("🌐 Online as: " + username);
                publicStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 11;");
                goOnlineButton.setText("Go Offline");
                goOnlineButton.setStyle("-fx-background-color: #e94560; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
                usernameField.setDisable(true);
                passwordField.setDisable(true);
            } else {
                publicStatusLabel.setText("🌐 Offline");
                publicStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");
                goOnlineButton.setText("Go Online");
                goOnlineButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
                usernameField.setDisable(false);
                passwordField.setDisable(false);
                publicPeerList.clear();
                updatePublicPeerCountLabel();
            }
        });
    }

    private void updatePublicPeerCountLabel() {
        int count = publicPeerList.size();
        String text = count == 0 ? "No public peers online" : count + " peer(s) online";
        publicPeerCountLabel.setText(text);
    }

    private VBox createRootLayout(LocalIdentity identity) {
        VBox layout = new VBox(12);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #1a1a2e;");

        Label titleLabel = createTitleLabel();
        Label subtitleLabel = createSubtitleLabel();
        VBox identitySection = createIdentitySection(identity);
        VBox publicDiscoverySection = createPublicDiscoverySection();
        VBox peerSection = createPeerSection();
        VBox transferSection = createTransferSection();

        VBox.setVgrow(peerSection, Priority.ALWAYS);

        layout.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                identitySection,
                publicDiscoverySection,
                peerSection,
                transferSection
        );

        return layout;
    }

    private Label createTitleLabel() {
        Label label = new Label(APP_TITLE);
        label.setFont(Font.font("System", FontWeight.BOLD, 32));
        label.setStyle("-fx-text-fill: #e94560;");
        return label;
    }

    private Label createSubtitleLabel() {
        Label label = new Label("Anonymous Peer-to-Peer Encrypted File Transfer");
        label.setFont(Font.font("System", FontWeight.NORMAL, 12));
        label.setStyle("-fx-text-fill: #a0a0a0;");
        return label;
    }

    private VBox createIdentitySection(LocalIdentity identity) {
        VBox section = new VBox(5);
        section.setAlignment(Pos.CENTER);
        section.setPadding(new Insets(15, 0, 15, 0));

        Label fingerprintTitleLabel = new Label("Your Identity:");
        fingerprintTitleLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        fingerprintTitleLabel.setStyle("-fx-text-fill: #16c79a;");

        Label fingerprintValueLabel = new Label(identity.getFormattedFingerprint());
        fingerprintValueLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 12));
        fingerprintValueLabel.setStyle("-fx-text-fill: #ffffff; -fx-background-color: #0f3460; -fx-padding: 8; -fx-background-radius: 5;");

        cryptoStatusLabel = new Label("🔐 Crypto: Verifying...");
        cryptoStatusLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        cryptoStatusLabel.setStyle("-fx-text-fill: #a0a0a0;");

        HBox backupButtons = new HBox(10);
        backupButtons.setAlignment(Pos.CENTER);

        Button backupButton = new Button("💾 Backup Identity");
        backupButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        backupButton.setOnAction(e -> onBackupIdentity());

        Button restoreButton = new Button("📥 Restore Identity");
        restoreButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        restoreButton.setOnAction(e -> onRestoreIdentity());

        backupButtons.getChildren().addAll(backupButton, restoreButton);

        section.getChildren().addAll(fingerprintTitleLabel, fingerprintValueLabel, cryptoStatusLabel, backupButtons);
        return section;
    }

    private VBox createPublicDiscoverySection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.CENTER_LEFT);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 5;");

        Label sectionTitle = new Label("🌐 Public Discovery");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        sectionTitle.setStyle("-fx-text-fill: #ffffff;");

        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);

        Label usernameLabel = new Label("Username:");
        usernameLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");
        usernameField = new TextField();
        usernameField.setPromptText("anonymous123");
        usernameField.setPrefWidth(120);
        usernameField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #666666;");

        Label passwordLabel = new Label("Password:");
        passwordLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");
        passwordField = new PasswordField();
        passwordField.setPromptText("••••••");
        passwordField.setPrefWidth(120);
        passwordField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #666666;");

        goOnlineButton = new Button("Go Online");
        goOnlineButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        goOnlineButton.setOnAction(e -> onGoOnlineClicked());

        inputBox.getChildren().addAll(usernameLabel, usernameField, passwordLabel, passwordField, goOnlineButton);

        publicStatusLabel = new Label("🌐 Offline");
        publicStatusLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        publicStatusLabel.setStyle("-fx-text-fill: #a0a0a0;");

        section.getChildren().addAll(sectionTitle, inputBox, publicStatusLabel);
        return section;
    }

    private void onGoOnlineClicked() {
        if (discoveryClient == null) {
            return;
        }

        if (discoveryClient.isOnline()) {
            goOnlineButton.setDisable(true);
            publicStatusLabel.setText("🌐 Going offline...");
            discoveryClient.logout().thenRun(() -> {
                Platform.runLater(() -> {
                    goOnlineButton.setDisable(false);
                    updatePublicStatus();
                });
            }).exceptionally(e -> {
                Platform.runLater(() -> {
                    publicStatusLabel.setText("⚠ Failed to go offline");
                    goOnlineButton.setDisable(false);
                });
                return null;
            });
        } else {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            if (username.isEmpty() || password.isEmpty()) {
                publicStatusLabel.setText("⚠ Enter username and password");
                publicStatusLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 10;");
                return;
            }

            goOnlineButton.setDisable(true);
            publicStatusLabel.setText("🌐 Connecting...");
            publicStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");

            discoveryClient.register(username, password).thenRun(() -> {
                Platform.runLater(() -> {
                    goOnlineButton.setDisable(false);
                    updatePublicStatus();
                });
            }).exceptionally(e -> {
                Platform.runLater(() -> {
                    String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    publicStatusLabel.setText("⚠ " + message);
                    publicStatusLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 10;");
                    goOnlineButton.setDisable(false);
                });
                return null;
            });
        }
    }

    private VBox createPeerSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        section.setPadding(new Insets(5, 0, 0, 0));

        peerTabPane = new TabPane();
        peerTabPane.setStyle("-fx-background-color: #16213e;");
        peerTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab lanTab = createLanPeerTab();
        Tab publicTab = createPublicPeerTab();

        peerTabPane.getTabs().addAll(lanTab, publicTab);
        VBox.setVgrow(peerTabPane, Priority.ALWAYS);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));

        p2pStatusLabel = new Label("");
        p2pStatusLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        p2pStatusLabel.setStyle("-fx-text-fill: #a0a0a0;");
        HBox.setHgrow(p2pStatusLabel, Priority.ALWAYS);

        connectButton = new Button("🔗 Connect");
        connectButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        connectButton.setDisable(true);
        connectButton.setOnAction(e -> onConnectClicked());

        sendFileButton = new Button("📤 Send File");
        sendFileButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        sendFileButton.setDisable(true);
        sendFileButton.setOnAction(e -> onSendFileClicked());

        buttonBox.getChildren().addAll(p2pStatusLabel, connectButton, sendFileButton);

        section.getChildren().addAll(peerTabPane, buttonBox);
        return section;
    }

    private Tab createLanPeerTab() {
        Tab tab = new Tab("📡 LAN Peers");

        VBox content = new VBox(5);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: #16213e;");

        lanPeerCountLabel = new Label("Scanning...");
        lanPeerCountLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        lanPeerCountLabel.setStyle("-fx-text-fill: #a0a0a0;");

        lanPeerListView = new ListView<>(lanPeerList);
        lanPeerListView.setStyle("-fx-background-color: #16213e; -fx-control-inner-background: #16213e;");
        lanPeerListView.setCellFactory(listView -> new LanPeerListCell());
        lanPeerListView.setPlaceholder(createEmptyPlaceholder("Waiting for LAN peers...\nMake sure other ANONET instances are running on the same network."));
        lanPeerListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        lanPeerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateSendButtonState());
        VBox.setVgrow(lanPeerListView, Priority.ALWAYS);

        content.getChildren().addAll(lanPeerCountLabel, lanPeerListView);
        tab.setContent(content);
        return tab;
    }

    private Tab createPublicPeerTab() {
        Tab tab = new Tab("🌐 Public Peers");

        VBox content = new VBox(5);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: #16213e;");

        publicPeerCountLabel = new Label("Go online to discover peers");
        publicPeerCountLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        publicPeerCountLabel.setStyle("-fx-text-fill: #a0a0a0;");

        publicPeerListView = new ListView<>(publicPeerList);
        publicPeerListView.setStyle("-fx-background-color: #16213e; -fx-control-inner-background: #16213e;");
        publicPeerListView.setCellFactory(listView -> new PublicPeerListCell());
        publicPeerListView.setPlaceholder(createEmptyPlaceholder("No public peers online.\nGo online to discover peers worldwide."));
        publicPeerListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        publicPeerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateSendButtonState());
        VBox.setVgrow(publicPeerListView, Priority.ALWAYS);

        content.getChildren().addAll(publicPeerCountLabel, publicPeerListView);
        tab.setContent(content);
        return tab;
    }

    private void updateSendButtonState() {
        Tab selectedTab = peerTabPane.getSelectionModel().getSelectedItem();
        boolean isLanTab = selectedTab != null && selectedTab.getText().contains("LAN");
        boolean isPublicTab = selectedTab != null && selectedTab.getText().contains("Public");

        if (isLanTab) {
            boolean hasSelection = lanPeerListView.getSelectionModel().getSelectedItem() != null;
            sendFileButton.setDisable(!hasSelection);
            connectButton.setDisable(true);
            connectButton.setVisible(false);
        } else if (isPublicTab) {
            PeerInfo selectedPeer = publicPeerListView.getSelectionModel().getSelectedItem();
            boolean hasSelection = selectedPeer != null;

            sendFileButton.setDisable(true);
            connectButton.setVisible(true);

            if (hasSelection) {
                ConnectionState state = peerConnectionStates.get(selectedPeer.publicKeyFingerprint());
                if (state == ConnectionState.CONNECTED) {
                    connectButton.setDisable(true);
                    connectButton.setText("✓ Connected");
                    connectButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
                } else if (state == ConnectionState.ATTEMPTING_P2P || state == ConnectionState.HOLE_PUNCHING || state == ConnectionState.AUTHENTICATING) {
                    connectButton.setDisable(true);
                    connectButton.setText("⏳ Connecting...");
                    connectButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
                } else {
                    connectButton.setDisable(false);
                    connectButton.setText("🔗 Connect");
                    connectButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
                }
            } else {
                connectButton.setDisable(true);
                connectButton.setText("🔗 Connect");
                connectButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
            }
        } else {
            sendFileButton.setDisable(true);
            connectButton.setDisable(true);
        }
    }

    private void onConnectClicked() {
        PeerInfo selectedPeer = publicPeerListView.getSelectionModel().getSelectedItem();
        if (selectedPeer == null || natTraversalService == null) {
            return;
        }

        PublicPeerEndpoint endpoint = new PublicPeerEndpoint(
                selectedPeer.publicKeyFingerprint(),
                selectedPeer.ipAddress(),
                selectedPeer.portCandidates(),
                selectedPeer.publicKeyEncoded()
        );

        peerConnectionStates.put(selectedPeer.publicKeyFingerprint(), ConnectionState.ATTEMPTING_P2P);
        updateSendButtonState();
        updateP2pStatus(ConnectionState.ATTEMPTING_P2P, selectedPeer.username());

        natTraversalService.connectToPeer(endpoint, state -> {
            Platform.runLater(() -> {
                peerConnectionStates.put(selectedPeer.publicKeyFingerprint(), state);
                updateSendButtonState();
                updateP2pStatus(state, selectedPeer.username());
                publicPeerListView.refresh();
            });
        }).thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.success()) {
                    updateP2pStatus(ConnectionState.CONNECTED, selectedPeer.username());
                } else {
                    String reason = result.failureReason() != null ? result.failureReason().name() : "Unknown";
                    p2pStatusLabel.setText("⚠ Connection failed: " + reason);
                    p2pStatusLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 10;");
                }
            });
        });
    }

    private void updateP2pStatus(ConnectionState state, String username) {
        String statusText = switch (state) {
            case DISCOVERED -> "";
            case ATTEMPTING_P2P -> "🔄 Attempting P2P to @" + username + "...";
            case HOLE_PUNCHING -> "🔄 NAT hole punching...";
            case AUTHENTICATING -> "🔐 Authenticating...";
            case CONNECTED -> "✓ Connected to @" + username;
            case FAILED_NAT -> "⚠ NAT traversal failed";
            case FAILED_AUTH -> "⚠ Authentication failed";
            case DISCONNECTED -> "";
        };

        String style = switch (state) {
            case CONNECTED -> "-fx-text-fill: #16c79a; -fx-font-size: 10;";
            case FAILED_NAT, FAILED_AUTH -> "-fx-text-fill: #e94560; -fx-font-size: 10;";
            default -> "-fx-text-fill: #f39c12; -fx-font-size: 10;";
        };

        p2pStatusLabel.setText(statusText);
        p2pStatusLabel.setStyle(style);
    }

    private VBox createTransferSection() {
        VBox section = new VBox(5);
        section.setAlignment(Pos.CENTER_LEFT);
        section.setPadding(new Insets(10, 0, 0, 0));
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 5; -fx-padding: 10;");

        Label sectionTitle = new Label("📁 File Transfer");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        sectionTitle.setStyle("-fx-text-fill: #ffffff;");

        transferProgressBar = new ProgressBar(0);
        transferProgressBar.setPrefWidth(Double.MAX_VALUE);
        transferProgressBar.setStyle("-fx-accent: #16c79a;");

        transferStatusLabel = new Label("Ready");
        transferStatusLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        transferStatusLabel.setStyle("-fx-text-fill: #a0a0a0;");

        section.getChildren().addAll(sectionTitle, transferProgressBar, transferStatusLabel);
        return section;
    }

    private void onSendFileClicked() {
        LanPeer selectedPeer = lanPeerListView.getSelectionModel().getSelectedItem();
        if (selectedPeer == null) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            sendFileButton.setDisable(true);
            transferProgressBar.setProgress(0);
            transferStatusLabel.setText("📤 Sending: " + file.getName());

            fileTransferService.sendFile(
                    selectedPeer,
                    file,
                    this::onSendProgress,
                    () -> Platform.runLater(() -> {
                        transferStatusLabel.setText("📤 ✓ Sent: " + file.getName());
                        transferProgressBar.setProgress(0);
                        sendFileButton.setDisable(false);
                    }),
                    error -> Platform.runLater(() -> {
                        transferStatusLabel.setText("📤 ✗ Failed: " + error);
                        transferProgressBar.setProgress(0);
                        sendFileButton.setDisable(false);
                    })
            );
        }
    }

    private void onSendProgress(TransferProgress progress) {
        Platform.runLater(() -> {
            transferProgressBar.setProgress(progress.getPercentage() / 100.0);
            String stateText = switch (progress.getState()) {
                case CONNECTING -> "Connecting...";
                case HANDSHAKING -> "Securing channel...";
                case TRANSFERRING -> String.format("Sending: %.1f%%", progress.getPercentage());
                case COMPLETING -> "Finalizing...";
                case COMPLETED -> "✓ Sent!";
                case FAILED -> "✗ Failed";
                case CANCELLED -> "Cancelled";
            };
            transferStatusLabel.setText("📤 " + stateText);
        });
    }

    private Label createEmptyPlaceholder(String text) {
        Label placeholder = new Label(text);
        placeholder.setStyle("-fx-text-fill: #666666;");
        placeholder.setFont(Font.font("System", FontWeight.NORMAL, 12));
        return placeholder;
    }

    private static class LanPeerListCell extends ListCell<LanPeer> {
        @Override
        protected void updateItem(LanPeer peer, boolean empty) {
            super.updateItem(peer, empty);
            if (empty || peer == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                HBox container = new HBox(15);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(8));
                container.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 5;");

                Label ipLabel = new Label(peer.getDisplayAddress());
                ipLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 12));
                ipLabel.setStyle("-fx-text-fill: #ffffff;");
                ipLabel.setMinWidth(120);

                Label fingerprintLabel = new Label(peer.getShortFingerprint());
                fingerprintLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11));
                fingerprintLabel.setStyle("-fx-text-fill: #16c79a;");

                container.getChildren().addAll(ipLabel, fingerprintLabel);
                setGraphic(container);
                setStyle("-fx-background-color: transparent; -fx-padding: 2 0 2 0;");
            }
        }
    }

    private class PublicPeerListCell extends ListCell<PeerInfo> {
        @Override
        protected void updateItem(PeerInfo peer, boolean empty) {
            super.updateItem(peer, empty);
            if (empty || peer == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                HBox container = new HBox(10);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(8));
                container.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 5;");

                ConnectionState state = peerConnectionStates.get(peer.publicKeyFingerprint());
                Label stateLabel = new Label(getStateIcon(state));
                stateLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                stateLabel.setMinWidth(20);

                Label usernameLabel = new Label("@" + peer.username());
                usernameLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
                usernameLabel.setStyle("-fx-text-fill: #ffffff;");
                usernameLabel.setMinWidth(90);

                Label ipLabel = new Label(peer.ipAddress());
                ipLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11));
                ipLabel.setStyle("-fx-text-fill: #a0a0a0;");
                ipLabel.setMinWidth(110);

                Label fingerprintLabel = new Label(peer.shortFingerprint());
                fingerprintLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11));
                fingerprintLabel.setStyle("-fx-text-fill: #16c79a;");

                container.getChildren().addAll(stateLabel, usernameLabel, ipLabel, fingerprintLabel);
                setGraphic(container);
                setStyle("-fx-background-color: transparent; -fx-padding: 2 0 2 0;");
            }
        }

        private String getStateIcon(ConnectionState state) {
            if (state == null) {
                return "○";
            }
            return switch (state) {
                case DISCOVERED -> "○";
                case ATTEMPTING_P2P, HOLE_PUNCHING, AUTHENTICATING -> "◐";
                case CONNECTED -> "●";
                case FAILED_NAT, FAILED_AUTH -> "✗";
                case DISCONNECTED -> "○";
            };
        }
    }

    private void onBackupIdentity() {
        showInfoDialog("Backup Identity",
            "Backup functionality is under development.\n\n" +
            "Your identity fingerprint:\n" + localIdentity.getFormattedFingerprint() + "\n\n" +
            "Please write down this fingerprint for now.");
    }

    private void onRestoreIdentity() {
        showInfoDialog("Restore Identity",
            "Restore functionality is under development.\n\n" +
            "Current identity cannot be restored yet.");
    }

    private void showRestoreDialog() {
    }

    private void restoreFromBackupFile() {
    }

    private void restoreFromSeedPhrase() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Restore from Seed Phrase");
        dialog.setHeaderText("Enter your 12-word seed phrase:");
        dialog.setContentText("Seed phrase:");
        dialog.getEditor().setPrefColumnCount(50);
        dialog.getDialogPane().getStylesheets().add("data:text/css," +
            ".dialog-pane { -fx-background-color: #16213e; } " +
            ".dialog-pane .content { -fx-background-color: #16213e; } " +
            ".dialog-pane .label { -fx-text-fill: #ffffff; } " +
            ".dialog-pane .text-field { -fx-background-color: #0f3460; -fx-text-fill: #ffffff; }");

        dialog.showAndWait().ifPresent(mnemonicText -> {
            try {
                SeedPhrase seedPhrase = SeedPhrase.fromWords(mnemonicText);

                identityManager.deleteIdentity();
                LocalIdentity newIdentity = identityManager.restoreFromSeedPhrase(seedPhrase);
                localIdentity = newIdentity;

                primaryStage.close();
                Platform.runLater(() -> {
                    try {
                        start(new Stage());
                        showInfoDialog("Restore Complete",
                            "Identity restored successfully!\n\n" +
                            "Fingerprint: " + newIdentity.getFormattedFingerprint());
                    } catch (Exception e) {
                        showErrorDialog("Restart Failed", "Please restart the application manually.");
                    }
                });

            } catch (Exception e) {
                showErrorDialog("Restore Failed", "Invalid seed phrase: " + e.getMessage());
            }
        });
    }


    private void showInfoDialog(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add("data:text/css," +
            ".dialog-pane { -fx-background-color: #16213e; } " +
            ".dialog-pane .content { -fx-background-color: #16213e; } " +
            ".dialog-pane .label { -fx-text-fill: #ffffff; }");
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add("data:text/css," +
            ".dialog-pane { -fx-background-color: #16213e; } " +
            ".dialog-pane .content { -fx-background-color: #16213e; } " +
            ".dialog-pane .label { -fx-text-fill: #ffffff; }");
        alert.showAndWait();
    }
}
