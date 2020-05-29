package narayana.io.reactive.transaction;

import io.smallrye.mutiny.Uni;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * TODO
 */
public interface ReactiveSynchronization {
    /**
     * This method is invoked before the start of the commit
     * process. The method invocation is done in the context of the
     * transaction that is about to be committed.
     * @return the callback function
     */
    Function<ReactiveTransaction, Uni<Void>> beforeCompletion();

    /**
     * This method is invoked after the transaction has committed or
     * rolled back.
     *
     * @param status The status of the completed transaction.
     * @return the callback function
     */
    BiFunction<ReactiveTransaction, Integer, Uni<Void>> afterCompletion(int status);
}
