package narayana.io.reactive.transaction.internal;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple;
import io.smallrye.mutiny.groups.UniJoin;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;

import javax.transaction.xa.XAResource;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import narayana.io.reactive.transaction.ReactiveSynchronization;
import narayana.io.reactive.transaction.ReactiveTransaction;
import narayana.io.reactive.transaction.ReactiveTransactionManager;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.smallrye.mutiny.unchecked.Unchecked.supplier;

public class TransactionManagerImpl implements ReactiveTransactionManager {
    private static final Logger log = Logger.getLogger(TransactionManagerImpl.class);

    //    private final com.arjuna.ats.jta.TransactionManager tm;
    //    private final TransactionManager tm;

    public TransactionManagerImpl() {
//        this.tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
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

    private static class Transaction<T> implements ReactiveTransaction {
        boolean rollback;
        Throwable error;

        // the execution is modelled on how hibernate-reactive does it:
        Uni<T> execute(Function<ReactiveTransaction, Uni<T>> work, ReactiveSynchronization synchronization, XAResource... xaResources) {
            return begin(xaResources)
                    .flatMap(v -> work.apply(this))
                    // have to capture the error here and pass it along,
                    // since we can't just return a CompletionStage that
                    // rolls back the transaction from the handle() function
                    .onTermination().invoke(this::processError)
                    // finally, commit or rollback the transaction, and
                    // then rethrow the caught error if necessary
                    .flatMap(
                            result -> end(synchronization)
                                    // make sure that if rollback() throws,
                                    // the original error doesn't get swallowed
                                    .onTermination().invoke(this::processError)
                                    // finally rethrow the original error, if any
                                    .map(v -> returnOrRethrow(error, result))
                    );
        }

        Uni<Void> begin(XAResource... xaResources) {
            return Uni.createFrom().item(supplier(() -> {
                com.arjuna.ats.jta.TransactionManager.transactionManager().begin();
                // register resources before executing transactional code
                UniJoin.Builder<Boolean> builder = Uni.join().builder();

                for (XAResource xar : xaResources) {
                    // since XA start/end communicate with resource managers the enlistments may block so use an emitter
                    // (emitters form a bridge between the imperative and the reactive world)
                    // alternatively could just use enlistment = Uni.createFrom().item(getTransaction().enlistResource(xar))
                    Uni<Boolean> enlistment = Uni.createFrom().emitter(emitter -> {
                        try {
                            if (com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction().enlistResource(xar)) {
                                emitter.complete(true);
                            } else {
                                rollback = true;

                                // TransactionImple does not include the exception in all cases of markRollbackOnly()
                                // such as when an XAResource throws an exception
                                // the exception trace is held in TransactionImple._rollbackOnlyCallerStacktrace
                                // but not available
                                // could change TransactionImple to use markRollbackOnly(String reason) which was added by JBTM-3882
                                emitter.fail(new Throwable("Could not enlist resource"));
                            }
                        } catch (SystemException | RollbackException e) {
                            emitter.fail(e);// propagate the exception along the chain
                        }
                    });

                    builder.add(enlistment);
                }

                UniJoin.JoinAllStrategy<Boolean> enlistments = builder.joinAll();

                enlistments.andCollectFailures().subscribe().with(
                        items -> {
                            // This block is executed if all Uni streams completed successfully.

                            // Note that since enlistResource, which is a blocking operation.
                            // It will catch any exception and set rollback_only and return false.
                            // Thus, we need to check the result of the enlistResource method call explicitly
                            if (items.contains(Boolean.FALSE)) {
                                // At least one enlistment failed. The transaction ought to be marked rollback only
                                // already but check that it is and mark rollback only if not
                                ReactiveTransaction tx = wrapTransaction();
                                if (!tx.isRollbackOnly())
                                    tx.setRollbackOnly();
                            }
                        },
                        failure -> {
                            // This block is executed because at least one Uni failed.
                            // But note that the enlistResource call is an imperative call so the uni wrapping it
                            // will never fail
                            if (failure instanceof io.smallrye.mutiny.CompositeException composite) {
                                List<Throwable> causes = composite.getCauses();
                                log.debugf("enlistment failure: %s",
                                        causes.isEmpty() ? "Could not enlist resource" : causes.get(0).getMessage());
                            }
                        }
                );

                return null;
            }));
        }

        private Synchronization toJTA(ReactiveSynchronization sync) {
            return new Synchronization() {
                @Override
                public void beforeCompletion() {
                    try {
                        // await only as long as the transaction timeout allows
                        long timeRemaining =
                                ((TransactionImple) com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction())
                                        .getRemainingTimeoutMills();
                        Duration waitFor = Duration.of(timeRemaining, ChronoUnit.MILLIS);

                        sync.beforeCompletion(wrapTransaction()).await().atMost(waitFor);
                    } catch (SystemException ignore) {
                        // log a message
                        sync.beforeCompletion(wrapTransaction()).await().indefinitely();
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    // run as a uni that returns nothing
                    Uni.createFrom().voidItem().subscribe().with(item -> {
                        sync.afterCompletion(status);
                    });
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
            Object[] prevContext = {null};
            SystemException[] systemException = {null};

            try {
                prevContext[0] = com.arjuna.ats.jta.TransactionManager.transactionManager().suspend();
            } catch (SystemException e) {
                systemException[0] = e;
            }

            // end is a blocking operation so use an emitter
            // (emitters form a bridge between the imperative and the reactive world)
            Uni<Void> uni = Uni.createFrom().emitter(emitter -> {
                if (systemException[0] != null) {
                    emitter.fail(systemException[0]);
                } else {
                    try {
                        assert prevContext[0] != null;

                        com.arjuna.ats.jta.TransactionManager.transactionManager().resume((jakarta.transaction.Transaction) prevContext[0]);

                        if (sync != null) {
                            // register the synchronisation
                            com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction().registerSynchronization(toJTA(sync));
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

        private Uni<Void> nonBlockingEnd() {
            return Uni.createFrom().item(supplier(() -> {
                endTransaction();
                return null;
            }));
        }

        private void endTransaction() throws SystemException,
                HeuristicRollbackException, HeuristicMixedException, RollbackException {
            if (rollback) {
                com.arjuna.ats.jta.TransactionManager.transactionManager().rollback();
            } else {
                com.arjuna.ats.jta.TransactionManager.transactionManager().commit();
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
                        com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction().setRollbackOnly();
                    } catch (SystemException e) {
                        // It's safe to ignore the exception since:
                        // - in non-crash scenarios the rollback flag ensures rollback
                        // - in crash scenarios the transaction will still rollback since
                        //   the TM uses presumed abort, ie if there is no log (logs are only
                        //   written after the prepare phase) it will roll back
                        //   the transaction on restart.
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
