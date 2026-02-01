package com.anonet.anonetclient.discovery;

import com.anonet.anonetclient.identity.LocalIdentity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.discovery
 * Created by: Ashish Kushwaha on 19-01-2026 23:00
 * File: DiscoveryClient.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */
public final class DiscoveryClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_PORT = 51821;

    private final String serverUrl;
    private final LocalIdentity identity;
    private final HttpClient httpClient;
    private final ScheduledExecutorService heartbeatExecutor;

    private volatile String currentUsername;
    private volatile String currentPassword;
    private volatile boolean isOnline;
    private volatile ScheduledFuture<?> heartbeatTask;

    public DiscoveryClient(String serverUrl, LocalIdentity identity) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.identity = identity;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "anonet-discovery-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.isOnline = false;
    }

    public CompletableFuture<Void> register(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String publicKeyEncoded = Base64.getEncoder().encodeToString(identity.getPublicKey().getEncoded());

                String json = buildJsonObject(
                        "username", username,
                        "password", password,
                        "publicKeyFingerprint", identity.getFingerprint(),
                        "publicKeyEncoded", publicKeyEncoded,
                        "port", String.valueOf(DEFAULT_PORT)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/presence/register"))
                        .timeout(HTTP_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    this.currentUsername = username;
                    this.currentPassword = password;
                    this.isOnline = true;
                    startHeartbeat();
                    return null;
                } else {
                    String errorMessage = extractErrorMessage(response.body());
                    throw new DiscoveryException(errorMessage);
                }
            } catch (DiscoveryException e) {
                throw e;
            } catch (Exception e) {
                throw new DiscoveryException("Failed to register: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<Void> heartbeat() {
        return CompletableFuture.runAsync(() -> {
            if (!isOnline || currentUsername == null) {
                return;
            }

            try {
                String json = buildJsonObject(
                        "username", currentUsername,
                        "password", currentPassword
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/presence/heartbeat"))
                        .timeout(HTTP_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    System.err.println("Heartbeat failed: HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("Heartbeat error: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Optional<PeerInfo>> lookupByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/presence/lookup/username/" + username))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    PeerInfo peer = parsePeerInfo(response.body());
                    return Optional.ofNullable(peer);
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                } else {
                    throw new DiscoveryException("Lookup failed: HTTP " + response.statusCode());
                }
            } catch (DiscoveryException e) {
                throw e;
            } catch (Exception e) {
                throw new DiscoveryException("Lookup failed: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<Optional<PeerInfo>> lookupByFingerprint(String fingerprint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/presence/lookup/key/" + fingerprint))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    PeerInfo peer = parsePeerInfo(response.body());
                    return Optional.ofNullable(peer);
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                } else {
                    throw new DiscoveryException("Lookup failed: HTTP " + response.statusCode());
                }
            } catch (DiscoveryException e) {
                throw e;
            } catch (Exception e) {
                throw new DiscoveryException("Lookup failed: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<List<PeerInfo>> getOnlinePeers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/presence/online"))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parsePeerInfoList(response.body());
                } else {
                    throw new DiscoveryException("Failed to get online peers: HTTP " + response.statusCode());
                }
            } catch (DiscoveryException e) {
                throw e;
            } catch (Exception e) {
                throw new DiscoveryException("Failed to get online peers: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<Void> logout() {
        return CompletableFuture.runAsync(() -> {
            if (!isOnline || currentUsername == null) {
                return;
            }

            try {
                stopHeartbeat();

                String json = buildJsonObject(
                        "username", currentUsername,
                        "password", currentPassword
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/presence/logout"))
                        .timeout(HTTP_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                System.err.println("Logout error: " + e.getMessage());
            } finally {
                this.isOnline = false;
                this.currentUsername = null;
                this.currentPassword = null;
            }
        });
    }

    public void shutdown() {
        try {
            logout().get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        heartbeatExecutor.shutdownNow();
    }

    public boolean isOnline() {
        return isOnline;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(
                () -> heartbeat().join(),
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private String buildJsonObject(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(keyValues[i]).append("\":\"").append(escapeJson(keyValues[i + 1])).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractErrorMessage(String responseBody) {
        int messageStart = responseBody.indexOf("\"message\":\"");
        if (messageStart >= 0) {
            messageStart += 11;
            int messageEnd = responseBody.indexOf("\"", messageStart);
            if (messageEnd > messageStart) {
                return responseBody.substring(messageStart, messageEnd);
            }
        }

        int errorStart = responseBody.indexOf("\"error\":\"");
        if (errorStart >= 0) {
            errorStart += 9;
            int errorEnd = responseBody.indexOf("\"", errorStart);
            if (errorEnd > errorStart) {
                return responseBody.substring(errorStart, errorEnd);
            }
        }

        return "Server error";
    }

    private PeerInfo parsePeerInfo(String json) {
        String username = extractJsonString(json, "username");
        String fingerprint = extractJsonString(json, "publicKeyFingerprint");
        String publicKey = extractJsonString(json, "publicKeyEncoded");
        String ipAddress = extractJsonString(json, "ipAddress");
        String status = extractJsonString(json, "status");
        long lastSeen = extractJsonLong(json, "lastSeen");
        List<Integer> ports = extractJsonIntArray(json, "portCandidates");
        boolean canSend = extractJsonBoolean(json, "canSend");
        boolean canReceive = extractJsonBoolean(json, "canReceive");

        if (username == null || fingerprint == null) {
            return null;
        }

        return new PeerInfo(username, fingerprint, publicKey, ipAddress, ports, canSend, canReceive, status, lastSeen);
    }

    private List<PeerInfo> parsePeerInfoList(String json) {
        List<PeerInfo> peers = new ArrayList<>();

        if (json == null || json.isBlank() || json.equals("[]")) {
            return peers;
        }

        int depth = 0;
        int start = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String peerJson = json.substring(start, i + 1);
                    PeerInfo peer = parsePeerInfo(peerJson);
                    if (peer != null) {
                        peers.add(peer);
                    }
                    start = -1;
                }
            }
        }

        return peers;
    }

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            return null;
        }

        int valueStart = keyIndex + searchKey.length();
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) {
            return null;
        }

        if (json.charAt(valueStart) == '"') {
            valueStart++;
            int valueEnd = json.indexOf('"', valueStart);
            if (valueEnd > valueStart) {
                return json.substring(valueStart, valueEnd);
            }
        } else if (json.substring(valueStart).startsWith("null")) {
            return null;
        }

        return null;
    }

    private long extractJsonLong(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            return 0;
        }

        int valueStart = keyIndex + searchKey.length();
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        StringBuilder sb = new StringBuilder();
        while (valueStart < json.length()) {
            char c = json.charAt(valueStart);
            if (Character.isDigit(c) || c == '-') {
                sb.append(c);
                valueStart++;
            } else {
                break;
            }
        }

        try {
            return Long.parseLong(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean extractJsonBoolean(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            return false;
        }

        int valueStart = keyIndex + searchKey.length();
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        return json.substring(valueStart).startsWith("true");
    }

    private List<Integer> extractJsonIntArray(String json, String key) {
        List<Integer> result = new ArrayList<>();
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            result.add(DEFAULT_PORT);
            return result;
        }

        int arrayStart = json.indexOf('[', keyIndex);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            result.add(DEFAULT_PORT);
            return result;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        String[] parts = arrayContent.split(",");
        for (String part : parts) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        if (result.isEmpty()) {
            result.add(DEFAULT_PORT);
        }

        return result;
    }
}
