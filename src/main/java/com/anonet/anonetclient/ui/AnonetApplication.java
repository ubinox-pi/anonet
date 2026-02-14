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

import com.anonet.anonetclient.contacts.Contact;
import com.anonet.anonetclient.contacts.ContactManager;
import com.anonet.anonetclient.crypto.session.SessionCryptoVerification;
import com.anonet.anonetclient.dht.DhtBootstrap;
import com.anonet.anonetclient.dht.DhtClient;
import com.anonet.anonetclient.dht.DhtContact;
import com.anonet.anonetclient.dht.DhtNode;
import com.anonet.anonetclient.dht.NodeId;
import com.anonet.anonetclient.dht.PeerAnnouncement;
import com.anonet.anonetclient.identity.IdentityBackup;
import com.anonet.anonetclient.identity.IdentityManager;
import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.identity.SeedPhrase;
import com.anonet.anonetclient.lan.LanDiscoveryService;
import com.anonet.anonetclient.lan.LanPeer;
import com.anonet.anonetclient.logging.AnonetLogger;
import com.anonet.anonetclient.onion.CircuitBuilder;
import com.anonet.anonetclient.onion.OnionRelay;
import com.anonet.anonetclient.publicnet.NatTraversalService;
import com.anonet.anonetclient.relay.RelayNode;
import com.anonet.anonetclient.relay.RelayProtocol;
import com.anonet.anonetclient.security.RateLimiter;
import com.anonet.anonetclient.transfer.FileTransferService;
import com.anonet.anonetclient.transfer.KnownPeersStore;
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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AnonetApplication extends Application {

    private static final AnonetLogger LOG = AnonetLogger.get(AnonetApplication.class);

    private static final String APP_TITLE = "ANONET";
    private static final double WINDOW_WIDTH = 800;
    private static final double WINDOW_HEIGHT = 700;

    private final IdentityManager identityManager;
    private AppSettings appSettings;
    private LocalIdentity localIdentity;
    private LanDiscoveryService lanDiscoveryService;
    private FileTransferService fileTransferService;
    private NatTraversalService natTraversalService;
    private DhtClient dhtClient;
    private DhtBootstrap dhtBootstrap;
    private ContactManager contactManager;
    private KnownPeersStore knownPeersStore;

    private RelayNode relayNode;
    private OnionRelay onionRelay;
    private CircuitBuilder circuitBuilder;
    private ScheduledExecutorService scheduler;
    private volatile long lastAnnounceTime = 0;

    private ObservableList<LanPeer> lanPeerList;
    private ListView<LanPeer> lanPeerListView;
    private Label lanPeerCountLabel;
    private Label cryptoStatusLabel;
    private Label transferStatusLabel;
    private ProgressBar transferProgressBar;
    private Button sendFileButton;
    private Stage primaryStage;
    private StatusBar statusBar;
    private ContactListPanel contactListPanel;
    private TransferHistoryPanel transferHistoryPanel;

    private Label networkDhtStatusLabel;
    private Label networkDhtPeersLabel;
    private Label networkDhtUserLabel;
    private Label networkDhtAnnounceLabel;
    private Label networkRelayStatusLabel;
    private Label networkRelayClientsLabel;
    private Label networkRelayRelaysLabel;
    private ListView<DhtContact> knownNodesListView;
    private ObservableList<DhtContact> knownNodesList;
    private Label networkOnionStatusLabel;
    private Label networkOnionCircuitsLabel;
    private Label networkOnionRelaysLabel;
    private volatile Set<String> knownLocalIps = new HashSet<>();

    public AnonetApplication() {
        this.identityManager = new IdentityManager();
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        appSettings = new AppSettings();
        appSettings.load();

        localIdentity = identityManager.loadOrCreateIdentity();
        LOG.info("Application starting, fingerprint: %s", localIdentity.getFormattedFingerprint());

        if (appSettings.isFirstRun()) {
            showFirstRunWizard(primaryStage);
        }

        lanPeerList = FXCollections.observableArrayList();

        lanDiscoveryService = new LanDiscoveryService(localIdentity);
        lanDiscoveryService.setOnPeersChangedCallback(this::updateLanPeerList);

        initializeDht();
        initializeContacts();
        initializeNatTraversalService();

        knownPeersStore = new KnownPeersStore(Paths.get(System.getProperty("user.home"), ".anonet"));

        Path downloadDir = Paths.get(appSettings.getDownloadPath());
        fileTransferService = new FileTransferService(localIdentity, downloadDir);
        fileTransferService.setTrustChecker(this::checkPeerTrust);
        fileTransferService.setReceiverRateLimiter(new RateLimiter(5, 1));

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "anonet-scheduler");
            t.setDaemon(true);
            return t;
        });

        VBox rootLayout = createRootLayout(localIdentity);
        Scene scene = new Scene(rootLayout, WINDOW_WIDTH, WINDOW_HEIGHT);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(550);
        primaryStage.setOnCloseRequest(_ -> shutdown());
        primaryStage.show();

        lanDiscoveryService.start();
        startFileReceiver();
        startDht();
        runCryptoVerification();

        if (appSettings.isEnableRelay()) {
            startRelayNode();
        }

        if (appSettings.isEnableOnion()) {
            startOnionRelay();
        }
    }

    private void showFirstRunWizard(Stage owner) {
        FirstRunWizard wizard = new FirstRunWizard(owner, result -> {
            if (result != null && result.getIdentity() != null) {
                localIdentity = result.getIdentity();
                appSettings.setDisplayName(result.getDisplayName());
                appSettings.setFirstRun(false);
                appSettings.save();

                identityManager.deleteIdentity();
                SeedPhrase seedPhrase = SeedPhrase.fromWords(result.getSeedPhrase());
                identityManager.createFromSeedPhrase(seedPhrase);

                LOG.info("First run wizard completed, display name: %s", result.getDisplayName());
            }
        });
        wizard.show();
    }

    private void initializeDht() {
        String displayName = appSettings.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = "anon";
        }
        dhtClient = new DhtClient(localIdentity, displayName);

        NodeId nodeId = NodeId.fromFingerprint(localIdentity.getFingerprint());
        Path anonetDir = Paths.get(System.getProperty("user.home"), ".anonet");
        dhtBootstrap = new DhtBootstrap(nodeId, anonetDir);
    }

    private void startDht() {
        CompletableFuture.runAsync(() -> {
            try {
                dhtClient.start();
                int actualDhtPort = dhtClient.getNode().getActualPort();
                LOG.info("DHT client started on port %d", actualDhtPort);

                dhtBootstrap.setActualDhtPort(actualDhtPort);
                dhtBootstrap.startLanDiscovery();

                lanDiscoveryService.setDhtPort(actualDhtPort);

                List<InetSocketAddress> bootstrapNodes = dhtBootstrap.getBootstrapNodes();
                if (!bootstrapNodes.isEmpty()) {
                    dhtClient.bootstrap(bootstrapNodes);
                }

                for (int attempt = 0; attempt < 5 && dhtClient.getKnownPeersCount() == 0; attempt++) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    List<InetSocketAddress> newNodes = dhtBootstrap.getBootstrapNodes();
                    for (InetSocketAddress node : newNodes) {
                        dhtClient.bootstrap(node);
                    }
                }

                dhtClient.announce(getAnnouncePorts());
                lastAnnounceTime = System.currentTimeMillis();

                Platform.runLater(() -> statusBar.setDhtStatus(true, dhtClient.getKnownPeersCount()));
            } catch (Exception e) {
                LOG.error("Failed to start DHT: %s", e.getMessage());
                Platform.runLater(() -> statusBar.setDhtStatus(false, 0));
            }
        });

        scheduler.scheduleAtFixedRate(() -> {
            if (dhtClient.isRunning()) {
                try {
                    List<InetSocketAddress> newNodes = dhtBootstrap.getBootstrapNodes();
                    for (InetSocketAddress node : newNodes) {
                        dhtClient.bootstrap(node);
                    }
                } catch (Exception e) {
                    LOG.debug("DHT bootstrap retry failed: %s", e.getMessage());
                }
                try {
                    dhtClient.announce(getAnnouncePorts());
                    lastAnnounceTime = System.currentTimeMillis();
                    LOG.debug("Periodic re-announce completed, peers: %d", dhtClient.getKnownPeersCount());
                } catch (Exception e) {
                    LOG.warn("DHT announce failed: %s", e.getMessage());
                }
                Platform.runLater(() -> statusBar.setDhtStatus(true, dhtClient.getKnownPeersCount()));
            }
        }, 60, 60, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            if (dhtClient.isRunning()) {
                if (detectNetworkChange()) {
                    LOG.info("Network change detected, clearing stale DHT state");
                    dhtClient.getNode().getRoutingTable().clear();
                    dhtBootstrap.clearDiscoveredNodes();
                }

                int peerCount = dhtClient.getKnownPeersCount();

                if (peerCount == 0) {
                    List<LanPeer> currentLanPeers = lanDiscoveryService.getDiscoveredPeers();
                    if (!currentLanPeers.isEmpty()) {
                        LOG.debug("DHT has 0 peers but %d LAN peers found, re-bootstrapping...", currentLanPeers.size());
                        for (LanPeer peer : currentLanPeers) {
                            if (peer.getDhtPort() > 0) {
                                dhtClient.bootstrap(new InetSocketAddress(peer.getIpAddress(), peer.getDhtPort()));
                            } else {
                                for (int p = DhtNode.DEFAULT_PORT; p <= DhtNode.DEFAULT_PORT + 5; p++) {
                                    dhtClient.bootstrap(new InetSocketAddress(peer.getIpAddress(), p));
                                }
                            }
                        }
                    }

                    for (InetSocketAddress node : dhtBootstrap.getDiscoveredNodes()) {
                        dhtClient.bootstrap(node);
                    }

                    try {
                        List<InetSocketAddress> cached = dhtBootstrap.getBootstrapNodes();
                        for (InetSocketAddress node : cached) {
                            dhtClient.bootstrap(node);
                        }
                    } catch (Exception e) {
                        LOG.debug("DHT re-bootstrap failed: %s", e.getMessage());
                    }

                    dhtClient.announce(getAnnouncePorts());
                    lastAnnounceTime = System.currentTimeMillis();
                    peerCount = dhtClient.getKnownPeersCount();
                } else if (System.currentTimeMillis() - lastAnnounceTime > 30_000) {
                    dhtClient.announce(getAnnouncePorts());
                    lastAnnounceTime = System.currentTimeMillis();
                }

                boolean relayRunning = relayNode != null && relayNode.isRunning();
                int relayClients = relayRunning ? relayNode.getConnectedClients() : 0;
                int relayRelays = relayRunning ? relayNode.getActiveRelays() : 0;
                boolean onionRunning = onionRelay != null && onionRelay.isRunning();
                int onionCircuits = onionRunning ? onionRelay.getActiveCircuits() : 0;
                int knownOnionRelays = circuitBuilder != null ? circuitBuilder.getKnownRelays().size() : 0;
                int connectionCount = relayClients + onionCircuits;

                if (circuitBuilder != null && onionRunning) {
                    circuitBuilder.refreshRelayList();
                }

                int finalPeerCount = peerCount;
                Platform.runLater(() -> {
                    statusBar.setDhtStatus(true, finalPeerCount);
                    statusBar.setRelayStatus(relayRunning, relayClients);
                    statusBar.setOnionStatus(onionRunning, onionCircuits);
                    statusBar.setConnectionStatus(connectionCount);
                    updateNetworkTab(finalPeerCount, relayRunning, relayClients, relayRelays,
                        onionRunning, onionCircuits, knownOnionRelays);
                });
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private List<Integer> getAnnouncePorts() {
        List<Integer> ports = new ArrayList<>();
        ports.add(51821);
        if (relayNode != null && relayNode.isRunning()) {
            ports.add(RelayProtocol.RELAY_PORT);
        }
        if (onionRelay != null && onionRelay.isRunning()) {
            ports.add(OnionRelay.DEFAULT_PORT);
        }
        return ports;
    }

    private void initializeContacts() {
        contactManager = new ContactManager();
        contactManager.setDhtClient(dhtClient);
        try {
            contactManager.load();
        } catch (Exception e) {
            LOG.warn("Failed to load contacts: %s", e.getMessage());
        }
    }

    private void initializeNatTraversalService() {
        natTraversalService = new NatTraversalService(localIdentity);
    }

    private void startRelayNode() {
        CompletableFuture.runAsync(() -> {
            try {
                relayNode = new RelayNode();
                relayNode.start();
                LOG.info("Relay node started");
            } catch (Exception e) {
                LOG.error("Failed to start relay node: %s", e.getMessage());
            }
        });
    }

    private void startOnionRelay() {
        CompletableFuture.runAsync(() -> {
            try {
                onionRelay = new OnionRelay(localIdentity);
                onionRelay.start();
                circuitBuilder = new CircuitBuilder(dhtClient, localIdentity);
                LOG.info("Onion relay started on port %d", OnionRelay.DEFAULT_PORT);
            } catch (Exception e) {
                LOG.error("Failed to start onion relay: %s", e.getMessage());
            }
        });
    }

    private void startFileReceiver() {
        fileTransferService.startReceiver(
                this::onReceiveProgress,
                this::onFileReceived
        );
        Platform.runLater(() -> {
            int receiverPort = fileTransferService.getReceiverPort();
            transferStatusLabel.setText("Receiver: Listening on port " + receiverPort);
            transferStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
        });
    }

    private boolean checkPeerTrust(String fingerprint) {
        if (contactManager != null) {
            for (var contact : contactManager.getAllContacts()) {
                if (contact.getFingerprint().equalsIgnoreCase(fingerprint)) {
                    return true;
                }
            }
        }
        if (knownPeersStore != null) {
            if (knownPeersStore.isKnown(fingerprint)) {
                return true;
            }
            knownPeersStore.addPeer(fingerprint);
            LOG.info("TOFU: First-time peer accepted: %s", fingerprint.substring(0, Math.min(16, fingerprint.length())));
        }
        return true;
    }

    private void onReceiveProgress(TransferProgress progress) {
        Platform.runLater(() -> {
            transferProgressBar.setProgress(progress.getPercentage() / 100.0);
            String stateText = switch (progress.getState()) {
                case CONNECTING -> "Incoming connection...";
                case HANDSHAKING -> "Establishing secure channel...";
                case TRANSFERRING -> String.format("Receiving: %.1f%%", progress.getPercentage());
                case COMPLETING -> "Verifying...";
                case COMPLETED -> "Received!";
                case FAILED -> "Failed";
                case CANCELLED -> "Cancelled";
            };
            transferStatusLabel.setText(stateText);
        });
    }

    private void onFileReceived(File file) {
        LOG.info("File received: %s", file.getName());
        Platform.runLater(() -> {
            transferStatusLabel.setText("Received: " + file.getName());
            transferProgressBar.setProgress(0);
            transferHistoryPanel.addReceivedRecord(file.getName(), file.length(), "LAN peer", true);
        });
    }

    private void runCryptoVerification() {
        CompletableFuture.runAsync(() -> {
            SessionCryptoVerification.VerificationResult result = SessionCryptoVerification.runAllTests();
            Platform.runLater(() -> {
                if (result.isAllPassed()) {
                    LOG.info("Crypto verification passed: %d/%d tests", result.getPassed(), result.getPassed());
                    cryptoStatusLabel.setText("Crypto: " + result.getPassed() + "/" + result.getPassed() + " tests passed");
                    cryptoStatusLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 10;");
                } else {
                    LOG.warn("Crypto verification failed: %d test(s) failed", result.getFailed());
                    cryptoStatusLabel.setText("Crypto: " + result.getFailed() + " test(s) failed");
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
        LOG.info("Shutting down application");

        if (dhtClient != null && dhtClient.isRunning()) {
            dhtBootstrap.saveNodes(dhtClient.getKnownPeers());
            dhtClient.stop();
        }

        if (dhtBootstrap != null) {
            dhtBootstrap.stopLanDiscovery();
        }

        if (natTraversalService != null) {
            natTraversalService.shutdown();
        }

        if (fileTransferService != null) {
            fileTransferService.shutdown();
        }

        if (lanDiscoveryService != null) {
            lanDiscoveryService.stop();
        }

        if (relayNode != null) {
            try {
                relayNode.close();
            } catch (Exception ignored) {
            }
        }

        if (onionRelay != null) {
            try {
                onionRelay.close();
            } catch (Exception ignored) {
            }
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        if (appSettings != null) {
            appSettings.save();
        }

        if (knownPeersStore != null) {
            knownPeersStore.save();
        }
    }

    private boolean detectNetworkChange() {
        Set<String> currentIps = getLocalIpAddresses();
        if (knownLocalIps.isEmpty()) {
            knownLocalIps = currentIps;
            return false;
        }
        if (!currentIps.equals(knownLocalIps)) {
            LOG.info("Network IPs changed: %s -> %s", knownLocalIps, currentIps);
            knownLocalIps = currentIps;
            return true;
        }
        return false;
    }

    private Set<String> getLocalIpAddresses() {
        Set<String> ips = new HashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to enumerate network interfaces: %s", e.getMessage());
        }
        return ips;
    }

    private void updateLanPeerList() {
        Platform.runLater(() -> {
            List<LanPeer> peers = lanDiscoveryService.getDiscoveredPeers();
            lanPeerList.setAll(peers);
            updateLanPeerCountLabel();
            statusBar.setLanStatus(peers.size());

            if (dhtClient != null && dhtClient.isRunning() && !peers.isEmpty()) {
                CompletableFuture.runAsync(() -> {
                    for (InetSocketAddress node : dhtBootstrap.getDiscoveredNodes()) {
                        dhtClient.bootstrap(node);
                    }

                    for (LanPeer peer : peers) {
                        if (peer.getDhtPort() > 0) {
                            dhtClient.bootstrap(new InetSocketAddress(peer.getIpAddress(), peer.getDhtPort()));
                        } else {
                            int actualPort = dhtClient.getNode().getActualPort();
                            dhtClient.bootstrap(new InetSocketAddress(peer.getIpAddress(), actualPort));
                            if (actualPort != DhtNode.DEFAULT_PORT) {
                                dhtClient.bootstrap(new InetSocketAddress(peer.getIpAddress(), DhtNode.DEFAULT_PORT));
                            }
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    dhtClient.announce(getAnnouncePorts());
                    lastAnnounceTime = System.currentTimeMillis();

                    for (LanPeer peer : peers) {
                        int peerDhtPort = peer.getDhtPort() > 0 ? peer.getDhtPort() : dhtClient.getNode().getActualPort();
                        InetSocketAddress peerAddr = new InetSocketAddress(peer.getIpAddress(), peerDhtPort);
                        PeerAnnouncement lastAnn = dhtClient.getLastAnnouncement();
                        if (lastAnn != null && dhtClient.getNode().isRunning()) {
                            dhtClient.getNode().announce(lastAnn, peerAddr);
                            LOG.debug("Directly announced to LAN peer %s:%d", peer.getIpAddress().getHostAddress(), peerDhtPort);
                        }
                    }

                    Platform.runLater(() -> statusBar.setDhtStatus(true, dhtClient.getKnownPeersCount()));
                });
            }
        });
    }

    private void updateLanPeerCountLabel() {
        int count = lanPeerList.size();
        String text = count == 0 ? "No peers discovered" : count + " peer(s) on LAN";
        lanPeerCountLabel.setText(text);
    }

    private VBox createRootLayout(LocalIdentity identity) {
        VBox layout = new VBox(8);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: #1a1a2e;");

        HBox toolbar = createToolbar();
        VBox identitySection = createIdentitySection(identity);
        TabPane mainTabs = createMainTabs();
        VBox transferSection = createTransferSection();
        statusBar = new StatusBar();

        VBox.setVgrow(mainTabs, Priority.ALWAYS);

        layout.getChildren().addAll(toolbar, identitySection, mainTabs, transferSection, statusBar);
        return layout;
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(5, 10, 5, 10));
        toolbar.setStyle("-fx-background-color: #16213e; -fx-background-radius: 5;");

        Label titleLabel = new Label(APP_TITLE);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        titleLabel.setStyle("-fx-text-fill: #e94560;");

        Label subtitleLabel = new Label("Encrypted P2P File Transfer");
        subtitleLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        subtitleLabel.setStyle("-fx-text-fill: #a0a0a0;");

        VBox titleBox = new VBox(2, titleLabel, subtitleLabel);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addContactButton = new Button("+ Contact");
        addContactButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11; -fx-cursor: hand;");
        addContactButton.setOnAction(e -> onAddContactClicked());

        Button settingsButton = new Button("Settings");
        settingsButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-cursor: hand;");
        settingsButton.setOnAction(e -> onSettingsClicked());

        toolbar.getChildren().addAll(titleBox, spacer, addContactButton, settingsButton);
        return toolbar;
    }

    private void onAddContactClicked() {
        AddContactDialog dialog = new AddContactDialog(primaryStage, contactManager, dhtClient, contact -> {
            if (contactListPanel != null) {
                contactListPanel.refreshContacts();
            }
        });
        dialog.show();
    }

    private void onSettingsClicked() {
        SettingsDialog.Settings currentSettings = appSettings.toDialogSettings();
        boolean wasRelayEnabled = appSettings.isEnableRelay();
        boolean wasOnionEnabled = appSettings.isEnableOnion();
        SettingsDialog dialog = new SettingsDialog(primaryStage, currentSettings, newSettings -> {
            appSettings.fromDialogSettings(newSettings);
            appSettings.save();
            LOG.info("Settings saved");

            boolean isRelayEnabled = appSettings.isEnableRelay();
            if (!wasRelayEnabled && isRelayEnabled) {
                startRelayNode();
            } else if (wasRelayEnabled && !isRelayEnabled) {
                if (relayNode != null) {
                    try {
                        relayNode.close();
                    } catch (Exception ignored) {
                    }
                    relayNode = null;
                    statusBar.setRelayStatus(false, 0);
                    LOG.info("Relay node stopped via settings");
                }
            }

            boolean isOnionEnabled = appSettings.isEnableOnion();
            if (!wasOnionEnabled && isOnionEnabled) {
                startOnionRelay();
            } else if (wasOnionEnabled && !isOnionEnabled) {
                if (onionRelay != null) {
                    try {
                        onionRelay.close();
                    } catch (Exception ignored) {
                    }
                    onionRelay = null;
                    circuitBuilder = null;
                    statusBar.setOnionStatus(false, 0);
                    LOG.info("Onion relay stopped via settings");
                }
            }
        });
        dialog.show();
    }

    private VBox createIdentitySection(LocalIdentity identity) {
        VBox section = new VBox(5);
        section.setAlignment(Pos.CENTER);
        section.setPadding(new Insets(10, 0, 5, 0));

        HBox identityRow = new HBox(10);
        identityRow.setAlignment(Pos.CENTER);

        Label fingerprintTitleLabel = new Label("Your ANONET ID:");
        fingerprintTitleLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        fingerprintTitleLabel.setStyle("-fx-text-fill: #16c79a;");

        String displayName = appSettings.getDisplayName();
        if (displayName == null || displayName.isEmpty()) displayName = "anon";
        String fullAnonetId = "anonet:" + displayName + "#" + identity.getFingerprint().substring(0, 8).toUpperCase();

        Label anonetIdLabel = new Label(fullAnonetId);
        anonetIdLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 11));
        anonetIdLabel.setStyle("-fx-text-fill: #ffffff; -fx-background-color: #0f3460; -fx-padding: 5; -fx-background-radius: 3;");

        Button copyIdButton = new Button("📋 Copy");
        copyIdButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        copyIdButton.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(fullAnonetId);
            clipboard.setContent(content);
            copyIdButton.setText("✓ Copied!");
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
                .execute(() -> Platform.runLater(() -> copyIdButton.setText("📋 Copy")));
        });

        Button backupButton = new Button("Backup");
        backupButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        backupButton.setOnAction(e -> onBackupIdentity());

        Button restoreButton = new Button("Restore");
        restoreButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        restoreButton.setOnAction(e -> onRestoreIdentity());

        identityRow.getChildren().addAll(fingerprintTitleLabel, anonetIdLabel, copyIdButton, backupButton, restoreButton);

        cryptoStatusLabel = new Label("Crypto: Verifying...");
        cryptoStatusLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
        cryptoStatusLabel.setStyle("-fx-text-fill: #a0a0a0;");

        section.getChildren().addAll(identityRow, cryptoStatusLabel);
        return section;
    }

    private TabPane createMainTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #16213e;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab networkTab = createNetworkTab();
        Tab peersTab = createLanPeerTab();

        contactListPanel = new ContactListPanel(contactManager);
        contactListPanel.setOnSendFile(this::onSendFileToContact);
        Tab contactsTab = new Tab("Contacts", contactListPanel);

        transferHistoryPanel = new TransferHistoryPanel();
        Tab historyTab = new Tab("History", transferHistoryPanel);

        LogViewerPanel logViewerPanel = new LogViewerPanel();
        Tab logsTab = new Tab("Logs", logViewerPanel);

        tabPane.getTabs().addAll(networkTab, peersTab, contactsTab, historyTab, logsTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        return tabPane;
    }

    private Tab createNetworkTab() {
        Tab tab = new Tab("Network");

        VBox content = new VBox(10);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: #16213e;");

        VBox dhtSection = createNetworkDhtSection();
        VBox relaySection = createNetworkRelaySection();
        VBox onionSection = createNetworkOnionSection();
        VBox searchSection = createNetworkSearchSection();
        VBox nodesSection = createNetworkNodesSection();

        content.getChildren().addAll(dhtSection, relaySection, onionSection, searchSection, nodesSection);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");

        tab.setContent(scrollPane);
        return tab;
    }

    private VBox createNetworkDhtSection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 5;");

        Label title = new Label("DHT Status");
        title.setFont(Font.font("System", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #16c79a;");

        String fullUsername = appSettings.getDisplayName();
        if (fullUsername == null || fullUsername.isEmpty()) fullUsername = "anon";
        fullUsername = fullUsername + "#" + localIdentity.getFingerprint().substring(0, 8).toUpperCase();

        networkDhtStatusLabel = new Label("Status: Starting...");
        networkDhtStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        networkDhtPeersLabel = new Label("Peers: 0");
        networkDhtPeersLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        networkDhtUserLabel = new Label("Your ID: " + fullUsername);
        networkDhtUserLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 11;");

        networkDhtAnnounceLabel = new Label("Announced: No");
        networkDhtAnnounceLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        HBox row1 = new HBox(20, networkDhtStatusLabel, networkDhtPeersLabel);
        HBox row2 = new HBox(20, networkDhtUserLabel, networkDhtAnnounceLabel);

        section.getChildren().addAll(title, row1, row2);
        return section;
    }

    private VBox createNetworkRelaySection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 5;");

        Label title = new Label("Relay Node");
        title.setFont(Font.font("System", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #16c79a;");

        networkRelayStatusLabel = new Label("Status: Starting...");
        networkRelayStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        networkRelayClientsLabel = new Label("Connected Clients: 0");
        networkRelayClientsLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        networkRelayRelaysLabel = new Label("Active Relays: 0");
        networkRelayRelaysLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        HBox row1 = new HBox(20, networkRelayStatusLabel);
        HBox row2 = new HBox(20, networkRelayClientsLabel, networkRelayRelaysLabel);

        section.getChildren().addAll(title, row1, row2);
        return section;
    }

    private VBox createNetworkOnionSection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 5;");

        Label title = new Label("Onion Routing");
        title.setFont(Font.font("System", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #16c79a;");

        networkOnionStatusLabel = new Label("Status: " + (appSettings.isEnableOnion() ? "Starting..." : "Disabled"));
        networkOnionStatusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        networkOnionCircuitsLabel = new Label("Active Circuits: 0");
        networkOnionCircuitsLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        networkOnionRelaysLabel = new Label("Known Relays: 0");
        networkOnionRelaysLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        HBox row1 = new HBox(20, networkOnionStatusLabel);
        HBox row2 = new HBox(20, networkOnionCircuitsLabel, networkOnionRelaysLabel);

        section.getChildren().addAll(title, row1, row2);
        return section;
    }

    private VBox createNetworkSearchSection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 5;");

        Label title = new Label("Search User");
        title.setFont(Font.font("System", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #16c79a;");

        HBox searchRow = new HBox(8);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("username#XXXXXXXX");
        searchField.setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #666666;");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchButton = new Button("Search");
        searchButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");

        searchRow.getChildren().addAll(searchField, searchButton);

        Label searchResultLabel = new Label("Enter a username to search (DHT + LAN)");
        searchResultLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");
        searchResultLabel.setWrapText(true);

        VBox resultBox = new VBox(5);
        resultBox.setPadding(new Insets(5, 0, 0, 0));

        searchButton.setOnAction(_ -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) return;

            searchButton.setDisable(true);
            searchResultLabel.setText("Searching for \"" + query + "\" (DHT + LAN)...");
            searchResultLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");
            resultBox.getChildren().clear();

            CompletableFuture<Optional<PeerAnnouncement>> dhtLookup = dhtClient.lookup(query);

            dhtLookup.thenAccept(result -> Platform.runLater(() -> {
                searchButton.setDisable(false);
                if (result.isPresent()) {
                    PeerAnnouncement ann = result.get();
                    searchResultLabel.setText("Found via DHT: " + ann.getUsername());
                    searchResultLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 11;");
                    showSearchResult(ann, resultBox);
                } else {
                    LanPeer lanMatch = findLanPeerByQuery(query);
                    if (lanMatch != null) {
                        searchResultLabel.setText("Found on LAN: " + lanMatch.getDisplayAddress());
                        searchResultLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 11;");
                        showLanSearchResult(query, lanMatch, resultBox);
                    } else {
                        searchResultLabel.setText("User \"" + query + "\" not found (DHT or LAN)");
                        searchResultLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 11;");
                    }
                }
            })).exceptionally(ex -> {
                Platform.runLater(() -> {
                    searchButton.setDisable(false);
                    LanPeer lanMatch = findLanPeerByQuery(query);
                    if (lanMatch != null) {
                        searchResultLabel.setText("DHT failed, but found on LAN: " + lanMatch.getDisplayAddress());
                        searchResultLabel.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 11;");
                        showLanSearchResult(query, lanMatch, resultBox);
                    } else {
                        searchResultLabel.setText("Search failed: " + ex.getMessage());
                        searchResultLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 11;");
                    }
                });
                return null;
            });
        });

        section.getChildren().addAll(title, searchRow, searchResultLabel, resultBox);
        return section;
    }

    private void showSearchResult(PeerAnnouncement ann, VBox resultBox) {
        Label fpLabel = new Label("Fingerprint: " + ann.getFingerprint().substring(0, 16) + "...");
        fpLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 10;");

        Label portsLabel = new Label("Ports: " + ann.getPortCandidates());
        portsLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");

        Button addContactBtn = new Button("Add to Contacts");
        addContactBtn.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        addContactBtn.setOnAction(_ -> {
            try {
                String displayName = ann.getUsername().contains("#")
                    ? ann.getUsername().substring(0, ann.getUsername().indexOf('#'))
                    : ann.getUsername();
                contactManager.addContact(displayName, ann.getUsername(), ann.getFingerprint(), ann.getPublicKey());
                if (contactListPanel != null) contactListPanel.refreshContacts();
                addContactBtn.setText("Added!");
                addContactBtn.setDisable(true);
            } catch (Exception ex) {
                addContactBtn.setText("Failed: " + ex.getMessage());
            }
        });

        resultBox.getChildren().addAll(fpLabel, portsLabel, addContactBtn);
    }

    private void showLanSearchResult(String query, LanPeer lanPeer, VBox resultBox) {
        Label fpLabel = new Label("Fingerprint: " + lanPeer.getFingerprint().substring(0, Math.min(16, lanPeer.getFingerprint().length())) + "...");
        fpLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 10;");

        Label addrLabel = new Label("LAN Address: " + lanPeer.getIpAddress().getHostAddress());
        addrLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");

        if (lanPeer.getDhtPort() > 0) {
            Label dhtLabel = new Label("DHT Port: " + lanPeer.getDhtPort());
            dhtLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10;");
            resultBox.getChildren().add(dhtLabel);
        }

        Button sendFileBtn = new Button("Send File");
        sendFileBtn.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        sendFileBtn.setOnAction(_ -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select File to Send");
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                LOG.info("Sending file via LAN search result: %s to %s", file.getName(), lanPeer.getDisplayAddress());
                sendFileBtn.setDisable(true);
                transferProgressBar.setProgress(0);
                transferStatusLabel.setText("Sending: " + file.getName());
                fileTransferService.sendFile(
                    lanPeer, file,
                    this::onSendProgress,
                    () -> Platform.runLater(() -> {
                        transferStatusLabel.setText("Sent: " + file.getName());
                        transferProgressBar.setProgress(0);
                        sendFileBtn.setDisable(false);
                        transferHistoryPanel.addSentRecord(file.getName(), file.length(), lanPeer.getDisplayAddress(), true);
                    }),
                    error -> Platform.runLater(() -> {
                        transferStatusLabel.setText("Failed: " + error);
                        transferProgressBar.setProgress(0);
                        sendFileBtn.setDisable(false);
                        transferHistoryPanel.addSentRecord(file.getName(), file.length(), lanPeer.getDisplayAddress(), false);
                    })
                );
            }
        });

        Button addContactBtn = new Button("Add to Contacts");
        addContactBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        addContactBtn.setOnAction(_ -> {
            try {
                String displayName = query.contains("#") ? query.substring(0, query.indexOf('#')) : query;
                contactManager.addContact(displayName, query, lanPeer.getFingerprint(), null);
                if (contactListPanel != null) contactListPanel.refreshContacts();
                addContactBtn.setText("Added!");
                addContactBtn.setDisable(true);
            } catch (Exception ex) {
                addContactBtn.setText("Failed: " + ex.getMessage());
            }
        });

        HBox buttonRow = new HBox(8, sendFileBtn, addContactBtn);
        resultBox.getChildren().addAll(fpLabel, addrLabel, buttonRow);
    }

    private LanPeer findLanPeerByQuery(String query) {
        String discriminator = null;
        if (query.contains("#")) {
            discriminator = query.substring(query.indexOf('#') + 1).trim().toUpperCase();
        }

        for (LanPeer peer : lanPeerList) {
            String peerFp = peer.getFingerprint();
            if (peerFp == null) continue;

            if (discriminator != null && !discriminator.isEmpty()) {
                String peerDisc = peerFp.substring(0, Math.min(discriminator.length(), peerFp.length())).toUpperCase();
                if (peerDisc.equals(discriminator)) {
                    return peer;
                }
            }

            if (peerFp.equalsIgnoreCase(query)) {
                return peer;
            }
        }

        return null;
    }

    private VBox createNetworkNodesSection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 5;");

        Label title = new Label("Known DHT Nodes");
        title.setFont(Font.font("System", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #16c79a;");

        HBox addNodeRow = new HBox(8);
        addNodeRow.setAlignment(Pos.CENTER_LEFT);

        TextField nodeIpField = new TextField();
        nodeIpField.setPromptText("IP:PORT (e.g., 192.168.1.5:51820)");
        nodeIpField.setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #666666;");
        HBox.setHgrow(nodeIpField, Priority.ALWAYS);

        Button addNodeButton = new Button("+ Add Node");
        addNodeButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-size: 10; -fx-cursor: hand;");
        addNodeButton.setOnAction(_ -> {
            String input = nodeIpField.getText().trim();
            if (input.isEmpty()) return;
            try {
                addNodeButton.setDisable(true);
                addNodeButton.setText("Connecting...");

                if (input.contains(":")) {
                    String[] parts = input.split(":");
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    InetSocketAddress addr = new InetSocketAddress(ip, port);
                    dhtClient.bootstrap(addr);
                } else {
                    for (int p = DhtNode.DEFAULT_PORT; p <= DhtNode.DEFAULT_PORT + 5; p++) {
                        dhtClient.bootstrap(new InetSocketAddress(input, p));
                    }
                }

                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    int peerCount = dhtClient.getKnownPeersCount();
                    dhtClient.announce(getAnnouncePorts());

                    Platform.runLater(() -> {
                        addNodeButton.setDisable(false);
                        addNodeButton.setText("+ Add Node");
                        nodeIpField.clear();
                        if (peerCount > 0) {
                            LOG.info("Manually added DHT node: %s, peers now: %d", input, peerCount);
                            knownNodesList.setAll(dhtClient.getKnownPeers());
                        } else {
                            LOG.warn("Added node %s but no peers responded yet", input);
                        }
                    });
                });
            } catch (Exception e) {
                addNodeButton.setDisable(false);
                addNodeButton.setText("+ Add Node");
                LOG.error("Failed to add node: %s", e.getMessage());
            }
        });

        addNodeRow.getChildren().addAll(nodeIpField, addNodeButton);

        knownNodesList = FXCollections.observableArrayList();
        knownNodesListView = new ListView<>(knownNodesList);
        knownNodesListView.setPrefHeight(150);
        knownNodesListView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        knownNodesListView.setPlaceholder(createEmptyPlaceholder("No DHT nodes known yet"));
        knownNodesListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(DhtContact contact, boolean empty) {
                super.updateItem(contact, empty);
                if (empty || contact == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox row = new HBox(15);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(3));

                    Label addrLabel = new Label(contact.getAddress().getAddress().getHostAddress() + ":" + contact.getPort());
                    addrLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 10));
                    addrLabel.setStyle("-fx-text-fill: #ffffff;");
                    addrLabel.setMinWidth(160);

                    Label nodeIdLabel = new Label("NodeId:" + contact.getNodeId().toShortHex());
                    nodeIdLabel.setFont(Font.font("Monospaced", FontWeight.NORMAL, 10));
                    nodeIdLabel.setStyle("-fx-text-fill: #16c79a;");

                    row.getChildren().addAll(addrLabel, nodeIdLabel);
                    setGraphic(row);
                    setStyle("-fx-background-color: transparent; -fx-padding: 1 0 1 0;");
                }
            }
        });

        section.getChildren().addAll(title, addNodeRow, knownNodesListView);
        return section;
    }

    private void updateNetworkTab(int peerCount, boolean relayRunning, int relayClients, int relayRelays,
                                   boolean onionRunning, int onionCircuits, int knownOnionRelays) {
        if (networkDhtStatusLabel == null) return;

        boolean dhtOnline = dhtClient != null && dhtClient.isRunning();
        networkDhtStatusLabel.setText("Status: " + (dhtOnline ? "Online" : "Offline"));
        networkDhtStatusLabel.setStyle(dhtOnline
            ? "-fx-text-fill: #16c79a; -fx-font-size: 11;"
            : "-fx-text-fill: #e94560; -fx-font-size: 11;");
        networkDhtPeersLabel.setText("Peers: " + peerCount);
        networkDhtAnnounceLabel.setText("Announced: " + (dhtOnline ? "Yes" : "No"));
        networkDhtAnnounceLabel.setStyle(dhtOnline
            ? "-fx-text-fill: #16c79a; -fx-font-size: 11;"
            : "-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        networkRelayStatusLabel.setText("Status: " + (relayRunning ? "Active  Port: " + relayNode.getActualPort() : "Off"));
        networkRelayStatusLabel.setStyle(relayRunning
            ? "-fx-text-fill: #16c79a; -fx-font-size: 11;"
            : "-fx-text-fill: #a0a0a0; -fx-font-size: 11;");
        networkRelayClientsLabel.setText("Connected Clients: " + relayClients);
        networkRelayRelaysLabel.setText("Active Relays: " + relayRelays);

        networkOnionStatusLabel.setText("Status: " + (onionRunning ? "Active  Port: " + onionRelay.getActualPort() : "Off"));
        networkOnionStatusLabel.setStyle(onionRunning
            ? "-fx-text-fill: #16c79a; -fx-font-size: 11;"
            : "-fx-text-fill: #a0a0a0; -fx-font-size: 11;");
        networkOnionCircuitsLabel.setText("Active Circuits: " + onionCircuits);
        networkOnionRelaysLabel.setText("Known Relays: " + knownOnionRelays);

        if (dhtClient != null) {
            List<DhtContact> peers = dhtClient.getKnownPeers();
            knownNodesList.setAll(peers);
        }
    }

    private void onSendFileToContact(Contact contact) {
        LOG.info("Send file to contact: %s", contact.getDisplayName());

        InetAddress lanAddress = findPeerAddressByFingerprint(contact.getFingerprint());

        if (dhtClient == null || !dhtClient.isRunning()) {
            if (lanAddress != null) {
                promptSendFile(contact, lanAddress);
            } else {
                showErrorDialog("Send File", "DHT is not connected and contact is not on LAN.");
            }
            return;
        }

        dhtClient.lookup(contact.getUsername()).thenAccept(result -> Platform.runLater(() -> {
            InetAddress peerAddress = null;

            if (result.isPresent()) {
                PeerAnnouncement ann = result.get();
                peerAddress = findPeerAddress(ann.getFingerprint());
            }

            if (peerAddress == null) {
                peerAddress = findPeerAddressByFingerprint(contact.getFingerprint());
            }

            if (peerAddress == null) {
                showInfoDialog("Send File", "Contact \"" + contact.getDisplayName() + "\" is not currently reachable.\n\n" +
                    "They must be on the same LAN or discoverable via DHT.");
                return;
            }

            promptSendFile(contact, peerAddress);
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                InetAddress fallback = findPeerAddressByFingerprint(contact.getFingerprint());
                if (fallback != null) {
                    promptSendFile(contact, fallback);
                } else {
                    showErrorDialog("Send File", "DHT lookup failed: " + ex.getMessage());
                }
            });
            return null;
        });
    }

    private InetAddress findPeerAddress(String fingerprint) {
        for (LanPeer lanPeer : lanPeerList) {
            if (lanPeer.getFingerprint().equalsIgnoreCase(fingerprint)) {
                return lanPeer.getIpAddress();
            }
        }

        if (dhtClient != null) {
            NodeId targetNodeId = NodeId.fromFingerprint(fingerprint);
            for (DhtContact contact : dhtClient.getKnownPeers()) {
                if (contact.getNodeId().equals(targetNodeId)) {
                    return contact.getIp();
                }
            }
        }

        return null;
    }

    private InetAddress findPeerAddressByFingerprint(String fingerprint) {
        for (LanPeer lanPeer : lanPeerList) {
            if (lanPeer.getFingerprint().equalsIgnoreCase(fingerprint)) {
                return lanPeer.getIpAddress();
            }
        }
        return null;
    }

    private void promptSendFile(Contact contact, InetAddress peerAddress) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send to " + contact.getDisplayName());
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            LanPeer targetPeer = new LanPeer(peerAddress, contact.getFingerprint());
            LOG.info("Sending file: %s to contact %s at %s", file.getName(), contact.getDisplayName(), peerAddress.getHostAddress());
            sendFileButton.setDisable(true);
            transferProgressBar.setProgress(0);
            transferStatusLabel.setText("Sending: " + file.getName() + " to " + contact.getDisplayName());

            fileTransferService.sendFile(
                targetPeer, file,
                this::onSendProgress,
                () -> Platform.runLater(() -> {
                    transferStatusLabel.setText("Sent: " + file.getName());
                    transferProgressBar.setProgress(0);
                    sendFileButton.setDisable(false);
                    transferHistoryPanel.addSentRecord(file.getName(), file.length(), contact.getDisplayName(), true);
                }),
                error -> Platform.runLater(() -> {
                    transferStatusLabel.setText("Failed: " + error);
                    transferProgressBar.setProgress(0);
                    sendFileButton.setDisable(false);
                    transferHistoryPanel.addSentRecord(file.getName(), file.length(), contact.getDisplayName(), false);
                })
            );
        }
    }

    private Tab createLanPeerTab() {
        Tab tab = new Tab("LAN Peers");

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

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));

        sendFileButton = new Button("Send File");
        sendFileButton.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-cursor: hand;");
        sendFileButton.setDisable(true);
        sendFileButton.setOnAction(e -> onSendFileClicked());

        buttonBox.getChildren().add(sendFileButton);

        content.getChildren().addAll(lanPeerCountLabel, lanPeerListView, buttonBox);
        tab.setContent(content);
        return tab;
    }

    private void updateSendButtonState() {
        boolean hasSelection = lanPeerListView.getSelectionModel().getSelectedItem() != null;
        sendFileButton.setDisable(!hasSelection);
    }

    private VBox createTransferSection() {
        VBox section = new VBox(5);
        section.setAlignment(Pos.CENTER_LEFT);
        section.setPadding(new Insets(5, 0, 0, 0));
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 5; -fx-padding: 8;");

        Label sectionTitle = new Label("File Transfer");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
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
            LOG.info("Sending file: %s to %s", file.getName(), selectedPeer.getDisplayAddress());
            sendFileButton.setDisable(true);
            transferProgressBar.setProgress(0);
            transferStatusLabel.setText("Sending: " + file.getName());

            fileTransferService.sendFile(
                    selectedPeer,
                    file,
                    this::onSendProgress,
                    () -> Platform.runLater(() -> {
                        transferStatusLabel.setText("Sent: " + file.getName());
                        transferProgressBar.setProgress(0);
                        sendFileButton.setDisable(false);
                        transferHistoryPanel.addSentRecord(file.getName(), file.length(), selectedPeer.getDisplayAddress(), true);
                    }),
                    error -> Platform.runLater(() -> {
                        transferStatusLabel.setText("Failed: " + error);
                        transferProgressBar.setProgress(0);
                        sendFileButton.setDisable(false);
                        transferHistoryPanel.addSentRecord(file.getName(), file.length(), selectedPeer.getDisplayAddress(), false);
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
                case COMPLETED -> "Sent!";
                case FAILED -> "Failed";
                case CANCELLED -> "Cancelled";
            };
            transferStatusLabel.setText(stateText);
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

    private void onBackupIdentity() {
        if (identityManager.hasSeedHash()) {
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
            dialog.setTitle("Backup Identity");
            dialog.setHeaderText("Enter your seed phrase to export:");
            dialog.setContentText("Seed phrase:");
            dialog.getEditor().setPrefColumnCount(50);
            styleDialog(dialog.getDialogPane());

            dialog.showAndWait().ifPresent(mnemonic -> {
                try {
                    SeedPhrase seedPhrase = SeedPhrase.fromWords(mnemonic);
                    if (!identityManager.verifySeedPhrase(seedPhrase)) {
                        showErrorDialog("Backup Failed", "Seed phrase does not match current identity.");
                        return;
                    }

                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Save Identity Backup");
                    fileChooser.setInitialFileName(appSettings.getDisplayName() + ".anonet-identity");
                    File file = fileChooser.showSaveDialog(primaryStage);

                    if (file != null) {
                        IdentityBackup.exportIdentity(localIdentity, seedPhrase, appSettings.getDisplayName(), file.getParentFile().toPath());
                        showInfoDialog("Backup Successful", "Identity backup saved to:\n" + file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    LOG.error("Backup failed: %s", e.getMessage());
                    showErrorDialog("Backup Failed", "Error: " + e.getMessage());
                }
            });
        } else {
            showInfoDialog("Backup Identity",
                "Your identity fingerprint:\n" + localIdentity.getFormattedFingerprint() + "\n\n" +
                "To create a full backup, restore your identity from a seed phrase first.");
        }
    }

    private void onRestoreIdentity() {
        javafx.scene.control.Alert choiceDialog = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        choiceDialog.setTitle("Restore Identity");
        choiceDialog.setHeaderText("Choose restore method:");
        choiceDialog.setContentText("How would you like to restore your identity?");
        styleDialog(choiceDialog.getDialogPane());

        javafx.scene.control.ButtonType seedPhraseBtn = new javafx.scene.control.ButtonType("Seed Phrase");
        javafx.scene.control.ButtonType backupFileBtn = new javafx.scene.control.ButtonType("Backup File");
        javafx.scene.control.ButtonType cancelBtn = new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

        choiceDialog.getButtonTypes().setAll(seedPhraseBtn, backupFileBtn, cancelBtn);

        choiceDialog.showAndWait().ifPresent(choice -> {
            if (choice == seedPhraseBtn) {
                restoreFromSeedPhrase();
            } else if (choice == backupFileBtn) {
                restoreFromBackupFile();
            }
        });
    }

    private void restoreFromSeedPhrase() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Restore from Seed Phrase");
        dialog.setHeaderText("Enter your 12-word seed phrase:");
        dialog.setContentText("Seed phrase:");
        dialog.getEditor().setPrefColumnCount(50);
        styleDialog(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(mnemonicText -> {
            try {
                SeedPhrase seedPhrase = SeedPhrase.fromWords(mnemonicText);

                identityManager.deleteIdentity();
                LocalIdentity newIdentity = identityManager.restoreFromSeedPhrase(seedPhrase);
                localIdentity = newIdentity;
                LOG.info("Identity restored, fingerprint: %s", newIdentity.getFormattedFingerprint());

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
                LOG.error("Identity restore failed: %s", e.getMessage());
                showErrorDialog("Restore Failed", "Invalid seed phrase: " + e.getMessage());
            }
        });
    }

    private void restoreFromBackupFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Identity Backup File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("ANONET Identity", "*.anonet-identity")
        );
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            try {
                IdentityBackup.BackupData backupData = IdentityBackup.importIdentity(file.toPath());

                identityManager.deleteIdentity();
                LocalIdentity newIdentity = IdentityBackup.restoreFromBackup(file.toPath());
                localIdentity = newIdentity;

                appSettings.setDisplayName(backupData.displayName);
                appSettings.save();

                LOG.info("Identity restored from backup, fingerprint: %s", newIdentity.getFormattedFingerprint());

                primaryStage.close();
                Platform.runLater(() -> {
                    try {
                        start(new Stage());
                        showInfoDialog("Restore Complete",
                            "Identity restored from backup!\n\n" +
                            "Display Name: " + backupData.displayName + "\n" +
                            "Fingerprint: " + newIdentity.getFormattedFingerprint());
                    } catch (Exception e) {
                        showErrorDialog("Restart Failed", "Please restart the application manually.");
                    }
                });
            } catch (Exception e) {
                LOG.error("Backup restore failed: %s", e.getMessage());
                showErrorDialog("Restore Failed", "Failed to restore from backup: " + e.getMessage());
            }
        }
    }

    private void styleDialog(javafx.scene.control.DialogPane dialogPane) {
        dialogPane.getStylesheets().add("data:text/css," +
            ".dialog-pane { -fx-background-color: #16213e; } " +
            ".dialog-pane .content { -fx-background-color: #16213e; } " +
            ".dialog-pane .label { -fx-text-fill: #ffffff; } " +
            ".dialog-pane .text-field { -fx-background-color: #0f3460; -fx-text-fill: #ffffff; }");
    }

    private void showInfoDialog(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }
}
