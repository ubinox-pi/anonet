/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.contacts
 * Created by: Ashish Kushwaha on 10-02-2026 12:00
 * File: ContactExporter.java
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

public final class ContactExporter {

    private static final String EXPORT_HEADER = "ANONET_CONTACTS_V1";
    private static final String FIELD_SEPARATOR = "|";
    private static final String RECORD_SEPARATOR = "\n";

    public void exportToFile(List<Contact> contacts, Path outputPath) throws ContactException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(EXPORT_HEADER);
            writer.write(RECORD_SEPARATOR);
            writer.write(String.valueOf(contacts.size()));
            writer.write(RECORD_SEPARATOR);

            for (Contact contact : contacts) {
                StringBuilder line = new StringBuilder();
                line.append(encodeField(contact.getDisplayName())).append(FIELD_SEPARATOR);
                line.append(encodeField(contact.getUsername())).append(FIELD_SEPARATOR);
                line.append(encodeField(contact.getFingerprint())).append(FIELD_SEPARATOR);
                line.append(encodePublicKey(contact.getPublicKey())).append(FIELD_SEPARATOR);
                line.append(contact.getAddedAt()).append(FIELD_SEPARATOR);
                line.append(contact.getLastSeen()).append(FIELD_SEPARATOR);
                line.append(contact.isFavorite()).append(FIELD_SEPARATOR);
                line.append(encodeField(contact.getNotes()));
                writer.write(line.toString());
                writer.write(RECORD_SEPARATOR);
            }
        } catch (IOException e) {
            throw new ContactException("Failed to export contacts", e);
        }
    }

    public List<Contact> importFromFile(Path inputPath) throws ContactException {
        List<Contact> contacts = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.equals(EXPORT_HEADER)) {
                throw new ContactException("Invalid contacts file format");
            }

            String countLine = reader.readLine();
            if (countLine == null) {
                throw new ContactException("Invalid contacts file: missing count");
            }
            int count = Integer.parseInt(countLine.trim());

            for (int i = 0; i < count; i++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                Contact contact = parseContactLine(line);
                if (contact != null) {
                    contacts.add(contact);
                }
            }
        } catch (IOException e) {
            throw new ContactException("Failed to import contacts", e);
        } catch (NumberFormatException e) {
            throw new ContactException("Invalid contacts file: corrupt count", e);
        }

        return contacts;
    }

    private Contact parseContactLine(String line) throws ContactException {
        String[] fields = line.split("\\|", -1);
        if (fields.length < 8) {
            return null;
        }

        try {
            String displayName = decodeField(fields[0]);
            String username = decodeField(fields[1]);
            String fingerprint = decodeField(fields[2]);
            PublicKey publicKey = decodePublicKey(fields[3]);
            long addedAt = Long.parseLong(fields[4]);
            long lastSeen = Long.parseLong(fields[5]);
            boolean favorite = Boolean.parseBoolean(fields[6]);
            String notes = decodeField(fields[7]);

            return new Contact(displayName, username, fingerprint, publicKey, addedAt, lastSeen, favorite, notes);
        } catch (Exception e) {
            throw new ContactException("Failed to parse contact line", e);
        }
    }

    public String exportToString(Contact contact) {
        StringBuilder sb = new StringBuilder();
        sb.append(contact.getDisplayName()).append(" (");
        sb.append(contact.getUsername()).append(")\n");
        sb.append("Fingerprint: ").append(contact.getFingerprint()).append("\n");
        sb.append("Public Key: ").append(encodePublicKey(contact.getPublicKey())).append("\n");
        return sb.toString();
    }

    public String exportToShareableFormat(Contact contact) {
        StringBuilder sb = new StringBuilder();
        sb.append("anonet:contact:");
        sb.append(encodeField(contact.getUsername())).append(":");
        sb.append(contact.getFingerprint().replace(":", "")).append(":");
        sb.append(encodePublicKey(contact.getPublicKey()));
        return sb.toString();
    }

    public Contact importFromShareableFormat(String shareableString) throws ContactException {
        if (!shareableString.startsWith("anonet:contact:")) {
            throw new ContactException("Invalid shareable format");
        }

        String data = shareableString.substring("anonet:contact:".length());
        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            throw new ContactException("Incomplete shareable format");
        }

        try {
            String username = decodeField(parts[0]);
            String fingerprint = formatFingerprint(parts[1]);
            PublicKey publicKey = decodePublicKey(parts[2]);

            String displayName = username.contains("#") ? username.substring(0, username.indexOf('#')) : username;

            return new Contact(displayName, username, fingerprint, publicKey);
        } catch (Exception e) {
            throw new ContactException("Failed to parse shareable format", e);
        }
    }

    private String encodeField(String value) {
        if (value == null) return "";
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeField(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
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

    private String formatFingerprint(String raw) {
        if (raw.contains(":")) return raw;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i += 8) {
            if (i > 0) sb.append(":");
            int end = Math.min(i + 8, raw.length());
            sb.append(raw.substring(i, end));
        }
        return sb.toString();
    }
}
