/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.contacts
 * Created by: Ashish Kushwaha on 10-02-2026 12:00
 * File: ContactStorage.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.contacts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ContactStorage {

    private static final String CONTACTS_FILE = "contacts.json";
    private final Path storagePath;

    public ContactStorage(Path anonetDir) {
        this.storagePath = anonetDir.resolve(CONTACTS_FILE);
    }

    public void save(List<Contact> contacts) throws ContactException {
        try {
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            for (int i = 0; i < contacts.size(); i++) {
                Contact c = contacts.get(i);
                json.append("  {\n");
                json.append("    \"displayName\": \"").append(escapeJson(c.getDisplayName())).append("\",\n");
                json.append("    \"username\": \"").append(escapeJson(c.getUsername())).append("\",\n");
                json.append("    \"fingerprint\": \"").append(escapeJson(c.getFingerprint())).append("\",\n");
                json.append("    \"publicKey\": \"").append(encodePublicKey(c.getPublicKey())).append("\",\n");
                json.append("    \"addedAt\": ").append(c.getAddedAt()).append(",\n");
                json.append("    \"lastSeen\": ").append(c.getLastSeen()).append(",\n");
                json.append("    \"favorite\": ").append(c.isFavorite()).append(",\n");
                json.append("    \"notes\": \"").append(escapeJson(c.getNotes())).append("\"\n");
                json.append("  }");
                if (i < contacts.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("]");

            try (BufferedWriter writer = Files.newBufferedWriter(storagePath, StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            throw new ContactException("Failed to save contacts", e);
        }
    }

    public List<Contact> load() throws ContactException {
        List<Contact> contacts = new ArrayList<>();
        if (!Files.exists(storagePath)) {
            return contacts;
        }

        try (BufferedReader reader = Files.newBufferedReader(storagePath, StandardCharsets.UTF_8)) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            return parseContacts(content.toString());
        } catch (IOException e) {
            throw new ContactException("Failed to load contacts", e);
        }
    }

    private List<Contact> parseContacts(String json) throws ContactException {
        List<Contact> contacts = new ArrayList<>();
        json = json.trim();
        if (json.isEmpty() || json.equals("[]")) {
            return contacts;
        }

        int pos = 0;
        while (pos < json.length()) {
            int objStart = json.indexOf('{', pos);
            if (objStart == -1) break;
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd == -1) break;

            String objJson = json.substring(objStart, objEnd + 1);
            Contact contact = parseContact(objJson);
            if (contact != null) {
                contacts.add(contact);
            }
            pos = objEnd + 1;
        }
        return contacts;
    }

    private int findMatchingBrace(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private Contact parseContact(String json) throws ContactException {
        try {
            String displayName = extractString(json, "displayName");
            String username = extractString(json, "username");
            String fingerprint = extractString(json, "fingerprint");
            String publicKeyStr = extractString(json, "publicKey");
            long addedAt = extractLong(json, "addedAt");
            long lastSeen = extractLong(json, "lastSeen");
            boolean favorite = extractBoolean(json, "favorite");
            String notes = extractString(json, "notes");

            PublicKey publicKey = decodePublicKey(publicKeyStr);
            return new Contact(displayName, username, fingerprint, publicKey, addedAt, lastSeen, favorite, notes);
        } catch (Exception e) {
            throw new ContactException("Failed to parse contact", e);
        }
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) return "";

        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex == -1) return "";

        int startQuote = json.indexOf('"', colonIndex);
        if (startQuote == -1) return "";

        int endQuote = startQuote + 1;
        while (endQuote < json.length()) {
            char c = json.charAt(endQuote);
            if (c == '"' && json.charAt(endQuote - 1) != '\\') {
                break;
            }
            endQuote++;
        }

        String value = json.substring(startQuote + 1, endQuote);
        return unescapeJson(value);
    }

    private long extractLong(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) return 0;

        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex == -1) return 0;

        int start = colonIndex + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }

        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean extractBoolean(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) return false;

        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex == -1) return false;

        String rest = json.substring(colonIndex + 1).trim();
        return rest.startsWith("true");
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private String encodePublicKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private PublicKey decodePublicKey(String encoded) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance("EC");
        return factory.generatePublic(spec);
    }

    public void delete() throws ContactException {
        try {
            Files.deleteIfExists(storagePath);
        } catch (IOException e) {
            throw new ContactException("Failed to delete contacts file", e);
        }
    }

    public boolean exists() {
        return Files.exists(storagePath);
    }

    public Path getStoragePath() {
        return storagePath;
    }
}
