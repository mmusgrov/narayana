/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.TwoPhaseOutcome;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsRaftSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsRaftStoreEnvironmentBean;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.View;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.raft.ELECTION;
import org.jgroups.protocols.raft.NO_DUPES;
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.protocols.raft.REDIRECT;
import org.jgroups.protocols.raft.Role;
import org.jgroups.raft.blocks.ReplicatedStateMachine;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Raft consensus behavior under network partitions and unreliable networks,
 * exercised through the {@link RecoveryStore} and {@link AtomicAction} APIs.
 * <p>
 * Channels are built programmatically via {@link Util#getTestStack} (which omits
 * VERIFY_SUSPECT, FD_ALL3, and MERGE3) so that injected suspicions are not
 * automatically reversed.
 * <p>
 * Three scenarios:
 * <ol>
 *   <li><b>Minority partition</b> (3 nodes, isolate 1): the 2-node majority retains
 *       quorum and can commit; the isolated node cannot.</li>
 *   <li><b>Split brain</b> (4 nodes, 2+2): quorum is 3, so neither half can commit.
 *       After merge the cluster recovers.</li>
 *   <li><b>Packet loss</b> (3 nodes, 30% drop rate): Raft's retry mechanism ensures
 *       all committed entries eventually replicate despite random message loss.</li>
 * </ol>
 */
public class JGroupsRaftPartitionTest extends JGroupsTestBase {

    private static final int RAFT_TIMEOUT_MS = 3000;
    private static final String TYPE_NAME =
            "StateManager/BasicAction/TwoPhaseCoordinator/AtomicAction";

    private final List<RaftNode> nodes = new ArrayList<>();

    /**
     * Prepare succeeds, commit fails — the transaction record stays in the store
     * (BasicAction.updateState sees a non-empty failedList and re-writes the log).
     */
    static class CrashInCommitRecord extends AbstractRecord {
        @Override public int topLevelPrepare()  { return TwoPhaseOutcome.PREPARE_OK; }
        @Override public int topLevelCommit()   { return TwoPhaseOutcome.FINISH_ERROR; }
        @Override public int topLevelAbort()    { return TwoPhaseOutcome.FINISH_OK; }
        @Override public int nestedPrepare()    { return 0; }
        @Override public int nestedCommit()     { return 0; }
        @Override public int nestedAbort()      { return 0; }
        @Override public int typeIs()           { return 0; }
        @Override public Object value()         { return null; }
        @Override public void setValue(Object o) {}
        @Override public void merge(AbstractRecord a) {}
        @Override public void alter(AbstractRecord a) {}
        @Override public boolean shouldAdd(AbstractRecord a)     { return true; }
        @Override public boolean shouldAlter(AbstractRecord a)   { return false; }
        @Override public boolean shouldMerge(AbstractRecord a)   { return false; }
        @Override public boolean shouldReplace(AbstractRecord a) { return false; }
    }

    /**
     * Wraps a Raft node: JChannel, ReplicatedStateMachine, and the environment bean
     * needed by {@link JGroupsRaftSlots} / {@link RecoveryStore}.
     */
    static class RaftNode {
        final String name;
        final String storeDir;
        final JGroupsRaftStoreEnvironmentBean config;
        final JGroupsRaftSlots slots;
        JChannel channel;
        ReplicatedStateMachine<Integer, byte[]> sm;

        RaftNode(String name, String storeDir) {
            this.name = name;
            this.storeDir = storeDir;
            this.config = new JGroupsRaftStoreEnvironmentBean();
            this.slots = new JGroupsRaftSlots();

            config.setStoreDir(storeDir);
            config.setNumberOfSlots(256);
            config.setRaftLogFsync(false);
            config.setRaftTimeout(RAFT_TIMEOUT_MS);
            config.setRaftElectionMaxInterval(10_000);
            config.setBackingSlots(slots);
        }

        void start(String clusterName, List<String> members) throws Exception {
            RAFT raft = new RAFT();
            raft.logDir(storeDir);
            raft.logUseFsync(false);
            raft.members(members);

            channel = new JChannel(Util.getTestStack(
                    new NO_DUPES(), new ELECTION(), raft, new REDIRECT()))
                    .name(name);

            sm = new ReplicatedStateMachine<>(channel);
            sm.raftId(name);
            sm.timeout(RAFT_TIMEOUT_MS);

            channel.connect(clusterName);

            config.setPreConfiguredChannel(channel);
            config.setPreConfiguredStateMachine(sm);
            config.setNodeAddress(name);
            config.setClusterName(clusterName);
            config.setCacheName(clusterName);
            config.setRaftMembers(String.join(",", members));
        }

        void stop() {
            if (channel != null) {
                try { channel.close(); } catch (Exception ignore) {}
            }
        }

        RAFT raft() {
            return channel.getProtocolStack().findProtocol(RAFT.class);
        }

        boolean isLeader() {
            return Role.Leader.name().equals(raft().role());
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        StoreManager.shutdown();
        for (RaftNode node : nodes) {
            node.stop();
        }
        nodes.clear();
        removeDirectory(STORE_DIR);
    }

    private RaftNode createAndStartNode(String name, String clusterName,
                                        List<String> members) throws Exception {
        String storeDir = STORE_DIR + "/partition-test/" + clusterName + "/" + name;
        RaftNode node = new RaftNode(name, storeDir);
        node.start(clusterName, members);
        nodes.add(node);
        return node;
    }

    private RaftNode findLeader() {
        return nodes.stream().filter(RaftNode::isLeader).findFirst()
                .orElseThrow(() -> new AssertionError("No leader found"));
    }

    private void awaitAllLeaders() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            boolean allHaveLeader = true;
            for (RaftNode n : nodes) {
                if (n.raft().leader() == null) {
                    allHaveLeader = false;
                    break;
                }
            }
            if (allHaveLeader) return;
            Thread.sleep(100);
        }
        fail("Leader election timed out after 30s");
    }

    // ── Partition / Merge helpers ──────────────────────────────────────

    private void partition(List<RaftNode> part1, List<RaftNode> part2) throws TimeoutException {
        List<List<RaftNode>> parts = List.of(part1, part2);
        for (List<RaftNode> p : parts) {
            List<Address> suspects = parts.stream()
                    .filter(other -> other != p)
                    .flatMap(List::stream)
                    .map(n -> n.channel.address())
                    .collect(Collectors.toList());
            for (RaftNode n : p) {
                n.channel.stack().getBottomProtocol()
                        .up(new Event(Event.SUSPECT, suspects));
            }
            Util.waitUntilAllChannelsHaveSameView(30_000, 1000,
                    p.stream().map(n -> n.channel).toArray(JChannel[]::new));
        }
    }

    private void mergePartitions(List<RaftNode> part1, List<RaftNode> part2) throws TimeoutException {
        RaftNode coord1 = findViewCoordinator(part1);
        RaftNode coord2 = findViewCoordinator(part2);
        Map<Address, View> views = Map.of(
                coord1.channel.address(), coord1.channel.view(),
                coord2.channel.address(), coord2.channel.view());
        coord1.channel.stack().getBottomProtocol().up(new Event(Event.MERGE, views));
        coord2.channel.stack().getBottomProtocol().up(new Event(Event.MERGE, views));

        GMS gms1 = coord1.channel.stack().findProtocol(GMS.class);
        GMS gms2 = coord2.channel.stack().findProtocol(GMS.class);
        Util.waitUntil(30_000, 1000, () -> !gms1.isMergeTaskRunning());
        Util.waitUntil(30_000, 1000, () -> !gms2.isMergeTaskRunning());
    }

    private RaftNode findViewCoordinator(List<RaftNode> partition) {
        Address coordAddr = partition.get(0).channel.view().getCoord();
        return partition.stream()
                .filter(n -> n.channel.address().equals(coordAddr))
                .findFirst().orElseThrow();
    }

    // ── RecoveryStore / AtomicAction helpers ───────────────────────────

    /**
     * Activate the {@link RecoveryStore} backed by the given node's
     * pre-configured Raft channel and state machine.
     */
    private RecoveryStore activateStore(RaftNode node) throws Throwable {
        arjPropertyManager.getCoreEnvironmentBean().setNodeIdentifier(node.name);
        resetAtomicActionRecoveryModule();
        return startRecoveryStore(node.config);
    }

    private Uid createInDoubtTransaction() {
        AtomicAction aa = new AtomicAction();
        aa.begin();
        aa.add(new CrashInCommitRecord());
        aa.add(new CrashInCommitRecord());
        aa.commit(true);
        return aa.getSavingUid();
    }

    // ── Tests ─────────────────────────────────────────────────────────

    /**
     * Minority partition: isolate 1 of 3 nodes.
     * <p>
     * The 2-node majority retains quorum (majority of 3 = 2) and can commit new
     * transactions via {@link RecoveryStore}. After the partition heals, the
     * isolated node catches up and the data is visible through its store.
     */
    @Test
    void testMinorityPartition() throws Throwable {
        String cluster = "raft-minority-" + System.currentTimeMillis();
        List<String> members = List.of("P1", "P2", "P3");

        createAndStartNode("P1", cluster, members);
        createAndStartNode("P2", cluster, members);
        createAndStartNode("P3", cluster, members);
        awaitAllLeaders();

        RaftNode leader = findLeader();
        List<RaftNode> followers = nodes.stream()
                .filter(n -> !n.isLeader()).collect(Collectors.toList());
        RaftNode isolated = followers.get(0);
        RaftNode remaining = followers.get(1);

        // Write initial data through RecoveryStore on the leader
        RecoveryStore rs = activateStore(leader);

        Uid uid1 = new Uid();
        OutputObjectState data1 = new OutputObjectState();
        data1.packString("before-partition");
        assertTrue(rs.write_committed(uid1, TYPE_NAME, data1),
                "Pre-partition write should succeed");

        // ── Partition: isolate one follower ──
        List<RaftNode> majority = List.of(leader, remaining);
        List<RaftNode> minority = List.of(isolated);
        partition(majority, minority);

        // Majority (2 nodes, quorum=2) can still commit via RecoveryStore
        Uid uid2 = new Uid();
        OutputObjectState data2 = new OutputObjectState();
        data2.packString("during-partition");
        assertTrue(rs.write_committed(uid2, TYPE_NAME, data2),
                "Majority-side write should succeed during partition");

        // ── Merge ──
        mergePartitions(majority, minority);
        Util.waitUntilAllChannelsHaveSameView(30_000, 1000,
                nodes.stream().map(n -> n.channel).toArray(JChannel[]::new));
        awaitAllLeaders();

        // Switch RecoveryStore to the isolated node and verify both records arrived
        RecoveryStore isolatedRs = activateStore(isolated);
        InputObjectState read1 = isolatedRs.read_committed(uid1, TYPE_NAME);
        assertNotNull(read1, "Isolated node should see pre-partition data after merge");
        assertEquals("before-partition", read1.unpackString());

        InputObjectState read2 = isolatedRs.read_committed(uid2, TYPE_NAME);
        assertNotNull(read2, "Isolated node should see data written during partition");
        assertEquals("during-partition", read2.unpackString());
    }

    /**
     * Split brain: 4-node cluster split into two halves of 2.
     * <p>
     * Quorum for 4 nodes is 3, so neither half can commit new entries via
     * {@link RecoveryStore}. After the partition heals, a leader is elected
     * and normal operation resumes.
     * <p>
     * An even-sized cluster is intentional — it creates a true split where neither
     * side has a majority.
     */
    @Test
    void testSplitBrain() throws Throwable {
        String cluster = "raft-split-" + System.currentTimeMillis();
        List<String> members = List.of("S1", "S2", "S3", "S4");

        createAndStartNode("S1", cluster, members);
        createAndStartNode("S2", cluster, members);
        createAndStartNode("S3", cluster, members);
        createAndStartNode("S4", cluster, members);
        awaitAllLeaders();

        RaftNode leader = findLeader();

        // Write initial data through RecoveryStore
        RecoveryStore rs = activateStore(leader);
        Uid uid1 = new Uid();
        OutputObjectState data1 = new OutputObjectState();
        data1.packString("before-split");
        assertTrue(rs.write_committed(uid1, TYPE_NAME, data1));

        // Split 2+2: leader + one follower vs the other two
        List<RaftNode> leaderSide = new ArrayList<>();
        List<RaftNode> otherSide = new ArrayList<>();
        leaderSide.add(leader);
        boolean addedExtra = false;
        for (RaftNode n : nodes) {
            if (n == leader) continue;
            if (!addedExtra) { leaderSide.add(n); addedExtra = true; }
            else             { otherSide.add(n); }
        }

        // ── Partition: 2+2 ──
        partition(leaderSide, otherSide);

        // Leader side: quorum=3, only 2 nodes → write cannot reach consensus
        Uid uid2 = new Uid();
        OutputObjectState data2 = new OutputObjectState();
        data2.packString("should-fail");
        assertThrows(ObjectStoreException.class,
                () -> rs.write_committed(uid2, TYPE_NAME, data2),
                "Leader-side write should fail: 2 nodes < quorum of 3");

        // ── Merge ──
        mergePartitions(leaderSide, otherSide);
        Util.waitUntilAllChannelsHaveSameView(30_000, 1000,
                nodes.stream().map(n -> n.channel).toArray(JChannel[]::new));
        awaitAllLeaders();

        // Cluster recovers: RecoveryStore writes succeed again
        Uid uid3 = new Uid();
        OutputObjectState data3 = new OutputObjectState();
        data3.packString("after-merge");
        assertTrue(rs.write_committed(uid3, TYPE_NAME, data3),
                "Write should succeed after merge restores quorum");

        assertNotNull(rs.read_committed(uid1, TYPE_NAME));
        assertNotNull(rs.read_committed(uid3, TYPE_NAME));
    }

    /**
     * Packet loss: 30% of outgoing messages are randomly dropped via the
     * {@link DISCARD} protocol. Raft's AppendEntries retry mechanism ensures
     * every committed write eventually replicates to all nodes.
     * <p>
     * Uses {@link AtomicAction} with {@link CrashInCommitRecord} to create
     * in-doubt transactions under packet loss, then verifies all records
     * replicated to a follower via {@link RecoveryStore}.
     */
    @Test
    void testPacketLoss() throws Throwable {
        String cluster = "raft-packetloss-" + System.currentTimeMillis();
        List<String> members = List.of("L1", "L2", "L3");

        createAndStartNode("L1", cluster, members);
        createAndStartNode("L2", cluster, members);
        createAndStartNode("L3", cluster, members);
        awaitAllLeaders();

        RaftNode leader = findLeader();
        RaftNode follower = nodes.stream()
                .filter(n -> !n.isLeader()).findFirst().orElseThrow();

        // Insert DISCARD protocol on every node: 30% of outgoing messages dropped
        List<DISCARD> discards = new ArrayList<>();
        for (RaftNode n : nodes) {
            DISCARD discard = new DISCARD();
            discard.setDownDiscardRate(0.3);
            n.channel.getProtocolStack().insertProtocol(
                    discard, ProtocolStack.Position.ABOVE,
                    n.channel.getProtocolStack().getTransport().getClass());
            discards.add(discard);
        }

        // Increase RSM timeout to accommodate retries under packet loss
        for (RaftNode n : nodes) {
            n.sm.timeout(10_000);
        }

        // Activate RecoveryStore on the leader
        RecoveryStore rs = activateStore(leader);

        // Create 10 in-doubt transactions under packet loss
        List<Uid> txnUids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Uid uid = createInDoubtTransaction();
            txnUids.add(uid);
        }
        assertEquals(10, txnUids.size(), "All 10 transactions should complete");

        // Verify all records are in the leader's RecoveryStore
        for (Uid uid : txnUids) {
            AtomicAction probe = new AtomicAction(uid);
            assertTrue(containsAtomicAction(rs, probe),
                    "Leader store should contain txn " + uid);
        }

        // Remove packet loss
        for (DISCARD d : discards) {
            d.setDownDiscardRate(0.0);
        }

        // Switch to a follower's RecoveryStore and verify all records replicated
        RecoveryStore followerRs = activateStore(follower);
        for (Uid uid : txnUids) {
            AtomicAction probe = new AtomicAction(uid);
            assertTrue(containsAtomicAction(followerRs, probe),
                    "Follower store should contain replicated txn " + uid);
        }
    }
}
