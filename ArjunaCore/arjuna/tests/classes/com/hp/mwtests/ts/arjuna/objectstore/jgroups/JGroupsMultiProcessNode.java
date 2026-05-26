/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A standalone process that runs a JGroupsSlots-backed RecoveryStore.
 * Used for multi-process cluster testing via ProcessBuilder.
 *
 * Usage:
 *   java JGroupsMultiProcessNode <nodeName> <clusterName> <storeDir> <operation> [args...]
 *
 * Operations:
 *   write <uid> <typeName> <data>  - Write data to the store
 *   read <uid> <typeName>           - Read data from the store
 *   wait <seconds>                  - Wait for cluster to form
 */
public class JGroupsMultiProcessNode {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: JGroupsMultiProcessNode <nodeName> <clusterName> <storeDir> <operation> [args...]");
            System.exit(1);
        }

        String nodeName = args[0];
        String clusterName = args[1];
        String storeDir = args[2];
        String operation = args[3];

        try {
            // Set up the store
            RecoveryStore recoveryStore = setupStore(nodeName, clusterName, storeDir);

            // Execute the requested operation
            switch (operation) {
                case "write":
                    if (args.length < 7) {
                        System.err.println("write requires: <uid> <typeName> <data>");
                        System.exit(1);
                    }
                    String uid = args[4];
                    String typeName = args[5];
                    String data = args[6];
                    writeData(recoveryStore, uid, typeName, data);
                    System.out.println("Write complete");
                    break;

                case "write-and-wait":
                    if (args.length < 8) {
                        System.err.println("write-and-wait requires: <uid> <typeName> <data> <secondsToWait>");
                        System.exit(1);
                    }
                    uid = args[4];
                    typeName = args[5];
                    data = args[6];
                    int waitSeconds = Integer.parseInt(args[7]);
                    writeData(recoveryStore, uid, typeName, data);
                    System.out.println("Waiting " + waitSeconds + " seconds...");
                    Thread.sleep(waitSeconds * 1000L);
                    System.out.println("Wait complete");
                    break;

                case "read":
                    if (args.length < 6) {
                        System.err.println("read requires: <uid> <typeName>");
                        System.exit(1);
                    }
                    uid = args[4];
                    typeName = args[5];
                    readData(recoveryStore, uid, typeName);
                    break;

                case "wait":
                    if (args.length < 5) {
                        System.err.println("wait requires: <seconds>");
                        System.exit(1);
                    }
                    int seconds = Integer.parseInt(args[4]);
                    System.out.println("Waiting " + seconds + " seconds for cluster to form...");
                    Thread.sleep(seconds * 1000L);
                    System.out.println("Wait complete");
                    break;

                default:
                    System.err.println("Unknown operation: " + operation);
                    System.exit(1);
            }

            // Clean shutdown
            StoreManager.shutdown();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static RecoveryStore setupStore(String nodeName, String clusterName, String storeDir) throws Exception {
        System.out.println("Setting up store for node: " + nodeName + " in cluster: " + clusterName);

        // Create store directory
        Files.createDirectories(Paths.get(storeDir));

        // Configure environment beans
        SlotStoreEnvironmentBean slotStoreConfig = BeanPopulator.getDefaultInstance(SlotStoreEnvironmentBean.class);
        JGroupsStoreEnvironmentBean config = BeanPopulator.getDefaultInstance(JGroupsStoreEnvironmentBean.class);
        BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier(nodeName);
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreType(SlotStoreAdaptor.class.getName());

        var slots = new JGroupsSlots();

        slotStoreConfig.setBackingSlotsClassName(JGroupsSlots.class.getName());
        slotStoreConfig.setNumberOfSlots(256);
        slotStoreConfig.setStoreDir(storeDir);

        config.setNumberOfSlots(slotStoreConfig.getNumberOfSlots());
        config.setBytesPerSlot(slotStoreConfig.getBytesPerSlot());
        config.setStoreDir(storeDir);
        config.setSyncWrites(true);
        config.setSyncDeletes(true);
        config.setNodeAddress(nodeName);
        config.setGroupName(clusterName);
        config.setCacheName(clusterName);
        config.setBackingSlots(slots);
        config.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());

        slots.init(config);

        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).
                setObjectStoreType(SlotStoreAdaptor.class.getName());
        BeanPopulator.setBeanInstanceIfAbsent(JGroupsStoreEnvironmentBean.class.getName(), config);

        RecoveryStore recoveryStore = StoreManager.getRecoveryStore();
        System.out.println("Store initialized successfully");
        return recoveryStore;
    }

    private static void writeData(RecoveryStore store, String uidStr, String typeName, String data) throws Exception {
        Uid uid = new Uid(uidStr);
        OutputObjectState oos = new OutputObjectState();
        oos.packString(data);

        boolean success = store.write_committed(uid, typeName, oos);
        if (success) {
            System.out.println("WRITE_SUCCESS: " + uidStr + " " + typeName + " " + data);
        } else {
            System.err.println("WRITE_FAILED: " + uidStr + " " + typeName);
            System.exit(1);
        }
    }

    private static void readData(RecoveryStore store, String uidStr, String typeName) throws Exception {
        Uid uid = new Uid(uidStr);
        InputObjectState ios = store.read_committed(uid, typeName);

        if (ios != null) {
            String data = ios.unpackString();
            System.out.println("READ_SUCCESS: " + uidStr + " " + typeName + " " + data);
        } else {
            System.err.println("READ_FAILED: " + uidStr + " " + typeName + " (not found)");
            System.exit(1);
        }
    }
}
