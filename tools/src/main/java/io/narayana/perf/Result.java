/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package io.narayana.perf;

import java.io.Serializable;
import java.util.*;

/**
 * @author <a href="mailto:mmusgrov@redhat.com">M Musgrove</a>
 *
 * Config data for running a work load (@see PerformanceTester and @see Worker)
 *
 */
public class Result<T> implements Serializable {
    int threadCount = 1;
    int numberOfCalls;
    int batchSize;
    int errorCount;
    long totalMillis;
    int one; // time in msecs to do one call
    int throughput; // calls per second
    private T context;
    private PerformanceTester<T> tester;
    private Worker<T> worker;

    private final Set<T> contexts = new HashSet<T>();

    private String info;
    private boolean cancelled;
    private boolean mayInterruptIfRunning;

    public Result(Worker<T> worker, int numberOfThreads, int numberOfCalls) {
        this(worker, numberOfThreads, numberOfCalls, numberOfCalls / numberOfThreads);
    }

    public Result(Worker<T> worker, int numberOfThreads, int numberOfCalls, int batchSize) {
        this.threadCount = numberOfThreads;
        this.numberOfCalls = numberOfCalls;
        this.totalMillis = this.throughput = 0;
        this.errorCount = 0;
        this.batchSize = batchSize;
        this.worker = worker;

        tester = new PerformanceTester<T>(threadCount, batchSize);
    }

    Result(int numberOfThreads, int numberOfCalls, int batchSize) {
        this(null, numberOfThreads, numberOfCalls, batchSize);
    }

    public void setContext(T value) {
        context = value;
    }

    public T getContext() {
        return context;
    }

    public Set<T> getContexts() {
        return contexts;
    }

    void addContext(T t) {
        contexts.add(t);
    }

    public Result(Result result) {
        this(result.threadCount, result.numberOfCalls, result.getBatchSize());

        this.totalMillis = result.totalMillis;
        this.throughput = result.throughput;
        this.errorCount = 0;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getNumberOfCalls() {
        return numberOfCalls;
    }

    public void setNumberOfCalls(int numberOfCalls) {
        this.numberOfCalls = numberOfCalls;
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public long getOne() {
        return one;
    }

    public void setTotalMillis(long totalMillis) {
        this.totalMillis = totalMillis;
        if (totalMillis != 0) {
            this.one = totalMillis > 0 ? (int) (totalMillis / numberOfCalls) : 0;
            this.throughput = (int) ((1000 * numberOfCalls) / totalMillis);
        }
    }

    public int getThroughput() {
        return throughput;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public void incrementErrorCount() {
        this.errorCount += 1;
    }

    public String toString() {
            return String.format("%11d %6d %9d %11d%n",
                getThroughput(), getNumberOfCalls(), getErrorCount(), threadCount);
    }

    public void setInfo(String info) {
        this.info = info;
    }


    public String getInfo() {
        return info;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public synchronized Result<T>  measureThroughput() {
        return tester.measureThroughput(worker, this);
    }

    /**
     * Cancel the measurement.
     *
     * A worker may cancel a measurement by invoking this method on the Measurement object it was
     * passed in its @see Worker#doWork(T, int, Measurement) method
     * @param mayInterruptIfRunning if false then any running calls to @see Worker#doWork will be allowed to finish
     *                              before the the measurement is cancelled.
     */
    public void cancel(boolean mayInterruptIfRunning) {
        this.cancelled = true;
        this.mayInterruptIfRunning = mayInterruptIfRunning;
    }

    void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isMayInterruptIfRunning() {
        return mayInterruptIfRunning;
    }
}
