/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.logging
 * Created by: Ashish Kushwaha on 10-02-2026 15:00
 * File: LogEntry.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LogEntry {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String source;
    private final String message;
    private final String threadName;

    public LogEntry(LogLevel level, String source, String message) {
        this.timestamp = LocalDateTime.now();
        this.level = level;
        this.source = source;
        this.message = message;
        this.threadName = Thread.currentThread().getName();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public String getThreadName() {
        return threadName;
    }

    public String format() {
        return String.format("[%s] [%-5s] [%s] %s",
            timestamp.format(FORMATTER),
            level.getLabel(),
            source,
            message);
    }

    public String formatWithThread() {
        return String.format("[%s] [%-5s] [%s] [%s] %s",
            timestamp.format(FORMATTER),
            level.getLabel(),
            threadName,
            source,
            message);
    }

    @Override
    public String toString() {
        return format();
    }
}
