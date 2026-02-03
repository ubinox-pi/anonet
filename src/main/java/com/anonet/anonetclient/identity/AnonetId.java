/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.identity
 * Created by: Ashish Kushwaha on 02-02-2026 12:30
 * File: AnonetId.java
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

import java.security.PublicKey;
import java.util.Base64;
import java.util.regex.Pattern;

public record AnonetId(Username username, String fingerprint, PublicKey publicKey) {

    private static final String PREFIX = "anonet:";
    private static final Pattern ANONET_ID_PATTERN = Pattern.compile("^anonet:[a-zA-Z0-9_-]{1,32}#[A-F0-9]{8}$");

    public AnonetId {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Fingerprint cannot be null or blank");
        }
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key cannot be null");
        }
        if (!username.discriminator().equals(fingerprint.replaceAll("[^A-F0-9]", "").substring(0, 8))) {
            throw new IllegalArgumentException("Username discriminator must match fingerprint prefix");
        }
    }

    public static AnonetId fromLocalIdentity(LocalIdentity identity, String displayName) {
        Username username = Username.fromFingerprint(displayName, identity.getFingerprint());
        return new AnonetId(username, identity.getFingerprint(), identity.getPublicKey());
    }

    public static AnonetId parse(String anonetIdString) {
        if (anonetIdString == null || !anonetIdString.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid ANONET ID format");
        }

        String usernameString = anonetIdString.substring(PREFIX.length());
        if (!ANONET_ID_PATTERN.matcher(anonetIdString).matches()) {
            throw new IllegalArgumentException("Invalid ANONET ID format");
        }

        Username username = Username.parse(usernameString);
        return new AnonetId(username, "", null);
    }

    public static AnonetId parseWithKeys(String anonetIdString, String fingerprint, PublicKey publicKey) {
        AnonetId partial = parse(anonetIdString);
        return new AnonetId(partial.username(), fingerprint, publicKey);
    }

    public static boolean isValid(String anonetIdString) {
        try {
            parse(anonetIdString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String toIdString() {
        return PREFIX + username.getFullUsername();
    }

    public String toShareableLink() {
        return "anonet://add/" + username.getFullUsername();
    }

    public String getDisplayName() {
        return username.getDisplayName();
    }

    public String getDiscriminator() {
        return username.getDiscriminator();
    }

    public String getFullUsername() {
        return username.getFullUsername();
    }

    public String getPublicKeyBase64() {
        if (publicKey == null) return null;
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public boolean hasKeys() {
        return publicKey != null && fingerprint != null && !fingerprint.isBlank();
    }

    @Override
    public String toString() {
        return toIdString();
    }

    public boolean matches(String searchTerm) {
        if (searchTerm == null) return false;
        return username.matches(searchTerm) ||
               toIdString().toLowerCase().contains(searchTerm.toLowerCase());
    }
}
