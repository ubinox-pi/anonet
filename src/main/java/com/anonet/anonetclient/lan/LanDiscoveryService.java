/*
 * Copyright (c) 2026 Ramjee Prasad
 * Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
 * See the LICENSE file in the project root for full license information.
 *
 * Project: anonet-client
 * Package: com.anonet.anonetclient.lan
 * Created by: Ashish Kushwaha on 19-01-2026 21:50
 * File: LanDiscoveryService.java
 *
 * This source code is intended for educational and non-commercial purposes only.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *   - Attribution must be given to the original author.
 *   - The code must be shared under the same license.
 *   - Commercial use is strictly prohibited.
 *
 */

package com.anonet.anonetclient.lan;

import com.anonet.anonetclient.identity.LocalIdentity;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanDiscoveryService {

    private static final int CLEANUP_INTERVAL_MS = 5000;

    private final LocalIdentity localIdentity;
    private final Map<String, LanPeer> discoveredPeers;
    private final AtomicBoolean running;
    private final ScheduledExecutorService cleanupScheduler;

    private LanBroadcaster broadcaster;
    private LanListener listener;
    private Runnable onPeersChangedCallback;

    public LanDiscoveryService(LocalIdentity localIdentity) {
        this.localIdentity = localIdentity;
        this.discoveredPeers = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "LanDiscoveryCleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            String fingerprint = localIdentity.getFingerprint();

            broadcaster = new LanBroadcaster(fingerprint);
            listener = new LanListener(fingerprint, this::onPeerDiscovered);

            listener.start();
            broadcaster.start();

            cleanupScheduler.scheduleAtFixedRate(
                    this::cleanupStalePeers,
                    CLEANUP_INTERVAL_MS,
                    CLEANUP_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            cleanupScheduler.shutdown();
            try {
                cleanupScheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (broadcaster != null) {
                broadcaster.stop();
            }
            if (listener != null) {
                listener.stop();
            }

            discoveredPeers.clear();
        }
    }

    public void setOnPeersChangedCallback(Runnable callback) {
        this.onPeersChangedCallback = callback;
    }

    public List<LanPeer> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers.values());
    }

    public int getPeerCount() {
        return discoveredPeers.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    private void onPeerDiscovered(InetAddress address, String fingerprint) {
        LanPeer existingPeer = discoveredPeers.get(fingerprint);

        if (existingPeer != null) {
            existingPeer.updateLastSeen();
        } else {
            LanPeer newPeer = new LanPeer(address, fingerprint);
            discoveredPeers.put(fingerprint, newPeer);
            notifyPeersChanged();
        }
    }

    private void cleanupStalePeers() {
        Instant cutoffTime = Instant.now().minusMillis(LanDiscoveryProtocol.PEER_TIMEOUT_MS);
        boolean removed = discoveredPeers.entrySet().removeIf(
                entry -> entry.getValue().getLastSeen().isBefore(cutoffTime)
        );

        if (removed) {
            notifyPeersChanged();
        }
    }

    private void notifyPeersChanged() {
        if (onPeersChangedCallback != null) {
            onPeersChangedCallback.run();
        }
    }
}
