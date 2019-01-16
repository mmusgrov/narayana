package org.jboss.narayana.grpc.server;

import com.arjuna.ats.internal.jts.ORBManager;
import io.grpc.ManagedChannel;
import org.jboss.narayana.ots.grpc.ExceptionResponse;
import org.jboss.narayana.ots.grpc.OTSException;
import org.jboss.narayana.ots.grpc.ResourceId;
import org.jboss.narayana.ots.grpc.ResourceServiceGrpc;
import org.jboss.narayana.ots.grpc.VoteResponse;
import org.jboss.narayana.ots.grpc.otid_t;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.portable.UnknownException;
import org.omg.CosTransactions.HeuristicCommit;
import org.omg.CosTransactions.HeuristicHazard;
import org.omg.CosTransactions.HeuristicMixed;
import org.omg.CosTransactions.HeuristicRollback;
import org.omg.CosTransactions.NotPrepared;
import org.omg.CosTransactions.Resource;
import org.omg.CosTransactions.ResourceHelper;
import org.omg.CosTransactions.Vote;

import java.util.function.BiFunction;

public class ProxyResource extends org.omg.CosTransactions.ResourcePOA {
    private Resource ref;
    private ResourceId grpcResource;

    public ProxyResource(ResourceId resourceId) {
        ORBManager.getPOA().objectIsReady(this);

        this.ref = ResourceHelper.narrow(ORBManager.getPOA().corbaReference(this));
        this.grpcResource = resourceId;
    }

    public Resource getResource() {
        return ref;
    }

    public Vote prepare() throws SystemException {
        ManagedChannel channel = Utility.getChannel(grpcResource.getInstance().getLocation().getTarget());
        otid_t otid = Utility.getCurrentOtid();

        try {
            ResourceServiceGrpc.ResourceServiceBlockingStub stub = ResourceServiceGrpc.newBlockingStub(channel);

            VoteResponse vote = stub.prepare(otid);

            if (vote.getExceptionsCount() != 0) {
                throw new UnknownException(new Exception(vote.getExceptions(0).name()));
            }

            switch (vote.getVote().getNumber()) {
                case 0: return Vote.VoteCommit;
                case 1: return Vote.VoteRollback;
                case 2: return Vote.VoteReadOnly;
                default: throw new BAD_PARAM();
            }

        } finally {
            channel.shutdown();
        }
    }

    public void rollback() throws SystemException, HeuristicCommit, HeuristicMixed, HeuristicHazard {
        proxyRequest((otid, stub) -> stub.rollback(otid));
    }

    public void commit() throws SystemException, NotPrepared, HeuristicRollback, HeuristicMixed, HeuristicHazard {
        proxyRequest((otid, stub) -> stub.commit(otid));
    }

    public void forget() throws SystemException {
        proxyRequest((otid, stub) -> stub.forget(otid));
    }

    public void commit_one_phase() throws HeuristicHazard, SystemException {
        proxyRequest((otid, stub) -> stub.commitOnePhase(otid));
    }

    private ExceptionResponse proxyRequest(BiFunction<otid_t, ResourceServiceGrpc.ResourceServiceBlockingStub, ExceptionResponse> action) {
        ManagedChannel channel = Utility.getChannel(grpcResource.getInstance().getLocation().getTarget());

        try {
            otid_t otid = Utility.getCurrentOtid();
            ResourceServiceGrpc.ResourceServiceBlockingStub stub = ResourceServiceGrpc.newBlockingStub(channel);

            return action.apply(otid, stub);
        } catch (Exception e) {
            return ExceptionResponse.newBuilder()
                    .setExceptions(0, OTSException.Unavailable)
                    .build();
        } finally {
            channel.shutdown();
        }
    }
    private ExceptionResponse xxproxyRequest(BiFunction<otid_t, ResourceServiceGrpc.ResourceServiceBlockingStub, ExceptionResponse> action) {
        ManagedChannel channel = Utility.getChannel(grpcResource.getInstance().getLocation().getTarget());

        try {
            otid_t otid = Utility.getCurrentOtid();
            ResourceServiceGrpc.ResourceServiceBlockingStub stub = ResourceServiceGrpc.newBlockingStub(channel);

            return action.apply(otid, stub);

        } finally {
            channel.shutdown();
        }
    }
}

