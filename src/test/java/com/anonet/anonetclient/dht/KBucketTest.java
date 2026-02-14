package com.anonet.anonetclient.dht;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KBucketTest {

    private KBucket bucket;

    @BeforeEach
    void setUp() {
        bucket = new KBucket(0);
    }

    @Test
    void addContactToBucket() {
        DhtContact contact = makeContact();
        Optional<DhtContact> evicted = bucket.addOrUpdate(contact);
        assertTrue(evicted.isEmpty());
        assertEquals(1, bucket.size());
    }

    @Test
    void addUpToK() {
        for (int i = 0; i < KBucket.K; i++) {
            Optional<DhtContact> evicted = bucket.addOrUpdate(makeContact());
            assertTrue(evicted.isEmpty());
        }
        assertEquals(KBucket.K, bucket.size());
        assertTrue(bucket.isFull());
    }

    @Test
    void overflowReturnsOldest() {
        DhtContact first = makeContact();
        bucket.addOrUpdate(first);
        for (int i = 1; i < KBucket.K; i++) {
            bucket.addOrUpdate(makeContact());
        }
        DhtContact overflow = makeContact();
        Optional<DhtContact> evicted = bucket.addOrUpdate(overflow);
        assertTrue(evicted.isPresent());
        assertEquals(first.getNodeId(), evicted.get().getNodeId());
    }

    @Test
    void updateExistingMovesToEnd() {
        DhtContact contact = makeContact();
        bucket.addOrUpdate(contact);
        DhtContact other = makeContact();
        bucket.addOrUpdate(other);

        bucket.addOrUpdate(contact);

        var contacts = bucket.getContacts();
        assertEquals(contact.getNodeId(), contacts.get(contacts.size() - 1).getNodeId());
    }

    @Test
    void removeContact() {
        DhtContact contact = makeContact();
        bucket.addOrUpdate(contact);
        assertTrue(bucket.remove(contact.getNodeId()));
        assertEquals(0, bucket.size());
    }

    @Test
    void removeNonexistent() {
        assertFalse(bucket.remove(NodeId.random()));
    }

    @Test
    void getContactFindsExisting() {
        DhtContact contact = makeContact();
        bucket.addOrUpdate(contact);
        assertTrue(bucket.getContact(contact.getNodeId()).isPresent());
    }

    @Test
    void getContactReturnsEmptyForMissing() {
        assertTrue(bucket.getContact(NodeId.random()).isEmpty());
    }

    @Test
    void emptyBucket() {
        assertTrue(bucket.isEmpty());
        assertFalse(bucket.isFull());
        assertEquals(0, bucket.size());
    }

    private DhtContact makeContact() {
        return new DhtContact(NodeId.random(), new InetSocketAddress("127.0.0.1", 51820));
    }
}
