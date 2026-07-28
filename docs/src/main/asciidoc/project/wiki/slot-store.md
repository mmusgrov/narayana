# SlotStore internals: how JGroupsSlotStore works

This document describes the internal architecture of the slot-based object store,
focusing on how the JGroupsSlotStore makes transaction log data available across
a cluster.

## Layer overview

The slot store is built from four layers, each with a single responsibility:

```
ObjectStoreAPI  (Narayana's standard persistence interface)
      │
      ▼
SlotStoreAdaptor   translates (Uid, typeName) into SlotStoreKey
      │
      ▼
SlotStore           manages the slot index, free list, and serialisation
      │
      ▼
BackingSlots        physical read/write/clear of numbered slots
 (interface)        (JGroupsSlots, DiskSlots, InfinispanSlots, …)
```

Every layer delegates downward and never reaches past its immediate neighbour.

## SlotStoreAdaptor

`SlotStoreAdaptor` implements `ObjectStoreAPI` — the interface that the rest of
Narayana uses for transaction log persistence.  Its only job is to translate the
`(Uid, typeName, stateStatus)` tuples that `ObjectStoreAPI` works with into
`SlotStoreKey` objects that `SlotStore` understands.

| ObjectStoreAPI method | What the adaptor does |
|-|-|
| `write_committed(uid, type, state)` | Creates `SlotStoreKey(uid, type, OS_COMMITTED)`, calls `store.write(key, state)` |
| `read_committed(uid, type)` | Creates the same key, calls `store.read(key)` |
| `remove_committed(uid, type)` | Creates the same key, calls `store.remove(key)` |
| `currentState(uid, type)` | Calls `store.contains(key)` — returns `OS_COMMITTED` or `OS_UNKNOWN` |
| `allObjUids(type, …)` | Calls `store.getMatchingKeys(templateKey)` and packs the matching UIDs |
| `allTypes(…)` | Calls `store.getKnownTypes()` and decomposes hierarchical type paths |

The adaptor reports `fullCommitNeeded() == false` — the slot store writes
directly to committed state.  Shadow/uncommitted operations are not supported.

## SlotStoreKey

A `SlotStoreKey` is an immutable triple of `(Uid, typeName, stateStatus)`.
The type name is normalised to always start with `/`.

When a slot is written, the key is serialised into the front of the byte array
before the actual `OutputObjectState` payload:

```
┌──────────────────────┬──────────────────────────┐
│  SlotStoreKey bytes  │  OutputObjectState bytes  │
│  (uid+type+status)   │  (application payload)    │
└──────────────────────┴──────────────────────────┘
```

This makes every slot **self-describing**: on startup the store can read each
slot, deserialise the key from the front, and rebuild its in-memory index
without any external metadata.

## SlotStore

`SlotStore` is the core of the design.  It manages three data structures:

| Field | Type | Purpose |
|-|-|-|
| `slotIdIndex` | `ConcurrentHashMap<SlotStoreKey, Integer>` | Maps each logical key to the slot number that holds its data |
| `freeList` | `ConcurrentLinkedDeque<Integer>` | Pool of slot numbers available for new writes |
| `slots` | `BackingSlots` | The physical storage backend |

### Startup recovery

The constructor scans every slot (0 … numberOfSlots − 1) by calling
`slots.read(i)`.  For each slot:

- **Empty/null** → the slot number goes on the `freeList`.
- **Has data** → the `SlotStoreKey` is deserialised from the front of the data,
  and `slotIdIndex.put(key, i)` rebuilds the index entry.

No external index file is needed — the slots themselves are the source of truth.

### Write flow

```
SlotStore.write(key, outputObjectState)
```

1. **Serialise** — pack the `SlotStoreKey` then the `OutputObjectState` into a
   single `byte[]`.  Reject if larger than `bytesPerSlot`.
2. **Allocate** — `freeList.poll()` to claim a fresh slot number.  If the free
   list is empty the store is full and the write returns `false`.
3. **Write** — `slots.write(slotId, data, syncWrites)`.  On failure the slot is
   recycled and the exception propagates.
4. **Index** — `slotIdIndex.put(key, slotId)`.  The return value reveals whether
   this is a rewrite (previous slot existed for the same key).
5. **Free old slot** — if this was a rewrite, `slots.clear(previousSlot)` then
   `freeList.add(previousSlot)`.

Writes always go to a **new slot**, never in-place.  The old slot is freed only
after the new slot is written and the index is updated.  This guarantees that on
crash, recovery finds at least one complete copy.

### Read flow

```
SlotStore.read(key)
```

1. **Lookup** — `slotIdIndex.get(key)` to obtain the slot number.
2. **Read** — `slots.read(slotId)` to get the raw bytes.
3. **Deserialise** — `SlotStoreKey.unpackFrom(buffer)` advances past the key
   portion, then `InputObjectState.unpackFrom(buffer)` reads the payload.

### Remove flow

```
SlotStore.remove(key)
```

1. **Unindex** — `slotIdIndex.remove(key)`.  If the key was not present, return
   `false`.
2. **Clear** — `slots.clear(slotId, syncDeletes)`.  On failure the slot is
   recycled and the exception propagates.
3. **Free** — `freeList.add(slotId)`.

The index is updated *before* the backing store is cleared.  If the process
crashes between these two steps, the startup recovery loop will find data still
in the slot, re-index it, and recovery will retry the remove.

### Slot recycling

When `slots.write()` or `slots.clear()` throws `IOException`, the failed slot
must be handled.  The `recycleFailedSlots` configuration controls the policy:

- **`true`** (default) — return the slot to the free list immediately.  Safe
  because all built-in `BackingSlots` implementations guard against
  indeterminate state (DiskSlots uses checksums, JGroupsRaftSlots blocks until
  majority commit, JGroupsSlots uses synchronous RPC, InfinispanSlotStore uses
  synchronous cache operations).
- **`false`** — attempt to clear the slot first.  If the clear succeeds the slot
  is recycled; if the clear also fails the slot is **quarantined** (dropped from
  circulation).  Quarantined slots remain unavailable until the store restarts.

## BackingSlots interface

`BackingSlots` is the SPI that physical storage backends implement:

```java
void init(SlotStoreEnvironmentBean config) throws IOException;
void write(int slot, byte[] data, boolean sync) throws IOException;
byte[] read(int slot) throws IOException;
void clear(int slot, boolean sync) throws IOException;
void stop() throws IOException;   // default no-op
```

Slot numbers range from 0 to `numberOfSlots − 1`.  All concurrency control and
key-to-slot mapping is handled by `SlotStore` above this interface — a
`BackingSlots` implementation only deals with numbered byte arrays.

## JGroupsSlots

`JGroupsSlots` implements `BackingSlots` using a JGroups `ReplCache` — a
distributed hash map that replicates entries across cluster nodes.

> **Note:** this implementation is experimental and not yet recommended for
> production systems.

### Two-level key system

There are two kinds of keys in the system.  `SlotStoreKey` (Uid + typeName +
stateStatus) is the logical key used by `SlotStore`'s `ConcurrentHashMap`.
`ByteArrayKey` is the physical key used in the JGroups `ReplCache`.  The
`slots[]` array bridges them:

```
slotIdIndex: SlotStoreKey ──► int (slot number)
slots[int]:  int ──────────► ByteArrayKey (cache key)
ReplCache:   ByteArrayKey ──► byte[] (serialised data)
```

The first two mappings are local to each node and the third is global,
ie. the same for all nodes. Therefore, provided the composite mapping,
SlotStoreKey -> ByteArrayKey, is the same for all nodes it is irrelevant
which slot is used to store the ByteArrayKey.

### Initialisation

1. Allocate `ByteArrayKey[] slots` with size `numberOfSlots`.
2. Obtain or create the `JGroupsSlotKeyGenerator`.  The default generator
   creates keys from `new Uid().getBytes()` (random, not deterministic).
   A custom deterministic generator can be plugged in via
   `JGroupsStoreEnvironmentBean.setSlotKeyGeneratorClassName()`.
3. Optionally start a write-ahead log (`SlotJournal`) for disk persistence.
4. Start the `ReplCache`.
5. Call `load(existingKeys)` to populate `slots[]` from keys already in the
   cache's L2 store, filling remaining positions with generated keys.
6. If the WAL is enabled, call `loadFromWAL()` to recover data not yet in the
   cache (skipping entries the cache already has from replication).

### Write

```java
void write(int slot, byte[] data, boolean sync)
```

1. If WAL is enabled, write to the journal first.
2. `cache.put(slots[slot], data, replicationCount, 0)` — the `0` timeout means
   "cache forever until explicitly removed."

The `sync` parameter is **not used** — replication behaviour is controlled by
the `ReplCache` configuration (synchronous RPC with `callTimeout`).

### Read

```java
byte[] read(int slot)
```

1. `cache.get(slots[slot])` — reads from the local L2 cache, falling back to
   remote nodes if needed.
2. If null and WAL is enabled, falls back to `journal.read(slot)`.

### Clear

```java
void clear(int slot, boolean sync)
```

1. If WAL is enabled, delete from the journal.
2. `cache.remove(key)` — broadcasts removal across the cluster.
3. `cache.getL2Cache().remove(key)` — also clears the local L2 cache, because
   `ReplCache.remove()` does not always clean it.

### How different nodes see the same data

The JGroups `ReplCache` provides cross-node visibility:

- **`replicationCount`** controls how many nodes receive each write.  The
  default of `-1` means full replication — every node gets a copy.
- **`cache.put()`** replicates the entry via synchronous RPC, bounded by
  `callTimeout` (default 1500 ms).
- **`cache.get()`** reads from the local L2 cache first, then from remote nodes
  if the key is not found locally.
- **`cache.remove()`** broadcasts the removal to all nodes that hold the entry.
- **`migrateData`** (default `true`) automatically re-replicates entries when
  nodes join or leave the cluster.

For data to be consistently visible across nodes, all nodes must use the same
`ByteArrayKey` for each slot number.  The default key generator creates random
keys per-node (different nodes will have different cache keys for the same slot
index).  To share slot data across nodes, configure a deterministic key
generator that produces the same key for a given slot index on every node (see
`SharedSlotKeyGenerator` for an example).

### Write-ahead log (WAL)

When `walEnabled` is `true`, JGroupsSlots writes to an Artemis-based journal
before writing to the cache.  This provides crash recovery for the in-memory
cache data.

During startup, `loadFromWAL()` iterates the journal's slot IDs and restores
data to the cache **only if the cache does not already have data for that key**
(replicated data wins over WAL data, since it may be more recent).

## JGroupsRaftSlots

`JGroupsRaftSlots` implements `BackingSlots` using JGroups Raft — a consensus
protocol that guarantees linearizable writes across the cluster.  Unlike
`JGroupsSlots`, which relies on `ReplCache` (best-effort replication), this
implementation ensures that a write only succeeds after a majority of nodes have
durably committed the operation.

> **Note:** this implementation is experimental and not yet recommended for
> production systems.

### How it differs from JGroupsSlots

| Aspect | JGroupsRaftSlots | JGroupsSlots |
|-|-|-|
| Underlying mechanism | JGroups-Raft `ReplicatedStateMachine` | JGroups `ReplCache` (consistent hashing) |
| Consistency model | Strong (linearizable writes) | Eventual consistency |
| Persistence | Raft's built-in `FileBasedLog` | Optional `SlotJournal` (Artemis journal) |
| Key type | `Integer` (slot index directly) | `ByteArrayKey` (via `JGroupsSlotKeyGenerator`) |
| Split-brain protection | Yes (quorum prevents divergence) | No |
| Leader election | Required — writes go through elected leader | Not applicable — any node can write |
| Minimum nodes | 3 recommended (1 possible for testing) | 1+ |

### Key type

`JGroupsRaftSlots` uses the slot number (`int`) directly as the key into the
`ReplicatedStateMachine<Integer, byte[]>`.  There is no `ByteArrayKey` layer and
no key generator — the integer slot index is the cache key on every node:

```
slotIdIndex: SlotStoreKey ──► int (slot number)
ReplicatedStateMachine:  int ──► byte[] (serialised data)
```

Because every node uses the same integer key, there is no need for a
deterministic key generator to share data across nodes.

### State machine

`JGroupsRaftSlots` uses JGroups-Raft's built-in `ReplicatedStateMachine<Integer,
byte[]>` — a replicated key-value map. All operations (`put`, `remove`) are applied to an internal
`HashMap` when Raft log entries are committed.  On restart, the Raft log is
replayed through this state machine to restore the full in-memory state.

### Initialisation

1. Create a `JChannel` from the XML config file and set the node name.
2. Locate the `RAFT` protocol in the JGroups protocol stack.
3. Configure the Raft log directory (`raft.logDir()`) and fsync behaviour
   (`raft.logUseFsync()`).
4. Set the membership list:
   - If `raftMembers` is provided (e.g. `"node1,node2,node3"`), set that static
     list.
   - If empty, start with an empty membership for dynamic join (see below).
5. Create a `ReplicatedStateMachine<Integer, byte[]>` with `allowDirtyReads(true)`.
6. Connect the channel to the cluster.
7. If the node starts as a Learner (dynamic mode), call `joinOrBootstrap()`.
8. Wait for leader election to complete.

### Write

```java
void write(int slotId, byte[] data, boolean sync)
```

1. `cache.put(slotId, data)` — proposes a Raft log entry.
2. The call **blocks** until a majority of cluster members have appended the
   entry to their Raft log (and optionally fsynced).
3. If this node is a follower, the `REDIRECT` protocol in the JGroups stack
   transparently forwards the write to the current leader.

The `sync` parameter is **not used** — Raft consensus provides its own
durability guarantee.

### Read

```java
byte[] read(int slotId)
```

1. `cache.get(slotId)` — reads directly from the local `ReplicatedStateMachine`.
2. Because `allowDirtyReads(true)` is set, reads do not go through Raft
   consensus.  This is safe because all writes are committed via consensus
   before returning, and the local state machine is populated from the
   persistent Raft log on startup.

### Clear

```java
void clear(int slotId, boolean sync)
```

1. `cache.remove(slotId)` — proposes a deletion as a Raft log entry.
2. Like `write()`, blocks until majority commit.  The `sync` parameter is not
   used.

### Persistence

`JGroupsRaftSlots` does **not** use the `SlotJournal` (Artemis-based WAL).
Instead it relies on Raft's own `FileBasedLog`, stored in the directory
configured by `storeDir`.  The Raft log records every committed operation and
is replayed on startup to rebuild the state machine.

Durability is controlled by `raftLogFsync`:
- **`true`** (default) — every log entry is fsynced to disk before the write
  returns.
- **`false`** — log entries are buffered; a crash may lose recently committed
  entries.

### Leader election and forwarding

Leader election is handled by the `ELECTION` protocol in the JGroups-Raft stack
using the standard Raft election algorithm with randomised timeouts.

Write forwarding is handled by the `REDIRECT` protocol.  When a follower
receives a write, `REDIRECT` transparently forwards it to the current leader.
This means any node can accept writes.

### Dynamic membership

When `raftMembers` is not configured, the node starts with an empty membership
list and uses dynamic join:

1. After connecting, the node waits briefly for a leader to appear.
2. **Leader found** — join via `REDIRECT.addServer(nodeName)`, which is itself a
   Raft log entry requiring leader consensus.  The joining node starts as a
   Learner, receives the log, and is promoted to Follower.
3. **No leader, node is alone** — bootstrap a new single-member cluster by
   reconnecting with `members = [nodeName]`.
4. **No leader, other nodes exist** — wait for the coordinator to bootstrap,
   then join via `addServer()`.

`addServer()` and `removeServer()` are also exposed for programmatic cluster
management.

### Configuration

`JGroupsRaftStoreEnvironmentBean` extends `JGroupsStoreEnvironmentBean` and
adds:

| Property | Default | Description |
|-|-|-|
| `raftLogFsync` | `true` | Whether to fsync Raft log writes to disk |
| `raftMembers` | (empty) | Comma-separated static member list; empty for dynamic mode |
| `raftTimeout` | `5000` ms | Timeout for Raft operations (majority ack) |
| `raftElectionMaxInterval` | `500` ms | Max time to wait for leader election |
