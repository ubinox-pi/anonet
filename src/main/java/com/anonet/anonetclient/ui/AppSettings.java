/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.ui
 * Created by: Ashish Kushwaha on 10-02-2026 13:00
 * File: AppSettings.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.ui;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppSettings {

    private static final AnonetLogger LOG = AnonetLogger.get(AppSettings.class);

    private static final String SETTINGS_FILE = "settings.json";
    private final Path settingsPath;

    private boolean autoStartDht = true;
    private boolean enableRelay = true;
    private boolean enableOnion = false;
    private boolean showNotifications = true;
    private boolean firstRun = true;
    private int maxConnections = 5;
    private String theme = "Dark (Default)";
    private String downloadPath;
    private String displayName = "";

    public AppSettings() {
        Path anonetDir = Paths.get(System.getProperty("user.home"), ".anonet");
        this.settingsPath = anonetDir.resolve(SETTINGS_FILE);
        this.downloadPath = anonetDir.resolve("downloads").toString();
    }

    public void load() {
        if (!Files.exists(settingsPath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            parseJson(content.toString());
        } catch (IOException e) {
            LOG.warn("Failed to load settings from %s: %s", settingsPath, e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(settingsPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(settingsPath, StandardCharsets.UTF_8)) {
                writer.write(toJson());
            }
        } catch (IOException e) {
            LOG.warn("Failed to save settings to %s: %s", settingsPath, e.getMessage());
        }
    }

    private void parseJson(String json) {
        autoStartDht = extractBoolean(json, "autoStartDht", true);
        enableRelay = extractBoolean(json, "enableRelay", true);
        enableOnion = extractBoolean(json, "enableOnion", false);
        showNotifications = extractBoolean(json, "showNotifications", true);
        firstRun = extractBoolean(json, "firstRun", true);
        maxConnections = extractInt(json, "maxConnections", 5);
        theme = extractString(json, "theme", "Dark (Default)");
        downloadPath = extractString(json, "downloadPath", downloadPath);
        displayName = extractString(json, "displayName", "");
    }

    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"autoStartDht\": ").append(autoStartDht).append(",\n");
        sb.append("  \"enableRelay\": ").append(enableRelay).append(",\n");
        sb.append("  \"enableOnion\": ").append(enableOnion).append(",\n");
        sb.append("  \"showNotifications\": ").append(showNotifications).append(",\n");
        sb.append("  \"firstRun\": ").append(firstRun).append(",\n");
        sb.append("  \"maxConnections\": ").append(maxConnections).append(",\n");
        sb.append("  \"theme\": \"").append(escapeJson(theme)).append("\",\n");
        sb.append("  \"downloadPath\": \"").append(escapeJson(downloadPath)).append("\",\n");
        sb.append("  \"displayName\": \"").append(escapeJson(displayName)).append("\"\n");
        sb.append("}");
        return sb.toString();
    }

    private boolean extractBoolean(String json, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultValue;
        int colonIdx = json.indexOf(':', idx);
        if (colonIdx == -1) return defaultValue;
        String rest = json.substring(colonIdx + 1).trim();
        if (rest.startsWith("true")) return true;
        if (rest.startsWith("false")) return false;
        return defaultValue;
    }

    private int extractInt(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultValue;
        int colonIdx = json.indexOf(':', idx);
        if (colonIdx == -1) return defaultValue;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String extractString(String json, String key, String defaultValue) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultValue;
        int colonIdx = json.indexOf(':', idx);
        if (colonIdx == -1) return defaultValue;
        int startQuote = json.indexOf('"', colonIdx);
        if (startQuote == -1) return defaultValue;
        int endQuote = startQuote + 1;
        while (endQuote < json.length()) {
            char c = json.charAt(endQuote);
            if (c == '"' && json.charAt(endQuote - 1) != '\\') break;
            endQuote++;
        }
        return unescapeJson(json.substring(startQuote + 1, endQuote));
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String unescapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    public boolean isAutoStartDht() { return autoStartDht; }
    public void setAutoStartDht(boolean value) { this.autoStartDht = value; }

    public boolean isEnableRelay() { return enableRelay; }
    public void setEnableRelay(boolean value) { this.enableRelay = value; }

    public boolean isEnableOnion() { return enableOnion; }
    public void setEnableOnion(boolean value) { this.enableOnion = value; }

    public boolean isShowNotifications() { return showNotifications; }
    public void setShowNotifications(boolean value) { this.showNotifications = value; }

    public boolean isFirstRun() { return firstRun; }
    public void setFirstRun(boolean value) { this.firstRun = value; }

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int value) { this.maxConnections = value; }

    public String getTheme() { return theme; }
    public void setTheme(String value) { this.theme = value; }

    public String getDownloadPath() { return downloadPath; }
    public void setDownloadPath(String value) { this.downloadPath = value; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { this.displayName = value; }

    public SettingsDialog.Settings toDialogSettings() {
        return new SettingsDialog.Settings(
            autoStartDht, enableRelay, enableOnion, showNotifications,
            maxConnections, theme, Paths.get(downloadPath)
        );
    }

    public void fromDialogSettings(SettingsDialog.Settings settings) {
        this.autoStartDht = settings.isAutoStartDht();
        this.enableRelay = settings.isEnableRelay();
        this.enableOnion = settings.isEnableOnion();
        this.showNotifications = settings.isShowNotifications();
        this.maxConnections = settings.getMaxConnections();
        this.theme = settings.getTheme();
        this.downloadPath = settings.getDownloadPath().toString();
    }
}
