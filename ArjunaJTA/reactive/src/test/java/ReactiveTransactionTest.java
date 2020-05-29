import io.smallrye.mutiny.Uni;
import narayana.io.reactive.transaction.ReactiveSynchronization;
import narayana.io.reactive.transaction.ReactiveTransaction;
import narayana.io.reactive.transaction.internal.TransactionManagerImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.transaction.Status;
import javax.transaction.SystemException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.lang.System.out;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReactiveTransactionTest {
    @Rule
    public final TestName testName = new TestName();

    @Before
    public void before() {
        System.out.printf("Running %s%n", testName.getMethodName());
    }

    @Test
    // test that a block of code executes with an active transaction
    public void testWith() {
        String expected = "code block response";
        Function<ReactiveTransaction, Uni<String>> work = txn -> {
            out.printf("transactional work with txn=%s%n", txn);
            try {
                // the user work should be running with an active transaction
                assertNotNull("user code is not running transactionally",
                        com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction());
                assertEquals("user code is not running with an active transaction", Status.STATUS_ACTIVE,
                        com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction().getStatus());
            } catch (SystemException e) {
                fail(e.getMessage());
            }
            return Uni.createFrom().item(expected);
        };
        TransactionManagerImpl tm = new TransactionManagerImpl();
        String actual = tm
                .withTransaction(work)
                .onFailure().invoke(e -> fail(e.getMessage()))
                .await().indefinitely();

        assertEquals(expected, actual);
    }

    @Test
    // test that setting rollback only causes the transaction to rollback
    public void testWithSetRollbackOnly() {
        String expected = "code block response";
        Function<ReactiveTransaction, Uni<String>> work = txn -> {
            txn.setRollbackOnly();
            assertTrue("the transaction is not marked for rollback", txn.isRollbackOnly());
            out.printf("transactional work with txn=%s%n", txn); return Uni.createFrom().item(expected);};
        ReactiveSynchronizationImpl synchronization = new ReactiveSynchronizationImpl();
        TransactionManagerImpl tm = new TransactionManagerImpl();
        String actual = tm
                .withTransaction(work, synchronization)
                .onFailure().invoke(e -> fail(e.getMessage()))
                .await().indefinitely();

        assertEquals(Status.STATUS_ROLLEDBACK, synchronization.status);
        assertEquals(expected, actual); // user work was still invoked
    }

    @Test
    // test that synchronisations are executed
    public void testWithSynchronization() {
        String expected = "code block response";
        Function<ReactiveTransaction, Uni<String>> work = txn -> {
            out.printf("transactional work with txn=%s%n", txn);
            return Uni.createFrom().item(expected);
        };
        ReactiveSynchronizationImpl synchronization = new ReactiveSynchronizationImpl();
        TransactionManagerImpl tm = new TransactionManagerImpl();
        String actual = tm
                .withTransaction(work, synchronization)
                .onFailure().invoke(e -> fail(e.getMessage()))
                .await().indefinitely();

        assertEquals("both of the completion callbacks were not called",
                2, synchronization.callbackCount);
        assertEquals("the transaction finished in the wrong state",
                Status.STATUS_COMMITTED, synchronization.status);
        assertEquals(expected, actual);
    }

    @Test
    // test that XAResources are handled correctly
    public void testWithResources() {
        String expected = "code block response";
        Function<ReactiveTransaction, Uni<String>> work = txn -> {
            out.printf("transactional work with txn=%s%n", txn);
            return Uni.createFrom().item(expected);
        };
        TransactionManagerImpl tm = new TransactionManagerImpl();
        DummyXA[] xars = {new DummyXA(false), new DummyXA(false)};

        String actual = tm
                .withTransaction(work, xars)
                .onFailure().invoke(e -> fail(e.getMessage()))
                .await().indefinitely();

        assertEquals(expected, actual);
        assertEquals(1, xars[0].prepareCnt.get());
        assertEquals(1, xars[1].prepareCnt.get());
        assertEquals(1, xars[0].commitCnt.get());
        assertEquals(1, xars[1].commitCnt.get());
    }

    @Test
    // Test that two transactional flows do not interfere with each other.
    //
    // There is no suspend/resume for transaction to thread association in the implementation.
    // TODO need to resolve the following problem:
    // I wanted to add that but could create a test for it since Mutiny seems to always run my
    // "Uni flow" in a single thread. The next test is my attempt to get two flows to interleave but
    // it seems to pass without suspend/resume. Any help you can provide here would be appreciated.
    public void testTransactionsDoNotInterleave() throws InterruptedException {
        String[] expected = {"code block 1", "code block 2"};
        List<String> results = new ArrayList<>();
        ReactiveTransaction[] txns = new ReactiveTransaction[2];
        Long[] tids = new Long[2];
        Function<ReactiveTransaction, Uni<String>> work1 = txn -> {
            tids[0] = Thread.currentThread().getId();
            txns[0] = txn;
            out.printf("tid=%d txn=%s%n", tids[0], txns[0]); return Uni.createFrom().item(expected[0]);};
        Function<ReactiveTransaction, Uni<String>> work2 = txn -> {
            tids[1] = Thread.currentThread().getId();
            txns[1] = txn;
            out.printf("tid=%d txn=%s%n", tids[1], txn); return Uni.createFrom().item(expected[1]);};
        ReactiveSynchronizationImpl[] synchronizations = {new ReactiveSynchronizationImpl(), new ReactiveSynchronizationImpl()};
        TransactionManagerImpl tm = new TransactionManagerImpl();
        ExecutorService executor = Executors.newWorkStealingPool();
        List<Callable<String>> callables = Arrays.asList(
                () -> tm.withTransaction(work1, synchronizations[0]).onFailure().invoke(e -> fail(e.getMessage())).await().indefinitely(),
                () -> tm.withTransaction(work2, synchronizations[1]).onFailure().invoke(e -> fail(e.getMessage())).await().indefinitely());

        executor.invokeAll(callables)
                .stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .forEach(results::add);

        assertEquals("the first transaction did not commit successfully",
                javax.transaction.Status.STATUS_COMMITTED, synchronizations[0].status);
        assertEquals("the second transaction did not commit successfully",
                javax.transaction.Status.STATUS_COMMITTED, synchronizations[1].status);
        assertTrue(results.contains(expected[0]));
        assertTrue(results.contains(expected[1]));
        assertNotEquals("user work did not execute in different transactions",
                txns[0], txns[1]);
        assertNotEquals("user work did not execute in different threads",
                tids[0], tids[1]);
    }

    // Wrap user synhronizations.
    // Remark: The narayana TM is not reactive internally so we still need to do some internal
    // work around the commit and before/afterCompletion logic.
    private static class ReactiveSynchronizationImpl implements ReactiveSynchronization {
        int status = -1;
        int callbackCount = 0;

        Function<ReactiveTransaction, Uni<Void>> beforeCompletion = txn -> {
            out.printf("%s: before synch txn=%s%n", this.hashCode(), txn);
            callbackCount += 1;
            return Uni.createFrom().voidItem();
        };
        BiFunction<ReactiveTransaction, Integer, Uni<Void>> afterCompletion = (txn, status) -> {
            out.printf("%s: after sync txn=%s status=%s%n", this.hashCode(), txn, status);
            callbackCount += 1;
            return Uni.createFrom().voidItem();
        };

        @Override
        public Function<ReactiveTransaction, Uni<Void>> beforeCompletion() {
            return beforeCompletion;
        }

        @Override
        public BiFunction<ReactiveTransaction, Integer, Uni<Void>> afterCompletion(int status) {
            this.status = status;
            return afterCompletion;
        }
    }
}
