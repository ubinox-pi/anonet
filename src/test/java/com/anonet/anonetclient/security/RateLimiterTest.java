package com.anonet.anonetclient.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @AfterEach
    void tearDown() {
        if (rateLimiter != null) {
            rateLimiter.shutdown();
        }
    }

    @Test
    void acquireWithinLimitSucceeds() throws UnknownHostException {
        rateLimiter = new RateLimiter(5, 1);
        InetAddress addr = InetAddress.getByName("192.168.1.1");
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(addr));
        }
    }

    @Test
    void exceedingLimitRejected() throws UnknownHostException {
        rateLimiter = new RateLimiter(3, 1);
        InetAddress addr = InetAddress.getByName("192.168.1.2");
        assertTrue(rateLimiter.tryAcquire(addr));
        assertTrue(rateLimiter.tryAcquire(addr));
        assertTrue(rateLimiter.tryAcquire(addr));
        assertFalse(rateLimiter.tryAcquire(addr));
    }

    @Test
    void differentAddressesIndependent() throws UnknownHostException {
        rateLimiter = new RateLimiter(2, 1);
        InetAddress addr1 = InetAddress.getByName("10.0.0.1");
        InetAddress addr2 = InetAddress.getByName("10.0.0.2");

        assertTrue(rateLimiter.tryAcquire(addr1));
        assertTrue(rateLimiter.tryAcquire(addr1));
        assertFalse(rateLimiter.tryAcquire(addr1));

        assertTrue(rateLimiter.tryAcquire(addr2));
        assertTrue(rateLimiter.tryAcquire(addr2));
    }

    @Test
    void refillAfterTime() throws Exception {
        rateLimiter = new RateLimiter(2, 100);
        InetAddress addr = InetAddress.getByName("192.168.1.3");
        assertTrue(rateLimiter.tryAcquire(addr));
        assertTrue(rateLimiter.tryAcquire(addr));
        assertFalse(rateLimiter.tryAcquire(addr));

        Thread.sleep(50);

        assertTrue(rateLimiter.tryAcquire(addr));
    }

    @Test
    void zeroTokensRejectsImmediately() throws UnknownHostException {
        rateLimiter = new RateLimiter(0, 1);
        InetAddress addr = InetAddress.getByName("192.168.1.4");
        assertFalse(rateLimiter.tryAcquire(addr));
    }
}
