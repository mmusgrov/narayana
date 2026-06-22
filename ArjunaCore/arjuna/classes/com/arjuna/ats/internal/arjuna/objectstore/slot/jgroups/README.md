# JGroups Object Store Implementation

## Overview

The JGroups Object Store is an **experimental** distributed object store implementation for Narayana that uses JGroups ReplCache for replication across a cluster. It extends the slot-based object store architecture to provide cluster-wide visibility of transaction logs.

**Status**: Experimental - Not recommended for production use

## Architecture

### Components

1. **JGroupsSlots** (`JGroupsSlots.java`)
   - Implements `BackingSlots` interface
   - Manages an array of slot keys backed by JGroups ReplCache
   - Provides read/write/clear operations on cache entries
   - Each slot is identified by a unique `ByteArrayKey`

2. **JGroupsStoreEnvironmentBean** (`JGroupsStoreEnvironmentBean.java`)
   - Configuration bean extending `SlotStoreEnvironmentBean`
   - Configures ReplCache connection, cluster name, node identity
   - Manages cache lifecycle and replication settings

3. **ByteArrayKey** (`ByteArrayKey.java`)
   - Wrapper for byte[] that implements proper equals/hashCode
   - Used as cache keys in ReplCache

4. **JGroupsSlotKeyGenerator** (`JGroupsSlotKeyGenerator.java`)
   - Interface for generating unique slot keys
   - Default implementation uses `Uid`
   - `SharedSlotKeyGenerator` for cluster-wide consistent keys

### How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                    Transaction Manager                       │
└─────────────┬───────────────────────────────────────────────┘
              │ write_committed(uid, typeName, state)
              ▼
┌─────────────────────────────────────────────────────────────┐
│                   SlotStoreAdaptor                           │
│  • Maps (Uid, typeName) → SlotStoreKey                       │
│  • Manages slotIdIndex: SlotStoreKey → slot number           │
└─────────────┬───────────────────────────────────────────────┘
              │ write(slotId, data)
              ▼
┌─────────────────────────────────────────────────────────────┐
│                     JGroupsSlots                             │
│  • slots[slotId] = ByteArrayKey (unique per slot)            │
│  • cache.put(slots[slotId], data)                            │
└─────────────┬───────────────────────────────────────────────┘
              │ ReplCache operations
              ▼
┌─────────────────────────────────────────────────────────────┐
│                    JGroups ReplCache                         │
│  • Manages internal JChannel                                 │
│  • Replicates data across cluster members                    │
│  • L1 (local) + L2 (distributed) caching                     │
└─────────────────────────────────────────────────────────────┘
              │
              ▼
       JGroups Cluster
     (node1, node2, node3...)
```

### Data Flow

1. **Write Path**:
   - TM calls `write_committed(uid, type, state)`
   - SlotStoreAdaptor creates SlotStoreKey from (uid, type, status)
   - Allocates free slot, maps SlotStoreKey → slotId
   - Calls `JGroupsSlots.write(slotId, data)`
   - JGroupsSlots calls `cache.put(slots[slotId], data, replicationCount, timeout)`
   - ReplCache replicates to other cluster nodes

2. **Read Path**:
   - TM calls `read_committed(uid, type)`
   - SlotStoreAdaptor looks up slotId from SlotStoreKey
   - Calls `JGroupsSlots.read(slotId)`
   - JGroupsSlots calls `cache.get(slots[slotId])`
   - ReplCache retrieves from L1 cache or fetches from cluster

## Configuration

### Programmatic Configuration

```java
JGroupsStoreEnvironmentBean config = new JGroupsStoreEnvironmentBean();
config.setJGroupsConfigFileName("jgroups.xml");
config.setCacheName("transactionLogs");
config.setNodeAddress("node1");
config.setStoreDir("/var/narayana/store");
config.setReplicationCount((short) -1); // -1 = replicate to all nodes
config.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());

BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class)
    .setObjectStoreType(SlotStoreAdaptor.class.getName());
BeanPopulator.getDefaultInstance(SlotStoreEnvironmentBean.class)
    .setBackingSlotsClassName(JGroupsSlots.class.getName());
```

### Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `jGroupsConfigFileName` | JGroups XML configuration file | `jgroups-transport-config.xml` |
| `cacheName` | Cluster name for ReplCache | `defaultJGroupsCache` |
| `nodeAddress` | Unique identifier for this node | (required) |
| `storeDir` | Directory for persistent storage | (parent bean) |
| `replicationCount` | Replication factor (-1 = all nodes) | -1 |
| `numberOfSlots` | Maximum concurrent transactions | 256 |
| `bytesPerSlot` | Maximum size per transaction log | 10240 |
| `groupName` | Transaction group for partitioning | null |
| `slotKeyGeneratorClassName` | Key generator class | Uid-based |

### JGroups Configuration

For **testing** (localhost), use `jgroups.xml`:
```xml
<config xmlns="urn:org:jgroups">
    <SHARED_LOOPBACK/>
    <SHARED_LOOPBACK_PING/>
    <MERGE3 min_interval="1000" max_interval="5000"/>
    <FD_ALL3 timeout="3000" interval="1000"/>
    <VERIFY_SUSPECT2 timeout="1000"/>
    <pbcast.NAKACK2 use_mcast_xmit="false"/>
    <UNICAST3/>
    <pbcast.STABLE desired_avg_gossip="5000" max_bytes="1M"/>
    <pbcast.GMS print_local_addr="false" join_timeout="500"/>
    <UFC max_credits="4M" min_threshold="0.4"/>
    <MFC max_credits="4M" min_threshold="0.4"/>
    <FRAG4/>
</config>
```

For **production**, use appropriate discovery:
- TCPPING for known static hosts
- JDBC_PING for database-based discovery
- File-based or cloud-specific discovery protocols

## Cluster Deployment Considerations

### Requirements for CP (Consistency/Partition-tolerance)

The object store is used for **transaction recovery**, which requires **strict consistency**:

1. **Consensus Protocol**
   - Use JGroups-Raft or similar for consensus
   - Ensures all nodes agree on transaction state
   - Handles split-brain scenarios correctly

2. **Single Recovery Manager (HA Singleton)**
   - Only one recovery manager should be active
   - Use leader election to choose recovery coordinator
   - Automatic failover on leader failure

3. **Replication Count**
   - Set `replicationCount = -1` for full replication
   - Or use `replicationCount = N` for N-way replication
   - Balance between availability and performance

### Persistence

ReplCache is **in-memory** by default. For durability:

1. **Option 1: Persistent Backing Store**
   - Configure ReplCache with persistent L2 cache
   - Not currently implemented in bean configuration
   - Requires custom ReplCache setup

2. **Option 2: WAL with JGroups-Raft**
   - Use jgroups-raft for write-ahead logging
   - Provides both consensus and persistence
   - Recommended for production deployments

3. **Option 3: Hybrid Approach**
   - JGroups for replication
   - Local file store for persistence
   - Recovery manager reads from replicated cache first

## Limitations

### Current Limitations

1. **No Persistent Storage Integration**
   - ReplCache persistence not exposed in configuration
   - Data lost if all cluster nodes restart
   - Requires manual WAL/persistence setup

2. **Single JVM Recovery Store**
   - StoreManager is a JVM singleton
   - Cannot have multiple RecoveryStores in one JVM
   - Complicates testing of multi-node scenarios

3. **Slot Key Management**
   - Slot keys must be consistent across cluster
   - SharedSlotKeyGenerator required for cluster deployments
   - Changing slot count requires cluster restart

4. **No Split-Brain Protection**
   - ReplCache alone doesn't provide consensus
   - Requires JGroups-Raft or external coordination
   - Risk of inconsistency during network partitions

### Testing Limitations

1. **Multi-Node Testing**
   - Cannot easily test multiple nodes in single JUnit test
   - StoreManager singleton prevents multiple stores
   - Requires multi-JVM or multi-process testing

2. **View Change Timing**
   - Cluster formation takes time
   - View changes don't guarantee data migration complete
   - Tests must account for replication lag

3. **Discovery Configuration**
   - UDP multicast may not work in all environments
   - Localhost testing requires SHARED_LOOPBACK_PING
   - CI/CD environments may need special configuration

## Testing

### Unit Tests

**JGroupsSlotsTest** - Tests basic slot operations:
```bash
./build.sh test -pl :arjuna -Dtest=JGroupsSlotsTest
```

Tests:
- Cache initialization and configuration
- Write/read single entries
- Slot key generation

### Integration Tests

**JGroupsClusterTest** - Tests cluster functionality:
```bash
./build.sh test -pl :arjuna -Dtest=JGroupsClusterTest
```

Current status: Basic store operations work, multi-node replication testing requires multi-JVM framework.

### Manual Cluster Testing

For manual testing of actual cluster replication:

1. **Start Node 1**:
```bash
java -Dnode.id=1 -Dcluster.name=test-cluster \
     -Dstore.dir=/tmp/node1 \
     -cp ... YourTestApp
```

2. **Start Node 2**:
```bash
java -Dnode.id=2 -Dcluster.name=test-cluster \
     -Dstore.dir=/tmp/node2 \
     -cp ... YourTestApp
```

3. **Verify Replication**:
   - Write transaction on Node 1
   - Check visibility on Node 2
   - Stop Node 1
   - Verify Node 2 can recover transaction

## Performance Considerations

### Tuning Parameters

1. **ReplCache Timeouts**
   ```java
   cache.setCallTimeout(1500L);      // RPC timeout (ms)
   cache.setCachingTime(30000L);     // Entry TTL (ms), 0 = no timeout
   cache.setCachingTime(0L);         // disable L2 caching entirely otherwise nodes might read stale records
   ```

2. **Slot Count**
   - Set to expected max concurrent transactions
   - Too low: "too few slots" errors
   - Too high: wasted memory

3. **Replication Count**
   - `-1`: Full replication (safest, slower)
   - `1`: No replication (fastest, no HA)
   - `N`: N-way replication (balance)

### Monitoring

Monitor these metrics:
- Cluster view size
- Cache hit/miss ratio
- Replication lag
- Slot utilization
- GC pressure from cache entries

## Migration from Other Stores

### From File-Based Stores

1. Ensure cluster discovery configured
2. Set appropriate slot count
3. Configure persistent backing if needed
4. Test failover scenarios
5. Monitor replication lag

### Compatibility

- Transaction logs from other stores not compatible
- Must start with empty store
- Recovery must complete before migration

## Troubleshooting

### Common Issues

1. **"too few slots" error**
   - Increase `numberOfSlots` configuration
   - Review concurrent transaction count
   - Consider splitting into multiple clusters

2. **Nodes not discovering each other**
   - Check JGroups configuration file
   - Verify cluster name matches
   - Check network/firewall settings
   - For localhost: use SHARED_LOOPBACK_PING
   - For production: use TCPPING or JDBC_PING

3. **Data not replicating**
   - Verify cluster formation (`getClusterSize()`)
   - Check replication count setting
   - Review JGroups logs for errors
   - Ensure slot key generator is consistent

4. **ClassCastException in hash function**
   - Ensure ByteArrayKey used consistently
   - Check slot key generator implementation
   - Verify all nodes use same key generator

## Future Enhancements

Potential improvements:
- [ ] Integrate persistent L2 cache configuration
- [ ] Add JGroups-Raft support for consensus
- [ ] Expose split-brain detection/handling
- [ ] Add metrics/monitoring endpoints
- [ ] Improve slot key management
- [ ] Support for slot count changes without restart
- [ ] Better multi-JVM testing support

## References

- [JGroups Documentation](http://www.jgroups.org/manual/index.html)
- [ReplCache API](http://www.jgroups.org/javadoc/org/jgroups/blocks/ReplCache.html)
- [JGroups-Raft](https://github.com/jgroups-extras/jgroups-raft)
- [Narayana Slot Store Architecture](../SlotStore.md)
