/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.logging
 * Created by: Ashish Kushwaha on 10-02-2026 15:00
 * File: AnonetLogger.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnonetLogger {

    private static final LogStorage GLOBAL_STORAGE = new LogStorage();
    private static final Map<String, AnonetLogger> LOGGERS = new ConcurrentHashMap<>();
    private static volatile LogLevel globalMinLevel = LogLevel.DEBUG;
    private static volatile boolean consoleOutput = true;

    private final String name;
    private LogLevel minLevel;

    private AnonetLogger(String name) {
        this.name = name;
        this.minLevel = null;
    }

    public static AnonetLogger get(String name) {
        return LOGGERS.computeIfAbsent(name, AnonetLogger::new);
    }

    public static AnonetLogger get(Class<?> clazz) {
        return get(clazz.getSimpleName());
    }

    public static LogStorage getStorage() {
        return GLOBAL_STORAGE;
    }

    public static void setGlobalLevel(LogLevel level) {
        globalMinLevel = level;
    }

    public static LogLevel getGlobalLevel() {
        return globalMinLevel;
    }

    public static void setConsoleOutput(boolean enabled) {
        consoleOutput = enabled;
    }

    public void setLevel(LogLevel level) {
        this.minLevel = level;
    }

    public void trace(String message) {
        log(LogLevel.TRACE, message);
    }

    public void trace(String format, Object... args) {
        log(LogLevel.TRACE, String.format(format, args));
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public void debug(String format, Object... args) {
        log(LogLevel.DEBUG, String.format(format, args));
    }

    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void info(String format, Object... args) {
        log(LogLevel.INFO, String.format(format, args));
    }

    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public void warn(String format, Object... args) {
        log(LogLevel.WARN, String.format(format, args));
    }

    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public void error(String format, Object... args) {
        log(LogLevel.ERROR, String.format(format, args));
    }

    public void error(String message, Throwable throwable) {
        String fullMessage = message + " - " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        log(LogLevel.ERROR, fullMessage);
    }

    private void log(LogLevel level, String message) {
        LogLevel effectiveMinLevel = minLevel != null ? minLevel : globalMinLevel;
        if (!level.isEnabled(effectiveMinLevel)) {
            return;
        }

        LogEntry entry = new LogEntry(level, name, message);
        GLOBAL_STORAGE.add(entry);

        if (consoleOutput) {
            String formatted = entry.format();
            if (level == LogLevel.ERROR || level == LogLevel.WARN) {
                System.err.println(formatted);
            } else {
                System.out.println(formatted);
            }
        }
    }

    public boolean isTraceEnabled() {
        return isLevelEnabled(LogLevel.TRACE);
    }

    public boolean isDebugEnabled() {
        return isLevelEnabled(LogLevel.DEBUG);
    }

    public boolean isInfoEnabled() {
        return isLevelEnabled(LogLevel.INFO);
    }

    private boolean isLevelEnabled(LogLevel level) {
        LogLevel effectiveMinLevel = minLevel != null ? minLevel : globalMinLevel;
        return level.isEnabled(effectiveMinLevel);
    }

    public String getName() {
        return name;
    }
}
