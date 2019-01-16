package org.jboss.narayana.grpc.resource;

import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.SyncACRequest;
import org.jboss.narayana.ots.grpc.SynchronizationServiceGrpc;
import org.jboss.narayana.ots.grpc.VoidResponse;
import org.jboss.narayana.ots.grpc.otid_t;

public class SynchronizationServiceImpl extends SynchronizationServiceGrpc.SynchronizationServiceImplBase {
    private final String target;

    public SynchronizationServiceImpl(String target) {
        this.target = target;
    }

    @Override
    public void beforeCompletion(otid_t request, StreamObserver<VoidResponse> responseObserver) {
        super.beforeCompletion(request, responseObserver);
    }

    @Override
    public void afterCompletion(SyncACRequest request, StreamObserver<VoidResponse> responseObserver) {
        super.afterCompletion(request, responseObserver);
    }
}
