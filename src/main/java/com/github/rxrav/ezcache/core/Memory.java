package com.github.rxrav.ezcache.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Memory {

    /** Atomic snapshot of both maps returned by {@link #snapshotBoth()}. */
    public record MemorySnapshot(Map<String, ValueWrapper> memory, Map<String, ExpiryMetadata> expiry) {}

    /** Atomic value+expiry pair returned by {@link #getWithExpiry(String)}. */
    public record EntryWithExpiry(ValueWrapper value, ExpiryMetadata expiry) {}

    private final long allowedMemory;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    private Map<String, ValueWrapper> mainMemory;
    private Map<String, ExpiryMetadata> expiryMetadataRef;

    public Memory() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        Logger logger = LogManager.getLogger(Memory.class);
        logger.info("Runtime started with {} bytes of memory", maxMemory);

        allowedMemory = (long) (maxMemory * Constants.PERMITTED_MAIN_MEMORY_THRESHOLD);
        logger.info("Server allowed to store data till {} bytes of memory", allowedMemory);

        this.mainMemory = new HashMap<>();
        this.expiryMetadataRef = new HashMap<>();
    }

    public void setMainMemory(Map<String, ValueWrapper> mainMemory) {
        writeLock.lock();
        try {
            this.mainMemory = mainMemory != null ? mainMemory : new HashMap<>();
        } finally {
            writeLock.unlock();
        }
    }

    public void setExpiryMetadataRef(Map<String, ExpiryMetadata> expiryMetadataRef) {
        writeLock.lock();
        try {
            this.expiryMetadataRef = expiryMetadataRef != null ? expiryMetadataRef : new HashMap<>();
        } finally {
            writeLock.unlock();
        }
    }

    public void putData(String key, ValueWrapper value) throws UnsupportedEncodingException {
        writeLock.lock();
        try {
            if (isMemoryLeft()) {
                this.mainMemory.put(key, value);
            } else {
                throw new RuntimeException("Memory full!");
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void putExpiryData(String key, ExpiryMetadata expiryMetadata) {
        writeLock.lock();
        try {
            this.expiryMetadataRef.put(key, expiryMetadata);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Stores value and expiry metadata under a single write lock, preventing any
     * window where the key exists without its expiry metadata (partial-write race).
     */
    public void putDataWithExpiry(String key, ValueWrapper value, ExpiryMetadata expiryMetadata) throws UnsupportedEncodingException {
        writeLock.lock();
        try {
            if (!isMemoryLeft()) throw new RuntimeException("Memory full!");
            this.mainMemory.put(key, value);
            this.expiryMetadataRef.put(key, expiryMetadata);
        } finally {
            writeLock.unlock();
        }
    }

    public boolean has(String key) {
        readLock.lock();
        try {
            return this.mainMemory.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    public ValueWrapper get(String key) {
        readLock.lock();
        try {
            return this.mainMemory.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns value and expiry metadata under a single read lock, eliminating
     * the TOCTOU race between separate {@link #get} and {@link #getExpMd} calls.
     */
    public EntryWithExpiry getWithExpiry(String key) {
        readLock.lock();
        try {
            return new EntryWithExpiry(this.mainMemory.get(key), this.expiryMetadataRef.get(key));
        } finally {
            readLock.unlock();
        }
    }

    public ValueWrapper remove(String key) {
        writeLock.lock();
        try {
            this.expiryMetadataRef.remove(key);
            return this.mainMemory.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    /** Counts how many of the given keys exist under a single read lock. */
    public int hasMany(String[] keys) {
        readLock.lock();
        try {
            int count = 0;
            for (String key : keys) {
                if (this.mainMemory.containsKey(key)) count++;
            }
            return count;
        } finally {
            readLock.unlock();
        }
    }

    /** Removes all given keys under a single write lock and returns the number that existed. */
    public int removeMany(String[] keys) {
        writeLock.lock();
        try {
            int count = 0;
            for (String key : keys) {
                if (this.mainMemory.remove(key) != null) {
                    this.expiryMetadataRef.remove(key);
                    count++;
                }
            }
            return count;
        } finally {
            writeLock.unlock();
        }
    }

    public void fullFlush() {
        writeLock.lock();
        try {
            this.mainMemory.clear();
            this.expiryMetadataRef.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public ExpiryMetadata getExpMd(String key) {
        readLock.lock();
        try {
            return this.expiryMetadataRef.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns a consistent copy of both maps under a single read lock, ensuring
     * the snapshot is not split across concurrent writes (atomic snapshot).
     */
    public MemorySnapshot snapshotBoth() {
        readLock.lock();
        try {
            return new MemorySnapshot(new HashMap<>(this.mainMemory), new HashMap<>(this.expiryMetadataRef));
        } finally {
            readLock.unlock();
        }
    }

    /** Returns a defensive copy — never the live map reference. */
    public Map<String, ValueWrapper> getMainMemorySnapshot() {
        readLock.lock();
        try {
            return new HashMap<>(this.mainMemory);
        } finally {
            readLock.unlock();
        }
    }

    /** Returns a defensive copy — never the live map reference. */
    public Map<String, ExpiryMetadata> getExpiryMetadataRefSnapshot() {
        readLock.lock();
        try {
            return new HashMap<>(this.expiryMetadataRef);
        } finally {
            readLock.unlock();
        }
    }

    private boolean isMemoryLeft() {
        return this.allowedMemory > Runtime.getRuntime().freeMemory();
    }
}
