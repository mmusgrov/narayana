package narayana.io.reactive.transaction.internal;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import narayana.io.reactive.transaction.ReactiveSynchronization;
import narayana.io.reactive.transaction.ReactiveTransaction;
import narayana.io.reactive.transaction.ReactiveTransactionManager;

import java.util.function.Function;

import static io.smallrye.mutiny.unchecked.Unchecked.supplier;

public class TransactionManagerImpl implements ReactiveTransactionManager {
    private final TransactionManager tm;

    public TransactionManagerImpl() {
        this.tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
    }

    public <T> Uni<T> withTransaction(Function<ReactiveTransaction, Uni<T>> work) {
        return new Transaction<T>().execute(work, null);
    }

    public <T> Uni<T> withTransaction(Function<ReactiveTransaction, Uni<T>> work,
                                      ReactiveSynchronization sync,
                                      XAResource... xaResources) {
        return new Transaction<T>().execute(work, sync, xaResources);
    }

    public<T> Uni<T> withTransaction(Function<ReactiveTransaction, Uni<T>> work,
                                     XAResource... xaResources) {
        return new Transaction<T>().execute(work, null, xaResources);
    }

    private class Transaction<T> implements ReactiveTransaction {
        boolean rollback;
        Throwable error;

        // the execution is modelled on how hibernate-reactive does it:
        Uni<T> execute(Function<ReactiveTransaction, Uni<T>> work, ReactiveSynchronization synchronization, XAResource... xaResources) {
            return begin(xaResources)
                    .flatMap(v -> work.apply(this))
                    // have to capture the error here and pass it along,
                    // since we can't just return a CompletionStage that
                    // rolls back the transaction from the handle() function
                    .on().termination(this::processError)
                    // finally, commit or rollback the transaction, and
                    // then rethrow the caught error if necessary
                    .flatMap(
                            result -> end(synchronization)
                                    // make sure that if rollback() throws,
                                    // the original error doesn't get swallowed
                                    .on().termination(this::processError)
                                    // finally rethrow the original error, if any
                                    .map(v -> returnOrRethrow(error, result))
                    );
        }

        Uni<Void> begin(XAResource... xaResources) {
            return Uni.createFrom().item(supplier(() -> {
                tm.begin();
                // register resources before executing transactional code
                for (XAResource xar : xaResources) {
                    // TODO need a reactive strategy for enlisting resources
                    // since XA start/end communicate with resource managers
                    // (might be okay for inVM RMs)
                    tm.getTransaction().enlistResource(xar);
                }

                return null;
            }));
        }

        // TODO update the narayana TM to support reactive synchronizations
        private Synchronization toJTA(ReactiveSynchronization sync) {
            return new Synchronization() {
                @Override
                public void beforeCompletion() {
                    // TODO await only as long as the transaction timeout allows
                    sync.beforeCompletion().apply(wrapTransaction()).await().indefinitely();
                }

                @Override
                public void afterCompletion(int status) {
                    // TODO await only as long as the transaction timeout allows
                    sync.afterCompletion(status).apply(wrapTransaction(), status).await().indefinitely();
                }
            };
        }

        Uni<Void> end(ReactiveSynchronization sync) {
            return blockingEnd(sync);
        }

        /*
         * a blocking version of ending a transaction
         */
        Uni<Void> blockingEnd(final ReactiveSynchronization sync) {
            javax.transaction.Transaction[] prevContext = {null};
            SystemException[] systemException = {null};

            try {
                prevContext[0] = tm.suspend();
            } catch (SystemException e) {
                systemException[0] = e;
            }

            Uni<Void> uni = Uni.createFrom().emitter(emitter -> {
                if (systemException[0] != null) {
                    emitter.fail(systemException[0]);
                } else {
                    try {
                        assert prevContext[0] != null;

                        tm.resume(prevContext[0]);

                        if (sync != null) {
                            // register the synchronisation TODO probably need to support multiple registrations
                            tm.getTransaction().registerSynchronization(toJTA(sync));
                        }

                        endTransaction();
                        emitter.complete(null); // propagate a void item along the chain
                    } catch (Exception e) {
                        emitter.fail(e); // propagate the exception along the chain
                    }
                }
            });

            return uni.runSubscriptionOn(Infrastructure.getDefaultWorkerPool()); // the code above will run on the passed executor.
        }

        // end without a transaction context switch TODO the TM needs to be non blocking too
        private Uni<Void> nonBlockingEnd() {
            return Uni.createFrom().item(supplier(() -> {
                endTransaction();
                return null;
            }));
        }

        private void endTransaction() throws InvalidTransactionException, SystemException,
                HeuristicRollbackException, HeuristicMixedException, RollbackException {
            if (rollback) {
                tm.rollback();
            } else {
                tm.commit();
            }
        }

        <R> R processError(R result, Throwable e, boolean canceled) {
            if (e != null) {
                rollback = true;
                if (error == null) {
                    error = e;
                } else {
                    error.addSuppressed(e);
                }
            } else if (canceled) {
                rollback = true;
            }
            return result;
        }

        @Override
        public void setRollbackOnly() {
            rollback = true;
        }

        @Override
        public boolean isRollbackOnly() {
            return rollback;
        }


        private ReactiveTransaction wrapTransaction() {
            //tm.getTransaction()
            return new ReactiveTransaction() {
                @Override
                public void setRollbackOnly() {
                    rollback = true;
                    try {
                        tm.getTransaction().setRollbackOnly();
                    } catch (SystemException e) {
                        // It's safe to ignore the exception since:
                        // - in non crash scenarios the the rollback flag ensures rollback
                        // - in crash scenarios the transaction will still rollback since
                        //   the TM uses presumed abort, ie if there is no log (logs are only
                        //   written after the prepare phase) it will rollback
                        //   the transaction on restart).
                        // The downside of ignoring the exception is that the TM will be unable
                        // to make certain optimisations
                    }
                }

                @Override
                public boolean isRollbackOnly() {
                    return rollback;
                }
            };
        }
    }

    public static <T extends Throwable, Ret> Ret returnOrRethrow(Throwable x, Ret result) throws T {
        if (x != null) {
            throw (T) x;
        }
        return result;
    }
}
