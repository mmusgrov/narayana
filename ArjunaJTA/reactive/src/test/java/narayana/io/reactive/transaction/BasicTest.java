package narayana.io.reactive.transaction;

import io.smallrye.mutiny.Uni;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import narayana.io.reactive.transaction.internal.TransactionManagerImpl;
import org.junit.Test;

import java.util.function.Function;

import static java.lang.System.out;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class BasicTest {
    // test that a block of code executes with an active transaction
    @Test
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
        try {
            String actual = tm
                    .withTransaction(work)
                    .onFailure().invoke(e -> fail(e.getMessage()))
                    .await().indefinitely();

            assertEquals(expected, actual);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
