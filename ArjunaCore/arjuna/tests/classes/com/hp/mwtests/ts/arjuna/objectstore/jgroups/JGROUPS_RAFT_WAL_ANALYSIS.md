# JGroupsRaftSlots WAL Analysis

## Question
Should we add similar WAL changes (SlotJournal) to JGroupsRaftSlots like we did for JGroupsSlots?

## Answer
**No, we should NOT add SlotJournal WAL to JGroupsRaftSlots because it already has persistent WAL built-in.**

## Comparison: JGroupsSlots vs JGroupsRaftSlots

| Feature | JGroupsSlots | JGroupsRaftSlots |
|---------|-------------|------------------|
| **Underlying storage** | ReplCache (in-memory) | Raft ReplicatedStateMachine |
| **Persistence** | ❌ None (ephemeral) | ✅ FileBasedLog (persistent) |
| **WAL implementation** | Custom (SlotJournal + Artemis Journal) | Built-in (Raft FileBasedLog) |
| **WAL needed?** | ✅ Yes | ❌ No (already has it) |
| **Consistency** | Eventually consistent | Linearizable (strong) |
| **Recovery mechanism** | Custom WAL replay | Raft log replay |
| **Fsync configuration** | `walSyncWrites`, `walSyncDeletes` | `raftLogFsync` |

## How Raft's Built-in WAL Works

The Raft consensus algorithm inherently includes a persistent write-ahead log:

1. **Write request** → Raft leader receives write
2. **Leader logs** → Appends to persistent FileBasedLog on disk
3. **Replication** → Leader sends log entry to followers
4. **Follower logs** → Each follower appends to its own persistent log
5. **Commit** → Once majority has persisted, entry is committed
6. **Apply** → Committed entry is applied to state machine

On crash recovery:
- Node reads its Raft log from disk
- Replays all committed entries to rebuild state machine
- Rejoins cluster and catches up on any missed entries

**This IS a write-ahead log** - data is written to the log BEFORE being applied to the state machine.

## What JGroupsRaftSlots Already Has

From `JGroupsRaftSlots.java` documentation:

```java
/**
 * JGroups-Raft based implementation of {@link BackingSlots} providing strong consistency
 * and persistent write-ahead logging (WAL) for crash recovery.
 *
 * <p>Uses the Raft consensus algorithm for:
 * <ul>
 *   <li><b>Strong consistency</b>: Linearizable reads and writes</li>
 *   <li><b>Persistent WAL</b>: All committed data survives crashes via FileBasedLog</li>
 *   <li><b>Leader election</b>: Automatic failover on leader crash</li>
 *   <li><b>Split-brain protection</b>: Quorum-based operation prevents divergence</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>:
 * <ul>
 *   <li>Write latency: ~10-20ms (with fsync enabled)</li>
 *   <li>Read latency: ~0.1ms (local reads from state machine)</li>
 *   <li>Throughput: 100-200 ops/sec (with fsync), 1000+ ops/sec (without fsync)</li>
 * </ul>
 */
```

From `JGroupsStoreEnvironmentBean.java`, configuration already exists:

```java
// Raft-specific properties
private boolean raftLogFsync = true;  // Default: fsync enabled for durability
```

## Why Adding SlotJournal Would Be Wrong

Adding SlotJournal to JGroupsRaftSlots would create **double WAL logging**:

```
Write request
    ↓
Raft log (FileBasedLog)        ← Built-in persistent WAL
    ↓
State machine
    ↓
SlotJournal (Artemis Journal)  ← Redundant second WAL! ❌
    ↓
ReplCache
```

This would:
- **Double the write latency** (two fsyncs per write)
- **Double the storage** (same data in two logs)
- **Add complexity** (two logs to manage and recover from)
- **Create inconsistency risk** (what if logs diverge?)

## Current Status: raftLogFsync Not Applied

The `raftLogFsync` configuration property exists but is **not currently being applied** to the RAFT protocol.

**Location**: `JGroupsStoreEnvironmentBean.java:43`
```java
private boolean raftLogFsync = true;
```

**Usage**: Currently not used in `JGroupsRaftSlots.java` initialization.

The Raft log is configured via `jgroups-raft.xml`, which doesn't specify fsync behavior. The default FileBasedLog behavior would apply.

## Recommendation

**Do NOT add SlotJournal WAL to JGroupsRaftSlots.**

Instead, if crash recovery guarantees are important:

1. ✅ **Verify Raft log directory** - Ensure FileBasedLog uses correct `storeDir`
2. ✅ **Apply raftLogFsync setting** - Configure RAFT protocol to respect the fsync flag
3. ✅ **Add crash recovery tests** - Verify Raft log replay works correctly
4. ❌ **Don't add SlotJournal** - Redundant with built-in Raft log

## Next Steps

If needed, we should:
1. Check how to programmatically configure Raft FileBasedLog fsync setting
2. Apply the `raftLogFsync` configuration to the RAFT protocol during initialization
3. Add tests to verify Raft crash recovery (similar to JGroupsSlots WAL tests)

## Conclusion

JGroupsRaftSlots already has enterprise-grade persistent WAL via Raft's consensus algorithm. The SlotJournal WAL we added to JGroupsSlots is specifically for the non-Raft (ReplCache) implementation which lacks persistence. Adding it to JGroupsRaftSlots would be redundant and harmful.

---

**Document created**: 2026-06-22  
**Author**: Claude Code  
**Related**: JGROUPS_SLOTS_WAL_DESIGN.md
