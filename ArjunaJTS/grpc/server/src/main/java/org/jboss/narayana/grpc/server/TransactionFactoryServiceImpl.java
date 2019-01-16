package org.jboss.narayana.grpc.server;

import com.arjuna.ats.internal.jts.OTSImpleManager;
import com.arjuna.orbportability.ORB;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.Connection;
import org.jboss.narayana.ots.grpc.ControlResponse;
import org.jboss.narayana.ots.grpc.OTSException;
import org.jboss.narayana.ots.grpc.PropagationContext;
import org.jboss.narayana.ots.grpc.TransactionFactoryServiceGrpc;
import org.jboss.narayana.ots.grpc.ULongValue;
import org.jboss.narayana.ots.grpc.otid_t;
import org.omg.CosTransactions.Control;
import org.omg.CosTransactions.NoTransaction;
import org.omg.CosTransactions.TransIdentity;
import org.omg.CosTransactions.Unavailable;

import java.util.List;

public class TransactionFactoryServiceImpl extends TransactionFactoryServiceGrpc.TransactionFactoryServiceImplBase {
    private final String target;

    public TransactionFactoryServiceImpl(String target) {
        this.target = target;
    }

    @Override
    public void create(ULongValue request, StreamObserver<ControlResponse> responseObserver) {
//        UserTransaction utx = com.arjuna.ats.jta.UserTransaction.userTransaction();
//        Control control = OTSImpleManager.current().get_control();

        Control control = OTSImpleManager.factory().create(request.getTimeOut());
        ControlResponse.Builder builder = ControlResponse.newBuilder();
        ControlResponse response;

        try {
            org.omg.CosTransactions.otid_t ots_otid = control.get_coordinator().get_txcontext().current.otid;
            otid_t otid = Utility.toGrpcOtid(ots_otid);

            Connection connection = Connection.newBuilder()
                    .setTarget(target)
                    .build();

            response = Utility.buildControlResponse(otid, connection, connection);

        } catch (Unavailable unavailable) {
           // builder.set
            response = builder
                    .setExceptions(0, OTSException.Unavailable)
                    .build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void recreate(PropagationContext request, StreamObserver<ControlResponse> responseObserver) {
        ControlResponse response;
        ByteString tid = ByteString.copyFrom("tid".getBytes());

        int timeout = request.getTimeout();
        otid_t current = request.getCurrent();
        List<otid_t> parents = request.getParentsList();
        ByteString opaqueData = request.getImplementationSpecificData();
        org.omg.CORBA.Any any = Utility.toOtsAny(ORB.getInstance("ots").orb(), opaqueData);
        org.omg.CosTransactions.otid_t otsid = Utility.toOTSid(current);

        try {
//            Control control = OTSImpleManager.factory().getTransaction(otsid);
            TransIdentity ti = Utility.getTransaction(current);
            TransIdentity[] otsParents = new TransIdentity[parents.size()];
            int index = 0;

            for (otid_t id : parents) {
                otsParents[index++] = Utility.getTransaction(id);
            }

            org.omg.CosTransactions.PropagationContext ctx = new org.omg.CosTransactions.PropagationContext(
                    timeout, ti, otsParents, any);

            Control control = OTSImpleManager.factory().recreate(ctx);

            org.omg.CosTransactions.otid_t ots_otid = control.get_coordinator().get_txcontext().current.otid;
            otid_t otid = Utility.toGrpcOtid(ots_otid);

            Connection connection = Connection.newBuilder()
                    .setTarget(target)
                    .build();

            response = Utility.buildControlResponse(otid, connection, connection);
        } catch (Unavailable unavailable) {
            response = ControlResponse.newBuilder().setExceptions(0, OTSException.Unavailable).build();
            unavailable.printStackTrace();
        } catch (NoTransaction noTransaction) {
            response = ControlResponse.newBuilder().setExceptions(0, OTSException.NoTransaction).build();
            noTransaction.printStackTrace();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}