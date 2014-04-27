package org.jboss.jbossts.star.test;

import io.narayana.perf.PerformanceTester;
import io.narayana.perf.Result;
import io.narayana.perf.Worker;
import org.jboss.jbossts.star.util.TxSupport;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PerformanceTest extends BaseTest {
    @BeforeClass
    public static void beforeClass() throws Exception {
        startContainer(TXN_MGR_URL);
    }

    // 2PC commit
    @Test
    public void measureThroughput() throws Exception {
        PerformanceTester<String> tester = new PerformanceTester<String>(1, 10000);
        Result<String> opts = new Result<String>(1, 10000);

        try {
            tester.measureThroughput(new RTSWorker(), opts);

            System.out.printf("Test performance: %d calls/sec (%d invocations using %d threads with %d errors. Total time %d ms)%n",
                    opts.getThroughput(), opts.getNumberOfCalls(), opts.getThreadCount(),
                    opts.getErrorCount(), opts.getTotalMillis());

            Assert.assertEquals(0, opts.getErrorCount());
        } finally {
            tester.fini();
        }
    }

    private class RTSWorker implements Worker<String> {
        private TxSupport txn;

        private String run2PC(String context) throws Exception {
            String pUrl = PURL;
            String[] pid = new String[2];
            String[] pVal = new String[2];

            for (int i = 0; i < pid.length; i++) {
                pid[i] = modifyResource(txn, pUrl, null, "p1", "v1");
                pVal[i] = getResourceProperty(txn, pUrl, pid[i], "p1");

                Assert.assertEquals(pVal[i], "v1");
            }

            txn.startTx();

            for (int i = 0; i < pid.length; i++) { txn.enlistTestResource(pUrl, false);
                enlistResource(txn, pUrl + "?pId=" + pid[i]);

                modifyResource(txn, pUrl, pid[i], "p1", "v2");
                pVal[i] = getResourceProperty(txn, pUrl, pid[i], "p1");

                Assert.assertEquals(pVal[i], "v2");
            }

            txn.commitTx();

            for (int i = 0; i < pid.length; i++) {
                pVal[i] = getResourceProperty(txn, pUrl, pid[i], "p1");
                Assert.assertEquals(pVal[i], "v2");
            }

            return context;
        }

        private void emptyTxn() throws Exception {
            txn.startTx();
            txn.commitTx();
        }

        @Override
        public String doWork(String context, int niters, Result<String> opts) {
            for (int i = 0; i < niters; i++) {
                try {
                    emptyTxn();
                    //run2PC(context);
                } catch (Exception e) {
                    System.out.printf("workload %d failed with %s%n", i, e.getMessage());
                    opts.incrementErrorCount();
                }
            }

            return context;
        }

        @Override
        public void init() {
            txn = new TxSupport();
        }

        @Override
        public void fini() {
        }
    }
}
