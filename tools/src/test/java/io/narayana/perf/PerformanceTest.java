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

import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class PerformanceTest {
    private BigInteger factorial(int num) {
        BigInteger serialFac = BigInteger.ONE;

        for (int i = 2; i <= num; i++) {
            serialFac = serialFac.multiply(BigInteger.valueOf(i));
        }

        return serialFac;
    }

    @Test
    public void perfTest1() {
        int numberOfCalls = 50000;
        int threadCount = 40;
        int batchSize = 100;

        Worker worker = new SleepWorker();

        Measurement measurement = new Measurement(worker, numberOfCalls, threadCount, batchSize);
        measurement.measure();
    }
    @Test
    public void perfTest2() {
        int numberOfCalls = 50000;
        int threadCount = 40;
        int batchSize = 100;

        Worker worker = new SleepWorker();

        Result measurement = new Result(worker, threadCount, numberOfCalls, batchSize);
        measurement.measureThroughput();
    }
    @Test
    public void perfTest3() {
        int numberOfCalls = 50000;
        int threadCount = 40;
        int batchSize = 100;

        PerformanceTester tester = new PerformanceTester(threadCount, batchSize);
        Worker worker = new SleepWorker();

        Result measurement = new Result(worker, threadCount, numberOfCalls, batchSize);
        tester.measureThroughput(worker, measurement);
    }
    @Test
    public void testPerformanceTester() {
        int numberOfCalls = 50000;//100000;   // 1000000!: (total time: 78635 ms versus 1629870 ms) so dont make numberOfCalls to big
        int threadCount = 40; //40;
        int batchSize = 100; //100;

        FactorialWorker worker = new FactorialWorker();

        Measurement<BigInteger> measurement = new Measurement<BigInteger>(worker, numberOfCalls, threadCount, batchSize);
        measurement.measure();
        Set<BigInteger> subFactorials = measurement.getContexts();
        BigInteger fac = BigInteger.ONE;

        for (BigInteger bigInteger : subFactorials)
            fac = fac.multiply(bigInteger);

        long start = System.nanoTime();
        BigInteger serialFac = factorial(measurement.getNumberOfCalls());
        long millis = (System.nanoTime() - start) / 1000000L;

        System.out.printf("Test performance for %d!: %d calls / second (total time: %d ms versus %d ms) (%d, %d)%n",
                measurement.getNumberOfCalls(), measurement.getThroughput(), measurement.getTotalMillis(), millis,
                measurement.getThreadCount(), measurement.getBatchSize());

        assertTrue("Error cnt: " + measurement.getErrorCount(), measurement.getErrorCount() == 0);
        assertTrue("Factorials not equal", serialFac.equals(fac));

        assertTrue("init method not called", worker.getInitTimemillis() != -1);
        assertTrue("doWork method not called", worker.getWorkTimeMillis() != -1);
        assertTrue("fini method not called", worker.getFiniTimeMillis() != -1);

        assertTrue("init method called after work method", worker.getInitTimemillis() <= worker.getWorkTimeMillis());
        assertTrue("work method called after fini method", worker.getWorkTimeMillis() <= worker.getFiniTimeMillis());
    }

    @Test
    public void old1TestPerformanceTester() {
        int numberOfCalls = 50000;//100000;   // 1000000!: (total time: 78635 ms versus 1629870 ms) so dont make numberOfCalls to big
        int threadCount = 40; //40;
        int batchSize = 100; //100;

        PerformanceTester<BigInteger> tester = new PerformanceTester<BigInteger>(threadCount, batchSize);
        FactorialWorker worker = new FactorialWorker();
        Result<BigInteger> opts = new Result<BigInteger>(worker, threadCount, numberOfCalls);

        try {
            Result<BigInteger> res = tester.measureThroughput(worker, opts);

            Set<BigInteger> subFactorials = res.getContexts();
            BigInteger fac = BigInteger.ONE;

            for (BigInteger bigInteger : subFactorials)
                fac = fac.multiply(bigInteger);

            long start = System.nanoTime();
            BigInteger serialFac = factorial(opts.getNumberOfCalls());
            long millis = (System.nanoTime() - start) / 1000000L;

            System.out.printf("Old Test performance for %d!: %d calls / second (total time: %d ms versus %d ms) (%d, %d)%n",
                    opts.getNumberOfCalls(), opts.getThroughput(), opts.getTotalMillis(), millis,
                    opts.getThreadCount(), opts.getBatchSize());

            assertTrue("Error cnt: " + opts.getErrorCount(), opts.getErrorCount() == 0);
            assertTrue("Factorials not equal", serialFac.equals(fac));

            assertTrue("init method not called", worker.getInitTimemillis() != -1);
            assertTrue("doWork method not called", worker.getWorkTimeMillis() != -1);
            assertTrue("fini method not called", worker.getFiniTimeMillis() != -1);

            assertTrue("init method called after work method", worker.getInitTimemillis() <= worker.getWorkTimeMillis());
            assertTrue("work method called after fini method", worker.getWorkTimeMillis() <= worker.getFiniTimeMillis());

        } finally {
            tester.fini();
        }

    }

    @Test
    //@Ignore
    @Deprecated
    public void old2TestPerformanceTester() {
        int numberOfCalls = 50000;//100000;   // 1000000!: (total time: 78635 ms versus 1629870 ms) so dont make numberOfCalls to big
        int threadCount = 40; //40;
        int batchSize = 100; //100;

        FactorialWorker worker = new FactorialWorker();
        Result<BigInteger> opts = new Result<BigInteger>(worker, threadCount, numberOfCalls, batchSize);

        opts.measureThroughput();

        Set<BigInteger> subFactorials = opts.getContexts();
        BigInteger fac = BigInteger.ONE;

        for (BigInteger bigInteger : subFactorials)
            fac = fac.multiply(bigInteger);

        long start = System.nanoTime();
        BigInteger serialFac = factorial(opts.getNumberOfCalls());
        long millis = (System.nanoTime() - start) / 1000000L;

        System.out.printf("Old Test performance for %d!: %d calls / second (total time: %d ms versus %d ms) (%d, %d)%n",
                opts.getNumberOfCalls(), opts.getThroughput(), opts.getTotalMillis(), millis,
                opts.getThreadCount(), opts.getBatchSize());

        assertTrue("Error cnt: " + opts.getErrorCount(), opts.getErrorCount() == 0);
        assertTrue("Factorials not equal", serialFac.equals(fac));

        assertTrue("init method not called", worker.getInitTimemillis() != -1);
        assertTrue("doWork method not called", worker.getWorkTimeMillis() != -1);
        assertTrue("fini method not called", worker.getFiniTimeMillis() != -1);

        assertTrue("init method called after work method", worker.getInitTimemillis() <= worker.getWorkTimeMillis());
        assertTrue("work method called after fini method", worker.getWorkTimeMillis() <= worker.getFiniTimeMillis());
    }

    @Test
    public void testAbortMeasurement() {
        int numberOfCalls = 1000;
        int threadCount = 10;
        int batchSize = 1;

      //  Measurement<String> config = new Measurement<>(new AbortWorker(), numberOfCalls, threadCount, batchSize);
        Result<String> config = new Result<>(new AbortWorker(), numberOfCalls, threadCount, batchSize);

        config.measureThroughput();

        assertTrue("Test should have been aborted", config.isCancelled());
        assertEquals("Abort context should have been \"cancelled\"", "cancelled", config.getContext());
    };

    @Test
    public void testSingleCall() {
        Worker<String> worker = new Worker<String>() {

            @Override
            public String doWork(String context, int niters, Measurement<String> opts) {
                assertEquals("Wrong batch size", 1, niters);
                return context;
            }

            @Override
            public String doWork(String context, int niters, Result<String> opts) {
                assertEquals("Wrong batch size", 1, niters);
                return context;
            }

            @Override
            public void init() {}

            @Override
            public void fini() {}
        };

        Measurement<String> config = new Measurement<>(worker, 1, 1);

        assertEquals("Wrong batch size", 1, config.getBatchSize());
    }
}
