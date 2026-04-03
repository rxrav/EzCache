# EzCache Java Concurrency Report

This document describes the eight concurrency and parallelism issues found in the Java implementation of EzCache, explains why each was a problem, and shows the old and new code side-by-side.

---

## Issue 1 — TOCTOU Race in `GET`

### Problem

`Get.execute` originally called `memoryRef.get(key)` and `memoryRef.getExpMd(key)` as two separate operations, each acquiring its own lock. Between the two calls another thread could delete the key (e.g. a concurrent `DEL`), causing the second call to return `null` for a key that was just seen to exist — or vice versa. This is a classic **Time-of-Check to Time-of-Use (TOCTOU) race**.

### Old Code

```java
// Get.java — two separate lock acquisitions
@Override
protected ValueWrapper execute(Memory memoryRef) {
    String key = super.getArgs()[0];
    ValueWrapper obj = memoryRef.get(key);                   // readLock → readUnlock
    ExpiryMetadata expiryMetadata = memoryRef.getExpMd(key); // readLock → readUnlock  ← window between the two
    boolean itHasExpired = (expiryMetadata != null) && hasExpired(expiryMetadata);
    ...
}
```

```java
// Memory.java — each method acquires its own lock (original used ConcurrentHashMap, no explicit lock)
public ValueWrapper get(String key) {
    return this.mainMemory.get(key);
}

public ExpiryMetadata getExpMd(String key) {
    return this.expiryMetadataRef.get(key);
}
```

### New Code

```java
// Memory.java — new atomic method
public EntryWithExpiry getWithExpiry(String key) {
    readLock.lock();
    try {
        return new EntryWithExpiry(this.mainMemory.get(key), this.expiryMetadataRef.get(key));
    } finally {
        readLock.unlock();
    }
}
```

```java
// Get.java — single lock for both lookups
@Override
protected ValueWrapper execute(Memory memoryRef) {
    String key = super.getArgs()[0];
    // Single read-lock for both value and expiry — eliminates TOCTOU race.
    Memory.EntryWithExpiry entry = memoryRef.getWithExpiry(key);
    ValueWrapper obj = entry.value();
    ExpiryMetadata expiryMetadata = entry.expiry();
    boolean itHasExpired = (expiryMetadata != null) && hasExpired(expiryMetadata);
    ...
}
```

---

## Issue 2 — Partial-Write Window in `SET`

### Problem

The `set()` helper stored a key's value and its expiry metadata in two separate write operations. Between `putData` and `putExpiryData` a concurrent `GET` could observe the key with a value but no expiry metadata — producing an incorrect "never expires" answer for a key that was supposed to have a TTL.

### Old Code

```java
// Set.java — two separate lock acquisitions
private static void set(Memory memoryRef, ValueType valType, String key, int iVal, String sVal, long timeout)
        throws UnsupportedEncodingException {
    if (valType == ValueType.NUMBER) {
        memoryRef.putData(key, new ValueWrapper(iVal, ValueType.NUMBER)); // writeLock → writeUnlock
    } else {
        memoryRef.putData(key, new ValueWrapper(sVal, ValueType.STRING)); // writeLock → writeUnlock
    }
    // ← window: key is visible without expiry metadata
    memoryRef.putExpiryData(key, new ExpiryMetadata(new Date().getTime(), timeout)); // writeLock → writeUnlock
}
```

```java
// Memory.java — each method acquires its own lock
public void putData(String key, ValueWrapper value) throws UnsupportedEncodingException {
    if (isMemoryLeft()) {
        this.mainMemory.put(key, value);
    } else {
        throw new RuntimeException("Memory full!");
    }
}

public void putExpiryData(String key, ExpiryMetadata expiryMetadata) {
    this.expiryMetadataRef.put(key, expiryMetadata);
}
```

### New Code

```java
// Memory.java — new atomic method
public void putDataWithExpiry(String key, ValueWrapper value, ExpiryMetadata expiryMetadata)
        throws UnsupportedEncodingException {
    writeLock.lock();
    try {
        if (!isMemoryLeft()) throw new RuntimeException("Memory full!");
        this.mainMemory.put(key, value);
        this.expiryMetadataRef.put(key, expiryMetadata);
    } finally {
        writeLock.unlock();
    }
}
```

```java
// Set.java — single lock for both writes
private static void set(Memory memoryRef, ValueType valType, String key, int iVal, String sVal, long timeout)
        throws UnsupportedEncodingException {
    // Single write-lock: prevents window where key exists without expiry metadata.
    ValueWrapper vw = (valType == ValueType.NUMBER)
            ? new ValueWrapper(iVal, ValueType.NUMBER)
            : new ValueWrapper(sVal, ValueType.STRING);
    memoryRef.putDataWithExpiry(key, vw, new ExpiryMetadata(new Date().getTime(), timeout));
}
```

---

## Issue 3 — N Lock Acquisitions in `EXISTS`

### Problem

`Exists.execute` iterated over the provided keys in a for-loop, calling `memoryRef.has(key)` per key. Every call acquired and released the read-lock individually. For a request with N keys this meant N lock round-trips — N acquisitions, N memory fence instructions, and N contention points with concurrent writers — all avoidable.

### Old Code

```java
// Exists.java — N read-locks for N keys
@Override
protected ValueWrapper execute(Memory memoryRef) {
    int i = 0;
    for (String key : super.getArgs()) {
        if (memoryRef.has(key)) ++i; // readLock → readUnlock on every iteration
    }
    return new ValueWrapper(i, ValueType.NUMBER);
}
```

```java
// Memory.java — single-key method
public boolean has(String key) {
    return this.mainMemory.containsKey(key);
}
```

### New Code

```java
// Memory.java — new bulk method: one lock for all keys
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
```

```java
// Exists.java — single read-lock for all keys
@Override
protected ValueWrapper execute(Memory memoryRef) {
    // Single read-lock for all keys — N times fewer lock acquisitions.
    return new ValueWrapper(memoryRef.hasMany(super.getArgs()), ValueType.NUMBER);
}
```

---

## Issue 4 — N Lock Acquisitions in `DEL`

### Problem

`Del.execute` called `memoryRef.remove(key)` per key in a for-loop, acquiring and releasing the write-lock on every iteration. As with `EXISTS`, this produced N unnecessary lock round-trips and N windows where other threads could be scheduled between deletions — meaning a concurrent `EXISTS` during a multi-key `DEL` could observe a partially deleted set.

### Old Code

```java
// Del.java — N write-locks for N keys
@Override
protected ValueWrapper execute(Memory memoryRef) {
    int i = 0;
    for (String key : super.getArgs()) {
        ValueWrapper obj = memoryRef.remove(key); // writeLock → writeUnlock on every iteration
        if (obj != null) ++i;
    }
    return new ValueWrapper(i, ValueType.NUMBER);
}
```

```java
// Memory.java — single-key method
public ValueWrapper remove(String key) {
    this.expiryMetadataRef.remove(key);
    return this.mainMemory.remove(key);
}
```

### New Code

```java
// Memory.java — new bulk method: one lock for all keys
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
```

```java
// Del.java — single write-lock for all keys
@Override
protected ValueWrapper execute(Memory memoryRef) {
    // Single write-lock for all keys — N times fewer lock acquisitions.
    return new ValueWrapper(memoryRef.removeMany(super.getArgs()), ValueType.NUMBER);
}
```

---

## Issue 5 — Split Snapshot and Serial JSON Marshaling in `SAVE`

### Problem

`Save.execute` called `getMainMemorySnapshot()` and `getExpiryMetadataRefSnapshot()` as two separate read operations, then marshaled the two results sequentially. This had two problems:

1. **Split snapshot**: A write arriving between the two `getXxxSnapshot()` calls could appear in one segment but not the other, producing an inconsistent backup.
2. **Serial marshal**: The two JSON conversions are completely independent — one operates on `ValueWrapper` entries and the other on `ExpiryMetadata` entries. There was no reason to wait for the first to finish before starting the second.

Additionally, both `getXxxSnapshot()` methods returned **live references to the backing maps**, not copies, so mutations during marshaling could corrupt the JSON output or cause `ConcurrentModificationException`.

### Old Code

```java
// Save.java — two sequential snapshots (live references!) then two sequential marshals
@Override
protected ValueWrapper execute(Memory memoryRef) {
    String memData;
    String expMetaData;
    ObjectMapper objectMapper = new ObjectMapper();

    try {
        memData = objectMapper.writeValueAsString(memoryRef.getMainMemorySnapshot());       // snapshot 1 (live ref)
        // ← window: a concurrent write changes state here
        expMetaData = objectMapper.writeValueAsString(memoryRef.getExpiryMetadataRefSnapshot()); // snapshot 2 (live ref)
        ...
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
    ...
}
```

```java
// Memory.java — original snapshot methods returned live references
public Map<String, ValueWrapper> getMainMemorySnapshot() {
    return this.mainMemory; // ← live reference, not a copy
}

public Map<String, ExpiryMetadata> getExpiryMetadataRefSnapshot() {
    return this.expiryMetadataRef; // ← live reference, not a copy
}
```

### New Code

```java
// Memory.java — atomic snapshot returning defensive copies
public MemorySnapshot snapshotBoth() {
    readLock.lock();
    try {
        return new MemorySnapshot(new HashMap<>(this.mainMemory), new HashMap<>(this.expiryMetadataRef));
    } finally {
        readLock.unlock();
    }
}

// getMainMemorySnapshot / getExpiryMetadataRefSnapshot also fixed to return copies:
public Map<String, ValueWrapper> getMainMemorySnapshot() {
    readLock.lock();
    try {
        return new HashMap<>(this.mainMemory); // defensive copy
    } finally {
        readLock.unlock();
    }
}
```

```java
// Save.java — single atomic snapshot, then parallel marshal
@Override
protected ValueWrapper execute(Memory memoryRef) {
    // Single read-lock captures both maps atomically — no split-snapshot race.
    Memory.MemorySnapshot snapshot = memoryRef.snapshotBoth();
    ObjectMapper objectMapper = new ObjectMapper();

    // Marshal both snapshots concurrently — they are independent byte conversions.
    CompletableFuture<String> memFuture = CompletableFuture.supplyAsync(() -> {
        try {
            return objectMapper.writeValueAsString(snapshot.memory());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    });
    CompletableFuture<String> expFuture = CompletableFuture.supplyAsync(() -> {
        try {
            return objectMapper.writeValueAsString(snapshot.expiry());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    });

    try {
        String memData = memFuture.get();
        String expMetaData = expFuture.get();
        ...
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
    } catch (ExecutionException | IOException e) {
        throw new RuntimeException(e);
    }
    ...
}
```

---

## Issue 6 — Serial JSON Unmarshaling in `restoreDb`

### Problem

On startup `restoreDb` read the backup file, split it on the separator, and then ran two `mapper.readValue()` calls back-to-back. The two segments (main memory and expiry metadata) are completely independent — there is no reason to wait for one to finish before starting the other. On a large backup file this added unnecessary latency before the server was ready to accept connections.

### Old Code

```java
// EzCacheServer.java — two sequential unmarshals
private void restoreDb() {
    ...
    String[] data = fileContentStr.split(Constants.SEPARATOR);
    ObjectMapper mapper = new ObjectMapper();
    this.memoryRef.setMainMemory(mapper.readValue(data[0], new TypeReference<>() {}));       // sequential
    this.memoryRef.setExpiryMetadataRef(mapper.readValue(data[1], new TypeReference<>() {})); // sequential, starts only after previous finishes
    ...
}
```

### New Code

```java
// EzCacheServer.java — parallel unmarshal with CompletableFuture
private void restoreDb() {
    ...
    String[] data = fileContentStr.split(Constants.SEPARATOR);
    ObjectMapper mapper = new ObjectMapper();

    // Unmarshal both maps concurrently — they are independent byte conversions.
    CompletableFuture<Map<String, ValueWrapper>> memFuture =
            CompletableFuture.supplyAsync(() -> {
                try {
                    return mapper.readValue(data[0], new TypeReference<>() {});
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    CompletableFuture<Map<String, ExpiryMetadata>> expFuture =
            CompletableFuture.supplyAsync(() -> {
                try {
                    return mapper.readValue(data[1], new TypeReference<>() {});
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

    try {
        this.memoryRef.setMainMemory(memFuture.get());
        this.memoryRef.setExpiryMetadataRef(expFuture.get());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
    } catch (ExecutionException e) {
        throw new RuntimeException(e);
    }
    ...
}
```

---

## Issue 7 — Serial Client Teardown in `registerShutdownHook`

### Problem

The shutdown hook iterated over all open client sockets and called `connectedClient.close()` one at a time in a loop. `Socket.close()` issues a system call (sends FIN/RST to the peer) and may block briefly waiting for the OS network stack. With many simultaneous clients this added teardown time equal to the **sum** of all close latencies rather than the **maximum**.

### Old Code

```java
// EzCacheServer.java — sequential socket close
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    ...
    for (Socket connectedClient : this.connectedClientList) {
        connectedClient.close(); // blocks until OS teardown — no parallelism
    }
    ...
}));
```

### New Code

```java
// EzCacheServer.java — concurrent socket close
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    ...
    // Close all client sockets concurrently — serial close is O(n) latency.
    List<CompletableFuture<Void>> closeFutures = new ArrayList<>();
    for (Socket connectedClient : this.connectedClientList) {
        closeFutures.add(CompletableFuture.runAsync(() -> {
            try {
                connectedClient.close();
            } catch (IOException e) {
                logger.error("Error closing client socket: {}", e.getMessage());
            }
        }));
    }
    CompletableFuture.allOf(closeFutures.toArray(new CompletableFuture[0])).join();
    ...
}));
```

---

## Issue 8 — Blocking Read→Write Loop in `EzCacheConnHandler`

### Problem

The original `handle()` method ran as a single thread that **read → processed → wrote → flushed** in strict sequence. While `writer.flush()` was blocked on the socket (waiting for the OS to drain the TCP send buffer), `reader.read()` was also blocked — no new command could be received until the previous response had been fully flushed to the network. This prevented the server from taking advantage of RESP2 **pipelining** (sending multiple commands without waiting for each reply), and meant that a slow or congested client could stall command processing entirely.

### Old Code

```java
// EzCacheConnHandler.java — one thread: read blocks on write, write blocks on read
public void handle() throws IOException {
    try (BufferedWriter writer = ...;
         BufferedReader reader = ...) {

        char[] incoming = new char[MAX_BUFFER_SIZE];
        int nosOfBytesRead;

        while ((nosOfBytesRead = reader.read(incoming)) > 0) { // blocks here
            ...
            Object cmdResp = new CommandHandler(...).handleCommand(builder.toString());
            writer.write(cmdResp.toString());
            writer.flush(); // blocks here — next read waits for flush to finish
        }
    }
}
```

### New Code

```java
// EzCacheConnHandler.java — reader thread + virtual writer thread + blocking queue
public void handle() throws IOException {
    // Unbounded queue: reader never blocks on put(), writer drains in order.
    // Optional.empty() is the drain sentinel — a type-safe alternative to a magic string.
    LinkedBlockingQueue<Optional<String>> responses = new LinkedBlockingQueue<>();
    CountDownLatch writerDone = new CountDownLatch(1);

    try (BufferedWriter writer = ...;
         BufferedReader reader = ...) {

        // Writer virtual thread: drains the queue and flushes each response
        // independently, so the reader can keep processing the next command
        // without waiting for I/O.
        Thread.ofVirtual().name("conn-writer").start(() -> {
            try {
                Optional<String> item;
                while ((item = responses.take()).isPresent()) {
                    writer.write(item.get());
                    writer.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                logger.error("Write error: {}", e.getMessage());
            } finally {
                writerDone.countDown();
            }
        });

        // Reader loop (current thread): parse commands and enqueue responses.
        char[] incoming = new char[MAX_BUFFER_SIZE];
        int nosOfBytesRead;

        try {
            while ((nosOfBytesRead = reader.read(incoming)) > 0) {
                ...
                String cmdResp = new CommandHandler(...).handleCommand(builder.toString());
                responses.put(Optional.of(cmdResp)); // never blocks — queue is unbounded
            }
        } catch (SocketException e) {
            logger.info("Client disconnection requested");
        }

        // Signal writer that no more responses are coming, then wait for it to flush.
        responses.put(Optional.empty());
        writerDone.await();
    }
}
```

The reader can now process the next command immediately after enqueuing its response, without waiting for the writer to finish flushing the previous response to the socket. `Optional.empty()` as a drain sentinel avoids any sentinel-string parsing: all valid RESP2 responses start with `+`, `-`, `:`, `$`, or `*` — never an empty `Optional`.

---

## Summary

| # | Location | Root Cause | Fix |
|---|----------|-----------|-----|
| 1 | `Get.java` | Two separate `readLock` calls — TOCTOU race | `getWithExpiry` — single `readLock` |
| 2 | `Set.java` | Two separate `writeLock` calls — partial-write window | `putDataWithExpiry` — single `writeLock` |
| 3 | `Exists.java` | N `readLock` calls for N keys | `hasMany` — single `readLock` |
| 4 | `Del.java` | N `writeLock` calls for N keys | `removeMany` — single `writeLock` |
| 5 | `Save.java` | Two separate snapshots returning live map references; serial JSON marshal | `snapshotBoth` (atomic copy) + parallel `CompletableFuture` marshal |
| 6 | `EzCacheServer.java` `restoreDb` | Two sequential JSON unmarshals | Parallel `CompletableFuture` unmarshal |
| 7 | `EzCacheServer.java` shutdown hook | Sequential `Socket.close()` loop — O(n) teardown | `CompletableFuture.allOf()` for concurrent close |
| 8 | `EzCacheConnHandler.java` | Single-thread read→write→flush loop | Reader thread + virtual writer thread + `LinkedBlockingQueue` + `CountDownLatch` |
