package org.jboss.narayana.grpc.server;

import com.arjuna.ats.internal.jts.OTSImpleManager;
import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.ExceptionResponse;
import org.jboss.narayana.ots.grpc.OTSException;
import org.jboss.narayana.ots.grpc.TerminatorRequest;
import org.jboss.narayana.ots.grpc.TerminatorServiceGrpc;
import org.jboss.narayana.ots.grpc.VoidResponse;
import org.jboss.narayana.ots.grpc.otid_t;
import org.omg.CosTransactions.Control;
import org.omg.CosTransactions.HeuristicHazard;
import org.omg.CosTransactions.HeuristicMixed;
import org.omg.CosTransactions.InvalidControl;
import org.omg.CosTransactions.NoTransaction;
import org.omg.CosTransactions.TransIdentity;
import org.omg.CosTransactions.Unavailable;

public class TerminatorServiceImpl extends TerminatorServiceGrpc.TerminatorServiceImplBase {
    private final String target;

    public TerminatorServiceImpl(String target) {
        this.target = target;
    }

    @Override
    public void commit(TerminatorRequest request, StreamObserver<ExceptionResponse> responseObserver) {
        otid_t otid = request.getCurrent();
        Control control = null;

        try {
            TransIdentity txn = Utility.getTransaction(otid);

            control = OTSImpleManager.factory().getTransaction(Utility.toOTSid(otid));

            OTSImpleManager.current().resume(control);

            txn.term.commit(request.getReportHeuristics());

            responseObserver.onNext(ExceptionResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (NoTransaction noTransaction) {
            Utility.observeExceptionResponse(responseObserver, OTSException.NoTransaction);
        } catch (Unavailable unavailable) {
            Utility.observeExceptionResponse(responseObserver, OTSException.Unavailable);
        } catch (HeuristicMixed heuristicMixed) {
             Utility.observeExceptionResponse(responseObserver, OTSException.HeuristicMixed);
        } catch (HeuristicHazard heuristicHazard) {
             Utility.observeExceptionResponse(responseObserver, OTSException.HeuristicHazard);
        } catch (InvalidControl invalidControl) {
            Utility.observeExceptionResponse(responseObserver, OTSException.InvalidControl);
        } finally {
            OTSImpleManager.current().suspend();
        }
    }

    @Override
    public void rollback(otid_t request, StreamObserver<VoidResponse> responseObserver) {
        super.rollback(request, responseObserver);
    }
}
