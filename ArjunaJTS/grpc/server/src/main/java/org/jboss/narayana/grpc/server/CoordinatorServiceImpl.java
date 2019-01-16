package org.jboss.narayana.grpc.server;

import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.ControlResponse;
import org.jboss.narayana.ots.grpc.CoordinatorRelationshipRequest;
import org.jboss.narayana.ots.grpc.CoordinatorResourceRequest;
import org.jboss.narayana.ots.grpc.CoordinatorServiceGrpc;
import org.jboss.narayana.ots.grpc.CoordinatorSubtransactionAwareResourceRequest;
import org.jboss.narayana.ots.grpc.CoordinatorSynchronizationRequest;
import org.jboss.narayana.ots.grpc.ExceptionResponse;
import org.jboss.narayana.ots.grpc.OTSBoolValue;
import org.jboss.narayana.ots.grpc.OTSException;
import org.jboss.narayana.ots.grpc.OTSLongValue;
import org.jboss.narayana.ots.grpc.OTSRecoveryCoordinatorResponse;
import org.jboss.narayana.ots.grpc.OTSStatusValue;
import org.jboss.narayana.ots.grpc.PropagationContextResponse;
import org.jboss.narayana.ots.grpc.RecoveryCoordinatorId;
import org.jboss.narayana.ots.grpc.ResourceId;
import org.jboss.narayana.ots.grpc.ServiceInstance;
import org.jboss.narayana.ots.grpc.Status;
import org.jboss.narayana.ots.grpc.VoidResponse;
import org.jboss.narayana.ots.grpc.otid_t;
import org.omg.CosTransactions.Inactive;
import org.omg.CosTransactions.NoTransaction;
import org.omg.CosTransactions.RecoveryCoordinator;
import org.omg.CosTransactions.TransIdentity;
import org.omg.CosTransactions.Unavailable;

public class CoordinatorServiceImpl extends CoordinatorServiceGrpc.CoordinatorServiceImplBase {
    private final String target;

    public CoordinatorServiceImpl(String target) {
        this.target = target;
    }

    @Override
    public void getStatus(otid_t request, StreamObserver<OTSStatusValue> responseObserver) {
        OTSStatusValue response = OTSStatusValue.newBuilder()
                .setResult(Status.StatusActive)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();    }

    @Override
    public void getParentStatus(otid_t request, StreamObserver<OTSStatusValue> responseObserver) {
        super.getParentStatus(request, responseObserver);
    }

    @Override
    public void getTopLevelStatus(otid_t request, StreamObserver<OTSStatusValue> responseObserver) {
        super.getTopLevelStatus(request, responseObserver);
    }

    @Override
    public void isSameTransaction(CoordinatorRelationshipRequest request, StreamObserver<OTSBoolValue> responseObserver) {
        super.isSameTransaction(request, responseObserver);
    }

    @Override
    public void isAncestorTransaction(CoordinatorRelationshipRequest request, StreamObserver<OTSBoolValue> responseObserver) {
        super.isAncestorTransaction(request, responseObserver);
    }

    @Override
    public void isDescendantTransaction(CoordinatorRelationshipRequest request, StreamObserver<OTSBoolValue> responseObserver) {
        super.isDescendantTransaction(request, responseObserver);
    }

    @Override
    public void isRelatedTransaction(CoordinatorRelationshipRequest request, StreamObserver<OTSBoolValue> responseObserver) {
        super.isRelatedTransaction(request, responseObserver);
    }

    @Override
    public void isTopLevelTransaction(otid_t request, StreamObserver<OTSBoolValue> responseObserver) {
        super.isTopLevelTransaction(request, responseObserver);
    }

    @Override
    public void hashTransaction(otid_t request, StreamObserver<OTSLongValue> responseObserver) {
        super.hashTransaction(request, responseObserver);
    }

    @Override
    public void hashTopLevelTran(otid_t request, StreamObserver<OTSLongValue> responseObserver) {
        super.hashTopLevelTran(request, responseObserver);
    }

    @Override
    public void registerResource(CoordinatorResourceRequest request, StreamObserver<OTSRecoveryCoordinatorResponse> responseObserver) {
        otid_t current = request.getCurrent();
        ResourceId resourceId = request.getR();

        try {
            TransIdentity txn = Utility.getTransaction(current);
            ProxyResource resource = new ProxyResource(resourceId);

            RecoveryCoordinator rc = txn.coord.register_resource(resource.getResource());
            RecoveryCoordinatorId grpcRc = RecoveryCoordinatorId.newBuilder()
                    .setInstance(ServiceInstance.newBuilder()
                            .setUid(rc.toString())
                            .setLocation(Utility.getConnection(target)).build())
                    .build();

            responseObserver.onNext(OTSRecoveryCoordinatorResponse.newBuilder().setId(grpcRc).build());
            responseObserver.onCompleted();
        } catch (NoTransaction noTransaction) {
            responseObserver.onNext(OTSRecoveryCoordinatorResponse.newBuilder().setExceptions(0, OTSException.NoTransaction).build());
            responseObserver.onCompleted();
        } catch (Unavailable unavailable) {
            responseObserver.onNext(OTSRecoveryCoordinatorResponse.newBuilder().setExceptions(0, OTSException.Unavailable).build());
            responseObserver.onCompleted();
        } catch (Inactive inactive) {
            responseObserver.onNext(OTSRecoveryCoordinatorResponse.newBuilder().setExceptions(0, OTSException.Inactive).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void registerSynchronization(CoordinatorSynchronizationRequest request, StreamObserver<ExceptionResponse> responseObserver) {
        super.registerSynchronization(request, responseObserver);
    }

    @Override
    public void registerSubtranAware(CoordinatorSubtransactionAwareResourceRequest request, StreamObserver<ExceptionResponse> responseObserver) {
        super.registerSubtranAware(request, responseObserver);
    }

    @Override
    public void rollbackOnly(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        super.rollbackOnly(request, responseObserver);
    }

    @Override
    public void getTransactionName(otid_t request, StreamObserver<VoidResponse> responseObserver) {
        super.getTransactionName(request, responseObserver);
    }

    @Override
    public void createSubtransaction(otid_t request, StreamObserver<ControlResponse> responseObserver) {
        super.createSubtransaction(request, responseObserver);
    }

    @Override
    public void getTxcontext(otid_t request, StreamObserver<PropagationContextResponse> responseObserver) {
        super.getTxcontext(request, responseObserver);
    }
}
