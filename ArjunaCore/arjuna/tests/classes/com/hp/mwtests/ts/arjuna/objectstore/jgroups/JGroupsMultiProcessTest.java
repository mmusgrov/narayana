/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Multi-process test for JGroupsSlots replication.
 * Spawns separate JVM processes, each running a RecoveryStore instance,
 * and verifies that data written in one process is visible in another.
 */
public class JGroupsMultiProcessTest {

    private static final String CLUSTER_NAME = "test-multi-process-" + System.currentTimeMillis();
    private static final String STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-multi-process-test";
    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    @Before
    public void setUp() throws IOException {
        // Clean up any existing store directory
        Path storePath = Paths.get(STORE_DIR);
        if (Files.exists(storePath)) {
            Files.walk(storePath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
        Files.createDirectories(storePath);
    }

    @After
    public void tearDown() throws IOException {
        // Clean up store directory
        Path storePath = Paths.get(STORE_DIR);
        if (Files.exists(storePath)) {
            Files.walk(storePath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    public void testWriteInOneProcessReadInAnother() throws Exception {
        Uid uid = new Uid();
        String typeName = "StateManager/multi-process-test";
        String testData = "test-data-from-node1";

        // Start node1, write data, and keep it running for 10 seconds
        System.out.println("Starting node1 to write data and wait...");
        Process node1 = startNodeProcess("node1", "write-and-wait", uid.stringForm(), typeName, testData, "10");

        // Give node1 time to start and write
        Thread.sleep(5000);

        // While node1 is still running, start node2 and read the data
        System.out.println("Starting node2 to read data while node1 is still running...");
        ProcessResult readResult = runNode("node2", "read", uid.stringForm(), typeName);
        assertTrue("Read should succeed", readResult.output.contains("READ_SUCCESS"));
        assertTrue("Read should return correct data", readResult.output.contains(testData));
        assertEquals("Read process should exit successfully", 0, readResult.exitCode);

        // Wait for node1 to finish
        ProcessResult node1Result = waitForProcess(node1);
        assertEquals("Node1 should exit successfully", 0, node1Result.exitCode);
    }

    @Test
    public void testMultipleProcessesConcurrentWrites() throws Exception {
        String typeName = "StateManager/concurrent-test";
        Uid uid1 = new Uid();
        Uid uid2 = new Uid();
        Uid uid3 = new Uid();
        String data1 = "data-from-node1";
        String data2 = "data-from-node2";
        String data3 = "data-from-node3";

        // Start nodes sequentially so each can see previous nodes' slot keys
        System.out.println("Starting node1...");
        Process p1 = startNodeProcess("node1", "write-and-wait", uid1.stringForm(), typeName, data1, "25");
        Thread.sleep(5000); // Let node1 establish cluster and write

        System.out.println("Starting node2...");
        Process p2 = startNodeProcess("node2", "write-and-wait", uid2.stringForm(), typeName, data2, "20");
        Thread.sleep(3000); // Let node2 join and write

        System.out.println("Starting node3...");
        Process p3 = startNodeProcess("node3", "write-and-wait", uid3.stringForm(), typeName, data3, "15");
        Thread.sleep(3000); // Let node3 join and write

        // While all 3 are still running, verify node4 can read all three writes
        System.out.println("Starting node4 to verify all writes while others are running...");
        ProcessResult read1 = runNode("node4", "read", uid1.stringForm(), typeName);
        assertTrue("Should read data1", read1.output.contains(data1));

        ProcessResult read2 = runNode("node4", "read", uid2.stringForm(), typeName);
        assertTrue("Should read data2", read2.output.contains(data2));

        ProcessResult read3 = runNode("node4", "read", uid3.stringForm(), typeName);
        assertTrue("Should read data3", read3.output.contains(data3));

        // Wait for all write processes to finish
        ProcessResult r1 = waitForProcess(p1);
        ProcessResult r2 = waitForProcess(p2);
        ProcessResult r3 = waitForProcess(p3);

        assertEquals("Node1 write should succeed", 0, r1.exitCode);
        assertEquals("Node2 write should succeed", 0, r2.exitCode);
        assertEquals("Node3 write should succeed", 0, r3.exitCode);
    }

    @Test
    public void testLongRunningProcesses() throws Exception {
        Uid uid = new Uid();
        String typeName = "StateManager/long-running-test";
        String data = "persistent-data";

        // Start node1, write data, and keep running
        System.out.println("Starting long-running node1...");
        Process node1 = startNodeProcess("node1", "write-and-wait", uid.stringForm(), typeName, data, "10");

        // Give node1 time to start, join cluster, and write
        Thread.sleep(5000);

        // While node1 is still running, start node2 to read the data
        System.out.println("Starting node2 to read replicated data while node1 is running...");
        ProcessResult r2 = runNode("node2", "read", uid.stringForm(), typeName);
        assertTrue("Node2 should see replicated data", r2.output.contains(data));

        // Wait for node1 to finish
        ProcessResult r1 = waitForProcess(node1);
        assertEquals("Node1 should succeed", 0, r1.exitCode);
    }

    private ProcessResult runNode(String nodeName, String operation, String... args) throws Exception {
        Process process = startNodeProcess(nodeName, operation, args);
        return waitForProcess(process);
    }

    private Process startNodeProcess(String nodeName, String operation, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + "/bin/java");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(JGroupsMultiProcessNode.class.getName());
        command.add(nodeName);
        command.add(CLUSTER_NAME);
        command.add(STORE_DIR); // Use shared store directory for all nodes
        command.add(operation);
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        System.out.println("Starting process: " + nodeName + " operation: " + operation);
        return pb.start();
    }

    private ProcessResult waitForProcess(Process process) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println("[Process] " + line);
            output.append(line).append("\n");
        }

        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Process timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds");
        }

        int exitCode = process.exitValue();
        return new ProcessResult(exitCode, output.toString());
    }

    private static class ProcessResult {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
