package com.ascenddevs.safetyapp.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PendingCheckinStoreTest {

    @Test
    void peekDoesNotRemove_takeDoes() {
        PendingCheckinStore store = new PendingCheckinStore();
        UUID id = UUID.randomUUID();
        store.put(id, new PendingCheckinStore.Pending(0.9, 12345L));

        assertTrue(store.peek(id).isPresent());
        assertTrue(store.peek(id).isPresent());
        assertEquals(12345L, store.take(id).orElseThrow().deadlineMs());
        assertTrue(store.peek(id).isEmpty());
    }

    @Test
    void onlyOneCallerCanTakeACheckin() {
        PendingCheckinStore store = new PendingCheckinStore();
        UUID id = UUID.randomUUID();
        store.put(id, new PendingCheckinStore.Pending(0.8, 1L));

        assertTrue(store.take(id).isPresent());
        assertTrue(store.take(id).isEmpty());
    }

    @Test
    void unknownSession_isEmpty() {
        PendingCheckinStore store = new PendingCheckinStore();
        assertTrue(store.peek(UUID.randomUUID()).isEmpty());
        assertTrue(store.take(UUID.randomUUID()).isEmpty());
    }
}
