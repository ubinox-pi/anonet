/*

Copyright (c) 2026 Ramjee Prasad
Licensed under a custom Non-Commercial, Attribution, Share-Alike License.
See the LICENSE file in the project root for full license information.
Project: anonet-client
Package: com.anonet.anonetclient.security
Created by: Ashish Kushwaha on 11-02-2026 12:00
File: RateLimiter.java
This source code is intended for educational and non-commercial purposes only.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Attribution must be given to the original author.
The code must be shared under the same license.
Commercial use is strictly prohibited.
*/

package com.anonet.anonetclient.security;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class RateLimiter {

    private final int maxTokens;
    private final int refillPerSecond;
    private final Map<InetAddress, TokenBucket> buckets;
    private final ScheduledExecutorService cleaner;

    public RateLimiter(int maxTokens, int refillPerSecond) {
        this.maxTokens = maxTokens;
        this.refillPerSecond = refillPerSecond;
        this.buckets = new ConcurrentHashMap<>();
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RateLimiter-Cleaner");
            t.setDaemon(true);
            return t;
        });
        this.cleaner.scheduleAtFixedRate(this::evictStale, 5, 5, TimeUnit.MINUTES);
    }

    public boolean tryAcquire(InetAddress addr) {
        TokenBucket bucket = buckets.computeIfAbsent(addr, _ -> new TokenBucket(maxTokens, refillPerSecond));
        return bucket.tryAcquire();
    }

    public void shutdown() {
        cleaner.shutdownNow();
    }

    private void evictStale() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > 5 * 60 * 1000);
    }

    private static final class TokenBucket {
        private final int maxTokens;
        private final int refillPerSecond;
        private double tokens;
        private long lastRefill;
        volatile long lastAccess;

        TokenBucket(int maxTokens, int refillPerSecond) {
            this.maxTokens = maxTokens;
            this.refillPerSecond = refillPerSecond;
            this.tokens = maxTokens;
            this.lastRefill = System.nanoTime();
            this.lastAccess = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire() {
            refill();
            lastAccess = System.currentTimeMillis();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefill) / 1_000_000_000.0;
            tokens = Math.min(maxTokens, tokens + elapsed * refillPerSecond);
            lastRefill = now;
        }
    }
}
