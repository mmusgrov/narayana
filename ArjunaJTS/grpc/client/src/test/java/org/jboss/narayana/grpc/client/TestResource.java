package org.jboss.narayana.grpc.client;

import org.jboss.narayana.grpc.resource.LocalVote;
import org.jboss.narayana.grpc.resource.Otid;
import org.jboss.narayana.grpc.resource.Resource;
import org.jboss.narayana.grpc.resource.ResourceException;

import java.util.concurrent.atomic.AtomicInteger;

public class TestResource implements Resource {
    private AtomicInteger prepareCount = new AtomicInteger(0);
    private AtomicInteger commitCount = new AtomicInteger(0);
    private AtomicInteger rollbackCount = new AtomicInteger(0);
    private AtomicInteger commitOnePhaseCount = new AtomicInteger(0);
    private AtomicInteger forgetCount = new AtomicInteger(0);

    @Override
    public LocalVote prepare(Otid otid) {
        prepareCount.incrementAndGet();
        return LocalVote.VoteCommit;
    }

    @Override
    public void rollback(Otid otid) throws ResourceException {
        rollbackCount.incrementAndGet();
    }

    @Override
    public void commit(Otid otid) throws ResourceException {
        commitCount.incrementAndGet();
    }

    @Override
    public void forget(Otid otid) throws ResourceException {
        forgetCount.incrementAndGet();
    }

    @Override
    public void commit_one_phase(Otid otid) throws ResourceException {
        commitOnePhaseCount.incrementAndGet();
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
}
