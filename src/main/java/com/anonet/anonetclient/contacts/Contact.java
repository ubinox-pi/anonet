/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.contacts
 * Created by: Ashish Kushwaha on 10-02-2026 12:00
 * File: Contact.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.contacts;

import java.security.PublicKey;
import java.util.Objects;

public final class Contact {

    private final String displayName;
    private final String username;
    private final String fingerprint;
    private final PublicKey publicKey;
    private final long addedAt;
    private long lastSeen;
    private boolean favorite;
    private String notes;

    public Contact(String displayName, String username, String fingerprint, PublicKey publicKey) {
        this(displayName, username, fingerprint, publicKey, System.currentTimeMillis(), 0, false, "");
    }

    public Contact(String displayName, String username, String fingerprint, PublicKey publicKey,
                   long addedAt, long lastSeen, boolean favorite, String notes) {
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey cannot be null");
        this.addedAt = addedAt;
        this.lastSeen = lastSeen;
        this.favorite = favorite;
        this.notes = notes != null ? notes : "";
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes != null ? notes : "";
    }

    public boolean isOnline() {
        long now = System.currentTimeMillis();
        long offlineThreshold = 5 * 60 * 1000;
        return (now - lastSeen) < offlineThreshold;
    }

    public String getDiscriminator() {
        if (username.contains("#")) {
            return username.substring(username.indexOf('#') + 1);
        }
        return fingerprint.length() >= 8 ? fingerprint.substring(0, 8).replace(":", "") : fingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return fingerprint.equals(contact.fingerprint);
    }

    @Override
    public int hashCode() {
        return fingerprint.hashCode();
    }

    @Override
    public String toString() {
        return displayName + " (" + username + ")";
    }
}
