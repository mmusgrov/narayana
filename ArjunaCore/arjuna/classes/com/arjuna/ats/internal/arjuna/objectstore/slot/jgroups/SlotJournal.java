/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups;

import com.arjuna.ats.arjuna.logging.tsLogger;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Write-Ahead Log for JGroupsSlots using Artemis Journal.
 * Provides crash recovery for slot data with efficient append-only logging.
 *
 * <p>Features:
 * <ul>
 *   <li>Append-only journal for fast writes</li>
 *   <li>Automatic compaction of old entries</li>
 *   <li>Crash recovery on restart</li>
 *   <li>Optional fsync for durability</li>
 * </ul>
 *
 * <p>Journal Format:
 * Each record contains: [slotId (4 bytes)] [data length (4 bytes)] [data (N bytes)]
 * @since 5.13.2
 */
public class SlotJournal {

    private final Journal journal;
    private final boolean syncWrites;
    private final boolean syncDeletes;
    private final AtomicLong nextRecordId = new AtomicLong(0);
    /*
     * protect against writes interleaving on the same slot (similarly for write and delete)
     * (note that journal I/O dominates so method-level synchronization adds negligible overhead)
     */
    private final Lock journalLock = new ReentrantLock();

    // Maps slotId to journal record ID for fast lookups
    private final Map<Integer, Long> slotToRecordId = new ConcurrentHashMap<>();

    // Maps slotId to data (in-memory cache loaded from journal)
    private final Map<Integer, byte[]> slotData = new ConcurrentHashMap<>();

    private static final byte RECORD_TYPE = 0x01;

    /**
     * Create a new SlotJournal.
     *
     * @param storeDir Directory for journal files
     * @param syncWrites If true, fsync after each write (slower but safer)
     * @param syncDeletes If true, fsync after each delete
     * @param bufferSize Buffer size in bytes for batching writes
     * @param bufferFlushesPerSecond How many times per second to flush the buffer
     * @throws IOException if journal cannot be created
     */
    public SlotJournal(String storeDir, boolean syncWrites, boolean syncDeletes,
                       int bufferSize, int bufferFlushesPerSecond) throws IOException {
        this.syncWrites = syncWrites;
        this.syncDeletes = syncDeletes;

        File storeDirFile = new File(storeDir);
        if (!storeDirFile.exists() && !storeDirFile.mkdirs()) {
            throw new IOException("Failed to create store directory: " + storeDir);
        }

        // Use NIO by default (AIO requires native library)
        // For production, could enable AIO if available
        // Buffer timeout calculation matches HornetqJournalStore
        int bufferTimeoutNanos = (int)(1000000000d / bufferFlushesPerSecond);

        SequentialFileFactory fileFactory = new NIOSequentialFileFactory(
            storeDirFile,
            true,                  // buffered - enables TimedBuffer for write batching
            bufferSize,            // buffer size (configurable, default 490KB)
            bufferTimeoutNanos,    // buffer timeout in nanos (calculated from flushes/sec)
            1,                     // maxIO (NIO ignores this)
            false                  // logRates
        );

        // Create journal with reasonable defaults
        journal = new JournalImpl(
            10 * 1024 * 1024,  // fileSize: 10MB per file
            2,                  // minFiles: keep at least 2 files
            0,                  // poolSize: 0 = no pool
            0,                  // compactMinFiles: 0 = no automatic compaction
            0,                  // compactPercentage: 0 = disabled
            fileFactory,
            "slot-journal",     // filePrefix
            "dat",              // fileExtension
            1                   // maxAIO (ignored for NIO)
        );

        // optimize record updates (in-place replace instead of append+delete) otherwise every slot overwrite results in
        // faster journal growth and more compaction overhead.
        journal.replaceableRecord(RECORD_TYPE);
        journal.setRemoveExtraFilesOnLoad(true);
    }

    /**
     * Start the journal and load existing records.
     *
     * @throws Exception if journal cannot be started
     */
    public void start() throws Exception {
        journal.start();

        List<RecordInfo> committedRecords = new LinkedList<>();
        List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();
        TransactionFailureCallback failureCallback = (txId, records, recordsToDelete) -> {
            tsLogger.logger.warn("SlotJournal: Transaction " + txId + " failed to load");
        };

        // Load journal and replay records
        JournalLoadInformation loadInfo = journal.load(committedRecords, preparedTransactions, failureCallback);
        nextRecordId.set(loadInfo.getMaxID() + 1);

        if (!preparedTransactions.isEmpty()) {
            tsLogger.logger.warn("SlotJournal: Found " + preparedTransactions.size() +
                " prepared transactions, ignoring (slots don't use tx)");
        }

        // Replay records into memory
        for (RecordInfo record : committedRecords) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(record.data);
                int slotId = buffer.getInt();
                int dataLength = buffer.getInt();
                byte[] data = new byte[dataLength];
                buffer.get(data);

                slotData.put(slotId, data);
                slotToRecordId.put(slotId, record.id);
            } catch (Exception e) {
                tsLogger.logger.warn("SlotJournal: Failed to load record " + record.id + ": " + e.getMessage());
            }
        }

        tsLogger.logger.info("SlotJournal: Loaded " + slotData.size() + " slots from journal");
    }

    /**
     * Stop the journal, flushing all pending writes.
     *
     * @throws Exception if journal cannot be stopped cleanly
     */
    public void stop() throws Exception {
        // Compact journal to apply deletes before stopping
        // This ensures deleted records are truly removed from the journal files
        try {
            journal.scheduleCompactAndBlock(5000); // 5 second timeout
        } catch (Exception e) {
            tsLogger.logger.warn("SlotJournal: Compaction failed during stop: " + e.getMessage());
        }
        journal.stop();
    }

    /**
     * Write slot data to journal.
     * If slot already exists, replaces it (deletes old record, adds new one).
     *
     * @param slotId the slot ID (0 to numberOfSlots-1)
     * @param data the data to write
     * @throws Exception if write fails
     */
    public void write(int slotId, byte[] data) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Cannot write null data for slot " + slotId);
        }

        // Pack data: [slotId (4 bytes)] [length (4 bytes)] [data (N bytes)]
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + data.length);
        buffer.putInt(slotId);
        buffer.putInt(data.length);
        buffer.put(data);
        byte[] encoded = buffer.array();

        journalLock.lock();
        try {
            // Get old record ID if exists
            Long oldRecordId = slotToRecordId.get(slotId);

            if (oldRecordId != null) {
                // Delete old record first
                if (syncWrites) {
                    journal.appendDeleteRecord(oldRecordId, true);
                } else {
                    journal.tryAppendDeleteRecord(oldRecordId, false, null, null);
                }
            }

            // Add new record
            long newRecordId = nextRecordId.getAndIncrement();
            journal.appendAddRecord(newRecordId, RECORD_TYPE, encoded, syncWrites);

            // Update in-memory maps
            slotData.put(slotId, data);
            slotToRecordId.put(slotId, newRecordId);
        } finally {
            journalLock.unlock();
        }
    }

    /**
     * Read slot data from memory (already loaded from journal on start).
     *
     * @param slotId the slot ID
     * @return the slot data, or null if slot is empty
     */
    public byte[] read(int slotId) {
        return slotData.get(slotId);
    }

    /**
     * Delete slot data from journal.
     *
     * @param slotId the slot ID
     * @throws Exception if delete fails
     */
    public void delete(int slotId) throws Exception {
        journalLock.lock();
        try {
            Long recordId = slotToRecordId.remove(slotId);
            slotData.remove(slotId);

            if (recordId != null) {
                if (syncDeletes) {
                    journal.appendDeleteRecord(recordId, true);
                } else {
                    journal.tryAppendDeleteRecord(recordId, false, null, null);
                }
            }
        } finally {
            journalLock.unlock();
        }
    }

    /**
     * Get all slot IDs that have data in the journal.
     *
     * @return set of slot IDs
     */
    public java.util.Set<Integer> getSlotIds() {
        return new java.util.HashSet<>(slotData.keySet());
    }

    /**
     * Compact the journal, removing old records.
     * Call periodically to prevent journal from growing indefinitely.
     *
     * @throws Exception if compaction fails
     */
    public void compact() throws Exception {
        journal.scheduleCompactAndBlock(5000); // 5 second timeout
        tsLogger.logger.debug("SlotJournal: Compaction completed");
    }

    /**
     * Get the number of slots with data.
     *
     * @return count of non-empty slots
     */
    public int size() {
        return slotData.size();
    }
}
