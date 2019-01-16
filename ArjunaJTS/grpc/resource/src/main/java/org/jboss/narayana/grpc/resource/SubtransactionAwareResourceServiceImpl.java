package org.jboss.narayana.grpc.resource;

import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.CommitSubTxnRequest;
import org.jboss.narayana.ots.grpc.ExceptionResponse;
import org.jboss.narayana.ots.grpc.SubtransactionAwareResourceServiceGrpc;
import org.jboss.narayana.ots.grpc.VoidResponse;
import org.jboss.narayana.ots.grpc.VoteResponse;
import org.jboss.narayana.ots.grpc.otid_t;

public class SubtransactionAwareResourceServiceImpl extends SubtransactionAwareResourceServiceGrpc.SubtransactionAwareResourceServiceImplBase {
    private final String target;

    public SubtransactionAwareResourceServiceImpl(String target) {
        this.target = target;
    }

    @Override
    public void prepare(otid_t request, StreamObserver<VoteResponse> responseObserver) {
        super.prepare(request, responseObserver);
    }

    @Override
    public void rollback(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        super.rollback(request, responseObserver);
    }

    @Override
    public void commit(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        super.commit(request, responseObserver);
    }

    @Override
    public void commitOnePhase(otid_t request, StreamObserver<ExceptionResponse> responseObserver) {
        super.commitOnePhase(request, responseObserver);
    }

    @Override
    public void forget(otid_t request, StreamObserver<VoidResponse> responseObserver) {
        super.forget(request, responseObserver);
    }

    @Override
    public void commitSubtransaction(CommitSubTxnRequest request, StreamObserver<VoidResponse> responseObserver) {
        super.commitSubtransaction(request, responseObserver);
    }

    @Override
    public void rollbackSubtransaction(otid_t request, StreamObserver<VoidResponse> responseObserver) {
        super.rollbackSubtransaction(request, responseObserver);
    }
}
