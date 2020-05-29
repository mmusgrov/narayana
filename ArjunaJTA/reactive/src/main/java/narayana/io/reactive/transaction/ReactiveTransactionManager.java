package narayana.io.reactive.transaction;

import io.smallrye.mutiny.Uni;
import java.util.function.Function;
import javax.transaction.xa.XAResource;

/**
 * TODO ...
 */
public interface ReactiveTransactionManager {
    <T> Uni<T> withTransaction(Function<ReactiveTransaction, Uni<T>> work);

    <T> Uni<T> withTransaction(Function<ReactiveTransaction, Uni<T>> work,
                                      ReactiveSynchronization sync,
                                      XAResource... xaResources);

    <T> Uni<T> withTransaction(Function<ReactiveTransaction, Uni<T>> work,
                                     XAResource... xaResources);
}
