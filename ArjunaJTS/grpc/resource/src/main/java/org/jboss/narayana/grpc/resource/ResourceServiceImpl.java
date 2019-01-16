package org.jboss.narayana.grpc.resource;

import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.ExceptionResponse;
import org.jboss.narayana.ots.grpc.ResourceServiceGrpc;
import org.jboss.narayana.ots.grpc.ServiceInstance;
import org.jboss.narayana.ots.grpc.Vote;
import org.jboss.narayana.ots.grpc.VoteResponse;
import org.jboss.narayana.ots.grpc.otid_t;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ResourceServiceImpl extends ResourceServiceGrpc.ResourceServiceImplBase {
    private static final Logger logger = Logger.getLogger(ResourceServiceImpl.class.getName());

    private final String target;
    private static AtomicInteger prepareCount = new AtomicInteger(0);
    private static AtomicInteger commitCount = new AtomicInteger(0);
    private static AtomicInteger rollbackCount = new AtomicInteger(0);
    private static AtomicInteger commitOnePhaseCount = new AtomicInteger(0);
    private static AtomicInteger forgetCount = new AtomicInteger(0);

    private Map<otid_t, List<ResourceRegistration>> registeredResources;

    public ResourceServiceImpl(String target) {
        this.target = target;
        registeredResources = new HashMap<>();
    }

    @Override
    public void prepare(otid_t request, StreamObserver<VoteResponse> responseObserver) {
        int value = prepareCount.incrementAndGet();
        logger.info(String.format("%s: %s invoked %d times%n", request.toString(), "prepare", value));

        List<ResourceRegistration> resources = registeredResources.get(request);
        Vote vote = Vote.VoteReadOnly;

        for (ResourceRegistration rr : resources) {
            LocalVote v = rr.getResourceCallback().prepare(getOtid(request));

            switch (v) {
                case VoteRollback:
                    vote = Vote.VoteRollback;
                    break;
                case VoteCommit:
                    if (vote != Vote.VoteRollback) {
                        vote = Vote.VoteCommit;
                    }
                    break;
                case VoteReadOnly:
                    /* FALLTHRU */
                default:
                    break;
            }
        }

        if (vote == Vote.VoteCommit) {
            logger.info(String.format("%s: voting to commit (TODO create a persistent log)", request.toString()));
        }

        responseObserver.onNext(VoteResponse.newBuilder().setVote(vote).build());
        responseObserver.onCompleted();
    }

    @Override
    public void rollback(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        incrementStat((otid, rc) -> rc.rollback(otid), request, responseObserver, rollbackCount, "rollback");
    }

    @Override
    public void commit(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        incrementStat((otid, rc) -> rc.commit(otid), request, responseObserver, commitCount, "commit");
    }

    @Override
    public void commitOnePhase(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        incrementStat((otid, rc) -> rc.commit_one_phase(otid), request, responseObserver, commitOnePhaseCount, "commitOnePhase");
    }

    @Override
    public void forget(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        incrementStat((otid, rc) -> rc.forget(otid), request, responseObserver, forgetCount, "forget");
    }

    private void incrementStat(CheckedFunction<Otid, Resource> op, otid_t request, StreamObserver<ExceptionResponse> responseObserver, AtomicInteger stat, String message) {
        int value = stat.incrementAndGet();
        logger.info(String.format("%s: %s invoked %d times%n", request.toString(), message, value));

        List<ResourceRegistration> resources = registeredResources.get(request);
        Otid otid = getOtid(request);
        final ResourceException[] resourceException = {null};

        resources.forEach(rr -> {
            try {
                op.apply(otid, rr.getResourceCallback());
            } catch (ResourceException e) {
                resourceException[0] = e;
            }
        });

        if (resourceException[0] != null) {
            responseObserver.onNext(ExceptionResponse.newBuilder().setExceptions(0, resourceException[0].getException()).build());
        } else {
            responseObserver.onNext(ExceptionResponse.newBuilder().build());
        }

        responseObserver.onCompleted();
    }

    public int getPrepareCount() {
        return prepareCount.get();
    }

    public int getCommitCount() {
        return commitCount.get();
    }

    public int getRollbackCount() {
        return rollbackCount.get();
    }

    public int getCommitOnePhaseCount() {
        return commitOnePhaseCount.get();
    }

    public int getForgetCount() {
        return forgetCount.get();
    }

    public void resetCounts() {
        prepareCount.set(0);
        commitCount.set(0);
        rollbackCount.set(0);
        commitOnePhaseCount.set(0);
        forgetCount.set(0);
    }

    public ServiceInstance addResourceCallback(otid_t current, ServiceInstance recovery, Resource resourceCallback) {
        List<ResourceRegistration> resources = registeredResources.computeIfAbsent(current, k -> new ArrayList<>());

        if (recovery == null && resources.size() != 0) {
            recovery = resources.get(0).getRecovery();
        }

        resources.add(new ResourceRegistration(current, recovery, resourceCallback));

        return recovery;
    }

    public boolean hasRegistrations(otid_t current) {
        return registeredResources.containsKey(current);
    }

    private Otid getOtid(otid_t request) {
        return new Otid(request.getFormatID(), request.getBqualLength(), request.getTid().toByteArray());
    }

    @FunctionalInterface
    private interface CheckedFunction<Otid, Resource> {
        void apply(Otid t, Resource r) throws ResourceException;
    }
}
