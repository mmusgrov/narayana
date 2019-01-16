package org.jboss.narayana.grpc.server;

import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.OTSStatusValue;
import org.jboss.narayana.ots.grpc.RecoveryCoordinatorServiceGrpc;
import org.jboss.narayana.ots.grpc.ResourceId;
import org.jboss.narayana.ots.grpc.Status;

public class RecoveryCoordinatorServiceImpl extends RecoveryCoordinatorServiceGrpc.RecoveryCoordinatorServiceImplBase {
    private final String target;

    public RecoveryCoordinatorServiceImpl(String target) {
        this.target = target;
    }

    @Override
    public void replayCompletion(ResourceId request, StreamObserver<OTSStatusValue> responseObserver) {
        System.out.println(request);

        OTSStatusValue response = OTSStatusValue.newBuilder()
                .setResult(Status.StatusActive)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
