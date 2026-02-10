/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 13:00
 * File: FirstRunWizard.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.ui;

import com.anonet.anonetclient.identity.DeterministicIdentity;
import com.anonet.anonetclient.identity.LocalIdentity;
import com.anonet.anonetclient.identity.SeedPhrase;
import com.anonet.anonetclient.identity.Username;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.function.Consumer;

public final class FirstRunWizard {

    private static final double WIZARD_WIDTH = 550;
    private static final double WIZARD_HEIGHT = 500;

    private final Stage wizardStage;
    private final Consumer<WizardResult> onComplete;
    private int currentStep = 0;
    private String generatedSeedPhrase;
    private String chosenDisplayName;
    private LocalIdentity createdIdentity;

    public FirstRunWizard(Stage owner, Consumer<WizardResult> onComplete) {
        this.onComplete = onComplete;
        this.wizardStage = new Stage();
        wizardStage.initOwner(owner);
        wizardStage.initModality(Modality.APPLICATION_MODAL);
        wizardStage.initStyle(StageStyle.UNDECORATED);
        wizardStage.setTitle("Welcome to ANONET");
    }

    public void show() {
        showStep(0);
        wizardStage.showAndWait();
    }

    private void showStep(int step) {
        currentStep = step;
        VBox content = switch (step) {
            case 0 -> createWelcomeStep();
            case 1 -> createChooseNameStep();
            case 2 -> createSeedPhraseStep();
            case 3 -> createConfirmStep();
            case 4 -> createCompleteStep();
            default -> createWelcomeStep();
        };
        wizardStage.setScene(new Scene(content, WIZARD_WIDTH, WIZARD_HEIGHT));
    }

    private VBox createWelcomeStep() {
        VBox layout = createBaseLayout();

        Label title = new Label("Welcome to ANONET");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setStyle("-fx-text-fill: #e94560;");

        Label subtitle = new Label("Anonymous Peer-to-Peer File Transfer");
        subtitle.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 14;");

        VBox features = new VBox(8);
        features.setAlignment(Pos.CENTER_LEFT);
        features.setPadding(new Insets(20, 40, 20, 40));

        String[] featureTexts = {
            "🔐 End-to-end encrypted file transfers",
            "🌐 No central server - fully decentralized",
            "🧅 Optional onion routing for anonymity",
            "📱 Easy identity backup with 12-word phrase",
            "👥 Add contacts by username"
        };

        for (String text : featureTexts) {
            Label feature = new Label(text);
            feature.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 13;");
            features.getChildren().add(feature);
        }

        Label notice = new Label("Let's set up your secure identity");
        notice.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12;");

        Button nextButton = createPrimaryButton("Get Started →");
        nextButton.setOnAction(e -> showStep(1));

        layout.getChildren().addAll(title, subtitle, features, notice, nextButton);
        return layout;
    }

    private VBox createChooseNameStep() {
        VBox layout = createBaseLayout();

        Label title = new Label("Choose Your Display Name");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #ffffff;");

        Label description = new Label("This name will be visible to other users.\nA unique discriminator will be added automatically.");
        description.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12; -fx-text-alignment: center;");
        description.setWrapText(true);

        TextField nameField = new TextField();
        nameField.setPromptText("Enter display name (e.g., alice)");
        nameField.setMaxWidth(300);
        nameField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 14; -fx-padding: 10;");

        Label previewLabel = new Label("Your username will be: ");
        previewLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11;");

        Label usernamePreview = new Label("yourname#XXXXXXXX");
        usernamePreview.setStyle("-fx-text-fill: #16c79a; -fx-font-size: 14; -fx-font-family: 'Monospaced';");

        nameField.textProperty().addListener((obs, old, newVal) -> {
            String clean = newVal.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();
            if (clean.length() > 20) clean = clean.substring(0, 20);
            if (!clean.equals(newVal)) {
                nameField.setText(clean);
            }
            usernamePreview.setText(clean.isEmpty() ? "yourname#XXXXXXXX" : clean + "#XXXXXXXX");
        });

        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER);

        Button backButton = createSecondaryButton("← Back");
        backButton.setOnAction(e -> showStep(0));

        Button nextButton = createPrimaryButton("Generate Identity →");
        nextButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                nameField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 14; -fx-padding: 10; -fx-border-color: #e94560;");
                return;
            }
            chosenDisplayName = name;
            generateIdentity();
            showStep(2);
        });

        buttons.getChildren().addAll(backButton, nextButton);
        layout.getChildren().addAll(title, description, nameField, previewLabel, usernamePreview, buttons);
        return layout;
    }

    private void generateIdentity() {
        try {
            SeedPhrase seedPhrase = SeedPhrase.generate();
            generatedSeedPhrase = seedPhrase.toString();
            createdIdentity = DeterministicIdentity.deriveFromSeedPhrase(seedPhrase);
        } catch (Exception e) {
            generatedSeedPhrase = "Error generating seed phrase";
        }
    }

    private VBox createSeedPhraseStep() {
        VBox layout = createBaseLayout();

        Label title = new Label("⚠️ IMPORTANT: Save Your Recovery Phrase");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #e94560;");

        Label warning = new Label("Write down these 12 words in order.\nThis is the ONLY way to recover your identity if you lose access.");
        warning.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12; -fx-text-alignment: center;");
        warning.setWrapText(true);

        TextArea seedArea = new TextArea(generatedSeedPhrase);
        seedArea.setEditable(false);
        seedArea.setWrapText(true);
        seedArea.setMaxWidth(400);
        seedArea.setPrefHeight(100);
        seedArea.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #16c79a; -fx-font-size: 14; -fx-font-family: 'Monospaced';");

        Label tipLabel = new Label("💡 TIP: Write on paper and store in a safe place.\nNever share with anyone. Never store digitally.");
        tipLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11; -fx-text-alignment: center;");
        tipLabel.setWrapText(true);

        CheckBox confirmCheck = new CheckBox("I have written down my recovery phrase");
        confirmCheck.setStyle("-fx-text-fill: #ffffff;");

        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER);

        Button backButton = createSecondaryButton("← Back");
        backButton.setOnAction(e -> showStep(1));

        Button nextButton = createPrimaryButton("Continue →");
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> showStep(3));

        confirmCheck.selectedProperty().addListener((obs, old, selected) -> nextButton.setDisable(!selected));

        buttons.getChildren().addAll(backButton, nextButton);
        layout.getChildren().addAll(title, warning, seedArea, tipLabel, confirmCheck, buttons);
        return layout;
    }

    private VBox createConfirmStep() {
        VBox layout = createBaseLayout();

        Label title = new Label("Verify Your Recovery Phrase");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #ffffff;");

        Label instruction = new Label("Enter word #1, #4, and #8 from your recovery phrase to confirm you saved it.");
        instruction.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12;");
        instruction.setWrapText(true);

        String[] words = generatedSeedPhrase.split("\\s+");

        VBox verifyBox = new VBox(10);
        verifyBox.setAlignment(Pos.CENTER);

        TextField word1Field = createVerifyField("Word #1");
        TextField word4Field = createVerifyField("Word #4");
        TextField word8Field = createVerifyField("Word #8");

        verifyBox.getChildren().addAll(word1Field, word4Field, word8Field);

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 11;");

        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER);

        Button backButton = createSecondaryButton("← Back");
        backButton.setOnAction(e -> showStep(2));

        Button verifyButton = createPrimaryButton("Verify & Create Identity");
        verifyButton.setOnAction(e -> {
            String w1 = word1Field.getText().trim().toLowerCase();
            String w4 = word4Field.getText().trim().toLowerCase();
            String w8 = word8Field.getText().trim().toLowerCase();

            boolean valid = words.length >= 8 &&
                    w1.equals(words[0].toLowerCase()) &&
                    w4.equals(words[3].toLowerCase()) &&
                    w8.equals(words[7].toLowerCase());

            if (valid) {
                showStep(4);
            } else {
                statusLabel.setText("❌ Words don't match. Please check your recovery phrase.");
            }
        });

        buttons.getChildren().addAll(backButton, verifyButton);
        layout.getChildren().addAll(title, instruction, verifyBox, statusLabel, buttons);
        return layout;
    }

    private TextField createVerifyField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(200);
        field.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 13; -fx-padding: 8;");
        return field;
    }

    private VBox createCompleteStep() {
        VBox layout = createBaseLayout();

        Label title = new Label("✓ Identity Created Successfully!");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #16c79a;");

        String fingerprint = createdIdentity != null ? createdIdentity.getFormattedFingerprint() : "Unknown";
        String discriminator = fingerprint.replace(":", "").substring(0, 8);
        String fullUsername = chosenDisplayName + "#" + discriminator;

        Label usernameLabel = new Label("Your Username:");
        usernameLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12;");

        Label usernameValue = new Label(fullUsername);
        usernameValue.setFont(Font.font("Monospaced", FontWeight.BOLD, 18));
        usernameValue.setStyle("-fx-text-fill: #ffffff; -fx-background-color: #0f3460; -fx-padding: 10; -fx-background-radius: 5;");

        Label fingerprintLabel = new Label("Your Fingerprint:");
        fingerprintLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12;");

        Label fingerprintValue = new Label(fingerprint);
        fingerprintValue.setFont(Font.font("Monospaced", FontWeight.NORMAL, 10));
        fingerprintValue.setStyle("-fx-text-fill: #16c79a;");
        fingerprintValue.setWrapText(true);

        Label readyLabel = new Label("You're ready to send and receive files anonymously!");
        readyLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 13;");

        Button finishButton = createPrimaryButton("Start Using ANONET →");
        finishButton.setOnAction(e -> {
            WizardResult result = new WizardResult(createdIdentity, chosenDisplayName, generatedSeedPhrase);
            wizardStage.close();
            if (onComplete != null) {
                onComplete.accept(result);
            }
        });

        layout.getChildren().addAll(title, usernameLabel, usernameValue, fingerprintLabel, fingerprintValue, readyLabel, finishButton);
        return layout;
    }

    private VBox createBaseLayout() {
        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle("-fx-background-color: #1a1a2e;");
        return layout;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #16c79a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 13; -fx-padding: 10 25; -fx-cursor: hand;");
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 13; -fx-padding: 10 25; -fx-cursor: hand;");
        return button;
    }

    public static final class WizardResult {
        private final LocalIdentity identity;
        private final String displayName;
        private final String seedPhrase;

        public WizardResult(LocalIdentity identity, String displayName, String seedPhrase) {
            this.identity = identity;
            this.displayName = displayName;
            this.seedPhrase = seedPhrase;
        }

        public LocalIdentity getIdentity() {
            return identity;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSeedPhrase() {
            return seedPhrase;
        }
    }
}
