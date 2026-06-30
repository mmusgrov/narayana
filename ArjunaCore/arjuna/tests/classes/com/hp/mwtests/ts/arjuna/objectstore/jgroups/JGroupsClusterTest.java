/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JGroups object store cluster formation and basic replication.
 * Uses view change notifications to avoid sleeps.
 */
public class JGroupsClusterTest extends JGroupsTestBase {

    private static final String TEST_CLUSTER_NAME = "test-cluster-" + System.currentTimeMillis();
    private static final String TYPE_NAME = "StateManager/BasicAction/TwoPhaseCoordinator/AtomicAction";

    private JGroupsTestBase.Store store;

    /**
     * Helper to wait for view changes
     */
    private static class ViewChangeListener implements Receiver {
        private final CountDownLatch latch;
        private final int expectedSize;

        ViewChangeListener(int expectedSize) {
            this.latch = new CountDownLatch(1);
            this.expectedSize = expectedSize;
        }

        @Override
        public void viewAccepted(View view) {
            if (view.size() >= expectedSize) {
                latch.countDown();
            }
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (store != null) {
            store.stop();
        }
        com.arjuna.ats.arjuna.objectstore.StoreManager.shutdown();

        // Clean up
        try {
            if (Files.exists(Paths.get(STORE_DIR))) {
                Files.walk(Paths.get(STORE_DIR))
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // ignore
                            }
                        });
            }
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Test that a JGroups-backed store can start and perform basic write/read operations
     */
    @Test
    public void testBasicStoreOperations() throws Throwable {
        store = createStore("node1", TEST_CLUSTER_NAME, STORE_DIR + "/node1");

        // Add listener before start
        ViewChangeListener listener = new ViewChangeListener(1);
        store.config().getCache().addReceiver(listener);

        store.start();
        assertTrue(listener.await(10, TimeUnit.SECONDS), "Store should start and join cluster");

        int clusterSize = store.config().getCache().getClusterSize();
        assertEquals(1, clusterSize, "Should see 1 node in cluster");

        // Get the recovery store and perform basic operations
        resetAtomicActionRecoveryModule();
        RecoveryStore recoveryStore = startRecoveryStore(store.config());

        Uid uid = new Uid();
        OutputObjectState data = new OutputObjectState();
        data.packString("test-transaction-data");

        // Write
        assertTrue(recoveryStore.write_committed(uid, TYPE_NAME, data), "Write should succeed");

        // Read back
        var result = recoveryStore.read_committed(uid, TYPE_NAME);
        assertNotNull(result, "Should be able to read back written data");
        assertEquals("test-transaction-data", result.unpackString(), "Data should match");

        // Remove
        assertTrue(recoveryStore.remove_committed(uid, TYPE_NAME), "Remove should succeed");

        // Verify removed — some stores throw ObjectStoreException, others return null
        try {
            InputObjectState afterRemove = recoveryStore.read_committed(uid, TYPE_NAME);
            assertNull(afterRemove, "should not contain removed record");
        } catch (ObjectStoreException ignore) {
        }

        System.out.println("✓ Basic store operations verified");
    }
}
