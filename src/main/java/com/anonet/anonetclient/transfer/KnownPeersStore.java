/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.transfer
Created by: Ashish Kushwaha on 11-02-2026 12:00
File: KnownPeersStore.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.transfer;

import com.anonet.anonetclient.logging.AnonetLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KnownPeersStore {

    private static final AnonetLogger LOG = AnonetLogger.get(KnownPeersStore.class);

    private final Path storePath;
    private final Map<String, PeerRecord> peers;

    public KnownPeersStore(Path anonetDir) {
        this.storePath = anonetDir.resolve("known_peers.json");
        this.peers = new ConcurrentHashMap<>();
    }

    public boolean isKnown(String fingerprint) {
        return peers.containsKey(fingerprint);
    }

    public void addPeer(String fingerprint) {
        long now = System.currentTimeMillis();
        PeerRecord existing = peers.get(fingerprint);
        if (existing != null) {
            peers.put(fingerprint, new PeerRecord(existing.firstSeen, now));
        } else {
            peers.put(fingerprint, new PeerRecord(now, now));
            LOG.info("New peer added to known peers: %s", fingerprint.substring(0, Math.min(8, fingerprint.length())));
        }
        save();
    }

    public void load() {
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            String json = Files.readString(storePath, StandardCharsets.UTF_8);
            parseJson(json);
            LOG.info("Loaded %d known peers", peers.size());
        } catch (IOException e) {
            LOG.warn("Failed to load known peers: %s", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(storePath.getParent());
            String json = toJson();
            Files.writeString(storePath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to save known peers: %s", e.getMessage());
        }
    }

    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, PeerRecord> entry : peers.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("  \"").append(entry.getKey()).append("\": {");
            sb.append("\"firstSeen\":").append(entry.getValue().firstSeen);
            sb.append(",\"lastSeen\":").append(entry.getValue().lastSeen);
            sb.append("}");
            i++;
        }
        sb.append("\n}");
        return sb.toString();
    }

    private void parseJson(String json) {
        peers.clear();
        String trimmed = json.trim();
        if (trimmed.length() < 3) return;

        trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        if (trimmed.isEmpty()) return;

        int pos = 0;
        while (pos < trimmed.length()) {
            int keyStart = trimmed.indexOf('"', pos);
            if (keyStart < 0) break;
            int keyEnd = trimmed.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String fingerprint = trimmed.substring(keyStart + 1, keyEnd);

            int objStart = trimmed.indexOf('{', keyEnd);
            if (objStart < 0) break;
            int objEnd = trimmed.indexOf('}', objStart);
            if (objEnd < 0) break;

            String obj = trimmed.substring(objStart + 1, objEnd);
            long firstSeen = extractLong(obj, "firstSeen");
            long lastSeen = extractLong(obj, "lastSeen");

            peers.put(fingerprint, new PeerRecord(firstSeen, lastSeen));
            pos = objEnd + 1;
        }
    }

    private long extractLong(String obj, String key) {
        int idx = obj.indexOf("\"" + key + "\"");
        if (idx < 0) return 0;
        int colonIdx = obj.indexOf(':', idx);
        if (colonIdx < 0) return 0;
        int end = obj.indexOf(',', colonIdx);
        if (end < 0) end = obj.length();
        String value = obj.substring(colonIdx + 1, end).trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record PeerRecord(long firstSeen, long lastSeen) {}
}
