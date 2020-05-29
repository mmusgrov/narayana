package narayana.io.reactive.transaction;

import javax.transaction.Status;
import java.util.function.Function;

/**
 * Allows code within {@link ReactiveTransactionManager#withTransaction(Function)}
 * to mark a transaction for rollback. A transaction marked for rollback will
 * never be committed.
 */
public interface ReactiveTransaction {
    /**
     * Mark the current transaction for rollback.
     */
    void setRollbackOnly();
    /**
     * Is the current transaction marked for rollback.
     */
    boolean isRollbackOnly();
}
