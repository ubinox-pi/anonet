/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 02-02-2026 12:25
 * File: Username.java
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

import java.util.regex.Pattern;

public record Username(String displayName, String discriminator) {

    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");
    private static final Pattern DISCRIMINATOR_PATTERN = Pattern.compile("^[A-F0-9]{8}$");
    private static final String SEPARATOR = "#";

    public Username {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be null or blank");
        }
        if (!DISPLAY_NAME_PATTERN.matcher(displayName).matches()) {
            throw new IllegalArgumentException("Display name must be 1-32 characters, alphanumeric, underscore, or dash");
        }
        if (discriminator == null || discriminator.isBlank()) {
            throw new IllegalArgumentException("Discriminator cannot be null or blank");
        }
        if (!DISCRIMINATOR_PATTERN.matcher(discriminator).matches()) {
            throw new IllegalArgumentException("Discriminator must be 8 uppercase hex characters");
        }
    }

    public static Username fromFingerprint(String displayName, String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            throw new IllegalArgumentException("Fingerprint too short for discriminator");
        }
        String discriminator = fingerprint.replaceAll("[^A-F0-9]", "").substring(0, 8).toUpperCase();
        return new Username(displayName, discriminator);
    }

    public static Username parse(String usernameString) {
        if (usernameString == null || usernameString.isBlank()) {
            throw new IllegalArgumentException("Username string cannot be null or blank");
        }

        int separatorIndex = usernameString.lastIndexOf(SEPARATOR);
        if (separatorIndex == -1 || separatorIndex == 0 || separatorIndex == usernameString.length() - 1) {
            throw new IllegalArgumentException("Invalid username format, expected: displayName#DISCRIMINATOR");
        }

        String displayName = usernameString.substring(0, separatorIndex);
        String discriminator = usernameString.substring(separatorIndex + 1);

        return new Username(displayName, discriminator);
    }

    public static boolean isValid(String usernameString) {
        try {
            parse(usernameString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDiscriminator() {
        return discriminator;
    }

    public String getFullUsername() {
        return displayName + SEPARATOR + discriminator;
    }

    @Override
    public String toString() {
        return getFullUsername();
    }

    public boolean matches(String input) {
        if (input == null) return false;
        String normalized = input.toLowerCase();
        return displayName.toLowerCase().contains(normalized) ||
               getFullUsername().toLowerCase().contains(normalized);
    }
}
