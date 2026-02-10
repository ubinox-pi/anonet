/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.logging
 * Created by: Ashish Kushwaha on 10-02-2026 15:00
 * File: LogExporter.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LogExporter {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public void exportToFile(List<LogEntry> entries, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("ANONET Log Export");
            writer.newLine();
            writer.write("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.newLine();
            writer.write("Total Entries: " + entries.size());
            writer.newLine();
            writer.write("=".repeat(80));
            writer.newLine();
            writer.newLine();

            for (LogEntry entry : entries) {
                writer.write(entry.formatWithThread());
                writer.newLine();
            }
        }
    }

    public void exportCurrentLogs(Path outputPath) throws IOException {
        exportToFile(AnonetLogger.getStorage().getAll(), outputPath);
    }

    public Path exportToDefaultPath() throws IOException {
        String fileName = "anonet_log_" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".log";
        Path logsDir = Path.of(System.getProperty("user.home"), ".anonet", "logs");
        Files.createDirectories(logsDir);
        Path outputPath = logsDir.resolve(fileName);
        exportCurrentLogs(outputPath);
        return outputPath;
    }

    public String exportToString(List<LogEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("ANONET Log Export\n");
        sb.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("Total Entries: ").append(entries.size()).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        for (LogEntry entry : entries) {
            sb.append(entry.formatWithThread()).append("\n");
        }
        return sb.toString();
    }

    public void exportFiltered(LogLevel minLevel, String sourceFilter, Path outputPath) throws IOException {
        List<LogEntry> filtered = AnonetLogger.getStorage().search("", minLevel, sourceFilter);
        exportToFile(filtered, outputPath);
    }
}
