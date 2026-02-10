/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.logging
 * Created by: Ashish Kushwaha on 10-02-2026 15:00
 * File: LogStorage.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class LogStorage {

    private static final int DEFAULT_MAX_ENTRIES = 10000;

    private final List<LogEntry> entries;
    private final List<Consumer<LogEntry>> listeners;
    private final int maxEntries;

    public LogStorage() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public LogStorage(int maxEntries) {
        this.maxEntries = maxEntries;
        this.entries = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public void add(LogEntry entry) {
        entries.add(entry);
        while (entries.size() > maxEntries) {
            entries.remove(0);
        }
        notifyListeners(entry);
    }

    public List<LogEntry> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<LogEntry> filter(Predicate<LogEntry> predicate) {
        return entries.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public List<LogEntry> filterByLevel(LogLevel minLevel) {
        return filter(e -> e.getLevel().isEnabled(minLevel));
    }

    public List<LogEntry> filterBySource(String source) {
        return filter(e -> e.getSource().equalsIgnoreCase(source));
    }

    public List<LogEntry> filterBySourceContains(String sourcePattern) {
        String pattern = sourcePattern.toLowerCase();
        return filter(e -> e.getSource().toLowerCase().contains(pattern));
    }

    public List<LogEntry> filterByMessage(String messagePattern) {
        String pattern = messagePattern.toLowerCase();
        return filter(e -> e.getMessage().toLowerCase().contains(pattern));
    }

    public List<LogEntry> search(String query, LogLevel minLevel, String sourceFilter) {
        String lowerQuery = query != null ? query.toLowerCase() : "";
        String lowerSource = sourceFilter != null ? sourceFilter.toLowerCase() : "";

        return entries.stream()
                .filter(e -> e.getLevel().isEnabled(minLevel))
                .filter(e -> lowerSource.isEmpty() || e.getSource().toLowerCase().contains(lowerSource))
                .filter(e -> lowerQuery.isEmpty() ||
                        e.getMessage().toLowerCase().contains(lowerQuery) ||
                        e.getSource().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public void addListener(Consumer<LogEntry> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<LogEntry> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(LogEntry entry) {
        for (Consumer<LogEntry> listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception ignored) {
            }
        }
    }

    public List<String> getUniqueSources() {
        return entries.stream()
                .map(LogEntry::getSource)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
