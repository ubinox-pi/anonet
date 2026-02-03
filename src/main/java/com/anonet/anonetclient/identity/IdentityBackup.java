/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 02-02-2026 12:35
 * File: IdentityBackup.java
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles identity backup and restore operations.
 * Exports identity to JSON backup files and restores from seed phrases.
 */
public final class IdentityBackup {

    private static final String BACKUP_FILE_EXTENSION = ".anonet-identity";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private IdentityBackup() {}

    public static class BackupData {
        public final String version;
        public final long timestamp;
        public final String displayName;
        public final String seedPhrase;
        public final String fingerprint;
        public final String publicKeyBase64;

        public BackupData(String displayName, String seedPhrase, String fingerprint, String publicKeyBase64) {
            this.version = "1.0";
            this.timestamp = System.currentTimeMillis();
            this.displayName = displayName;
            this.seedPhrase = seedPhrase;
            this.fingerprint = fingerprint;
            this.publicKeyBase64 = publicKeyBase64;
        }

        private BackupData(String version, long timestamp, String displayName,
                          String seedPhrase, String fingerprint, String publicKeyBase64) {
            this.version = version;
            this.timestamp = timestamp;
            this.displayName = displayName;
            this.seedPhrase = seedPhrase;
            this.fingerprint = fingerprint;
            this.publicKeyBase64 = publicKeyBase64;
        }

        public String toJson() {
            return "{\n" +
                "  \"version\": \"" + version + "\",\n" +
                "  \"timestamp\": " + timestamp + ",\n" +
                "  \"displayName\": \"" + escapeJson(displayName) + "\",\n" +
                "  \"seedPhrase\": \"" + escapeJson(seedPhrase) + "\",\n" +
                "  \"fingerprint\": \"" + escapeJson(fingerprint) + "\",\n" +
                "  \"publicKeyBase64\": \"" + escapeJson(publicKeyBase64) + "\"\n" +
                "}";
        }

        public static BackupData fromJson(String json) {
            String version = extractJsonString(json, "version");
            long timestamp = extractJsonLong(json, "timestamp");
            String displayName = extractJsonString(json, "displayName");
            String seedPhrase = extractJsonString(json, "seedPhrase");
            String fingerprint = extractJsonString(json, "fingerprint");
            String publicKeyBase64 = extractJsonString(json, "publicKeyBase64");

            return new BackupData(version, timestamp, displayName, seedPhrase, fingerprint, publicKeyBase64);
        }

        private static String escapeJson(String input) {
            if (input == null) return "";
            return input.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
        }

        private static String extractJsonString(String json, String key) {
            Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
            }
            return "";
        }

        private static long extractJsonLong(String json, String key) {
            Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
            return 0;
        }
    }

    public static Path exportIdentity(LocalIdentity identity, SeedPhrase seedPhrase,
                                      String displayName, Path exportDirectory) {
        try {
            Files.createDirectories(exportDirectory);

            String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
            String filename = displayName + "_" + timestamp + BACKUP_FILE_EXTENSION;
            Path backupFile = exportDirectory.resolve(filename);

            BackupData backupData = new BackupData(
                displayName,
                seedPhrase.getMnemonic(),
                identity.getFingerprint(),
                Base64.getEncoder().encodeToString(identity.getPublicKey().getEncoded())
            );

            String json = backupData.toJson();
            Files.write(backupFile, json.getBytes(StandardCharsets.UTF_8));

            return backupFile;

        } catch (IOException e) {
            throw new IdentityException("Failed to export identity backup", e);
        }
    }

    public static Path exportIdentityToDesktop(LocalIdentity identity, SeedPhrase seedPhrase,
                                               String displayName) {
        Path desktopPath = getDesktopPath();
        return exportIdentity(identity, seedPhrase, displayName, desktopPath);
    }

    public static BackupData importIdentity(Path backupFile) {
        try {
            if (!Files.exists(backupFile)) {
                throw new IdentityException("Backup file does not exist: " + backupFile);
            }

            String json = Files.readString(backupFile, StandardCharsets.UTF_8);
            BackupData backupData = BackupData.fromJson(json);

            if (!"1.0".equals(backupData.version)) {
                throw new IdentityException("Unsupported backup version: " + backupData.version);
            }

            if (backupData.seedPhrase == null || backupData.seedPhrase.isBlank()) {
                throw new IdentityException("Backup file missing seed phrase");
            }

            if (!SeedPhrase.isValid(backupData.seedPhrase)) {
                throw new IdentityException("Invalid seed phrase in backup file");
            }

            return backupData;

        } catch (IOException e) {
            throw new IdentityException("Failed to read backup file", e);
        }
    }

    public static LocalIdentity restoreFromBackup(Path backupFile) {
        BackupData backupData = importIdentity(backupFile);
        SeedPhrase seedPhrase = SeedPhrase.fromWords(backupData.seedPhrase);
        return DeterministicIdentity.deriveFromSeedPhrase(seedPhrase);
    }

    public static LocalIdentity restoreFromSeedPhrase(String mnemonic) {
        SeedPhrase seedPhrase = SeedPhrase.fromWords(mnemonic);
        return DeterministicIdentity.deriveFromSeedPhrase(seedPhrase);
    }

    public static boolean isBackupFile(Path file) {
        return Files.isRegularFile(file) &&
               file.getFileName().toString().endsWith(BACKUP_FILE_EXTENSION);
    }

    private static Path getDesktopPath() {
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, "Desktop");
    }
}
