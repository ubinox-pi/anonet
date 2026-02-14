/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.contacts
 * Created by: Ashish Kushwaha on 10-02-2026 12:00
 * File: ContactManager.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 */

package com.anonet.anonetclient.contacts;

import com.anonet.anonetclient.dht.DhtClient;
import com.anonet.anonetclient.dht.PeerAnnouncement;
import com.anonet.anonetclient.identity.AnonetId;
import com.anonet.anonetclient.identity.Username;
import com.anonet.anonetclient.logging.AnonetLogger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ContactManager {

    private static final AnonetLogger LOG = AnonetLogger.get(ContactManager.class);

    private final ContactStorage storage;
    private final List<Contact> contacts;
    private final List<Consumer<Contact>> addListeners;
    private final List<Consumer<Contact>> removeListeners;
    private final List<Consumer<Contact>> updateListeners;
    private DhtClient dhtClient;

    public ContactManager() {
        this(getDefaultAnonetDir());
    }

    public ContactManager(Path anonetDir) {
        this.storage = new ContactStorage(anonetDir);
        this.contacts = new CopyOnWriteArrayList<>();
        this.addListeners = new CopyOnWriteArrayList<>();
        this.removeListeners = new CopyOnWriteArrayList<>();
        this.updateListeners = new CopyOnWriteArrayList<>();
    }

    private static Path getDefaultAnonetDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".anonet");
    }

    public void setDhtClient(DhtClient dhtClient) {
        this.dhtClient = dhtClient;
    }

    public void load() throws ContactException {
        List<Contact> loaded = storage.load();
        contacts.clear();
        contacts.addAll(loaded);
        LOG.info("Loaded %d contacts", contacts.size());
    }

    public void save() throws ContactException {
        storage.save(new ArrayList<>(contacts));
        LOG.debug("Saved %d contacts", contacts.size());
    }

    public Contact addContact(String displayName, String username, String fingerprint, PublicKey publicKey) throws ContactException {
        Optional<Contact> existing = findByFingerprint(fingerprint);
        if (existing.isPresent()) {
            throw new ContactException("Contact with this fingerprint already exists");
        }

        Contact contact = new Contact(displayName, username, fingerprint, publicKey);
        contacts.add(contact);
        save();
        notifyAdd(contact);
        LOG.info("Added contact: %s (%s)", displayName, fingerprint.substring(0, 8));
        return contact;
    }

    public Contact addContactFromAnonetId(AnonetId anonetId, PublicKey publicKey) throws ContactException {
        String username = anonetId.username().toString();
        String displayName = anonetId.username().getDisplayName();
        String fingerprint = computeFingerprintFromKey(publicKey);
        return addContact(displayName, username, fingerprint, publicKey);
    }

    public Contact addContactFromDht(String usernameQuery) throws ContactException {
        if (dhtClient == null) {
            throw new ContactException("DHT client not available");
        }

        LOG.info("Looking up contact in DHT: %s", usernameQuery);
        try {
            var future = dhtClient.lookup(usernameQuery);
            var announcementOpt = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            if (announcementOpt.isEmpty()) {
                throw new ContactException("Peer not found in DHT: " + usernameQuery);
            }

            PeerAnnouncement announcement = announcementOpt.get();
            PublicKey publicKey = announcement.getPublicKey();
            String fingerprint = announcement.getFingerprint();
            String username = announcement.getUsername();
            String displayName = username.contains("#") ? username.substring(0, username.indexOf('#')) : username;

            return addContact(displayName, username, fingerprint, publicKey);
        } catch (Exception e) {
            LOG.error("Failed to find peer in DHT: %s", usernameQuery);
            throw new ContactException("Failed to find peer in DHT", e);
        }
    }

    public void removeContact(Contact contact) throws ContactException {
        if (contacts.remove(contact)) {
            save();
            notifyRemove(contact);
            LOG.info("Removed contact: %s", contact.getDisplayName());
        }
    }

    public void removeContactByFingerprint(String fingerprint) throws ContactException {
        Optional<Contact> contact = findByFingerprint(fingerprint);
        if (contact.isPresent()) {
            removeContact(contact.get());
        }
    }

    public void updateContact(Contact contact) throws ContactException {
        int index = contacts.indexOf(contact);
        if (index >= 0) {
            contacts.set(index, contact);
            save();
            notifyUpdate(contact);
        }
    }

    public void setFavorite(Contact contact, boolean favorite) throws ContactException {
        contact.setFavorite(favorite);
        updateContact(contact);
    }

    public void updateLastSeen(String fingerprint, long timestamp) throws ContactException {
        findByFingerprint(fingerprint).ifPresent(contact -> {
            contact.setLastSeen(timestamp);
            try {
                updateContact(contact);
            } catch (ContactException e) {
                // Ignore save errors for last seen updates
            }
        });
    }

    public Optional<Contact> findByFingerprint(String fingerprint) {
        return contacts.stream()
                .filter(c -> c.getFingerprint().equals(fingerprint))
                .findFirst();
    }

    public Optional<Contact> findByUsername(String username) {
        return contacts.stream()
                .filter(c -> c.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    public List<Contact> search(String query) {
        if (query == null || query.isBlank()) {
            return getAllContacts();
        }
        String lowerQuery = query.toLowerCase();
        return contacts.stream()
                .filter(c -> c.getDisplayName().toLowerCase().contains(lowerQuery) ||
                        c.getUsername().toLowerCase().contains(lowerQuery) ||
                        c.getFingerprint().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    public List<Contact> getAllContacts() {
        return Collections.unmodifiableList(new ArrayList<>(contacts));
    }

    public List<Contact> getFavorites() {
        return contacts.stream()
                .filter(Contact::isFavorite)
                .collect(Collectors.toList());
    }

    public List<Contact> getOnlineContacts() {
        return contacts.stream()
                .filter(Contact::isOnline)
                .collect(Collectors.toList());
    }

    public List<Contact> getSortedContacts() {
        return contacts.stream()
                .sorted(Comparator
                        .comparing(Contact::isFavorite).reversed()
                        .thenComparing(Contact::isOnline).reversed()
                        .thenComparing(Contact::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public int getContactCount() {
        return contacts.size();
    }

    public void refreshOnlineStatus() {
        if (dhtClient == null) {
            return;
        }

        for (Contact contact : contacts) {
            try {
                var future = dhtClient.lookup(contact.getUsername());
                var announcementOpt = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (announcementOpt.isPresent()) {
                    contact.setLastSeen(System.currentTimeMillis());
                }
            } catch (Exception e) {
                // Ignore lookup errors
            }
        }
    }

    public void onContactAdded(Consumer<Contact> listener) {
        addListeners.add(listener);
    }

    public void onContactRemoved(Consumer<Contact> listener) {
        removeListeners.add(listener);
    }

    public void onContactUpdated(Consumer<Contact> listener) {
        updateListeners.add(listener);
    }

    private void notifyAdd(Contact contact) {
        for (Consumer<Contact> listener : addListeners) {
            try {
                listener.accept(contact);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    private void notifyRemove(Contact contact) {
        for (Consumer<Contact> listener : removeListeners) {
            try {
                listener.accept(contact);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    private void notifyUpdate(Contact contact) {
        for (Consumer<Contact> listener : updateListeners) {
            try {
                listener.accept(contact);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    private String computeFingerprintFromKey(PublicKey publicKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                sb.append(String.format("%02X", hash[i]));
                if (i < hash.length - 1 && (i + 1) % 4 == 0) {
                    sb.append(":");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public void clear() throws ContactException {
        contacts.clear();
        save();
    }

    public void importContacts(List<Contact> newContacts) throws ContactException {
        int imported = 0;
        for (Contact contact : newContacts) {
            if (findByFingerprint(contact.getFingerprint()).isEmpty()) {
                contacts.add(contact);
                notifyAdd(contact);
                imported++;
            }
        }
        save();
        LOG.info("Imported %d new contacts (skipped %d duplicates)", imported, newContacts.size() - imported);
    }

    public List<Contact> exportContacts() {
        return new ArrayList<>(contacts);
    }
}
