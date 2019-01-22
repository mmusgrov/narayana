package org.jboss.narayana.grpc.server;

import com.arjuna.ats.internal.jts.OTSImpleManager;
import com.arjuna.ats.internal.jts.orbspecific.CurrentImple;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.jboss.narayana.ots.grpc.Connection;
import org.jboss.narayana.ots.grpc.ControlResponse;
import org.jboss.narayana.ots.grpc.ExceptionResponse;
import org.jboss.narayana.ots.grpc.OTSException;
import org.jboss.narayana.ots.grpc.otid_t;
import org.omg.CORBA.Any;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.UserException;
import org.omg.CORBA.portable.UnknownException;
import org.omg.CosTransactions.Control;
import org.omg.CosTransactions.HeuristicCommit;
import org.omg.CosTransactions.HeuristicHazard;
import org.omg.CosTransactions.HeuristicMixed;
import org.omg.CosTransactions.HeuristicRollback;
import org.omg.CosTransactions.Inactive;
import org.omg.CosTransactions.InvalidControl;
import org.omg.CosTransactions.NoTransaction;
import org.omg.CosTransactions.NotPrepared;
import org.omg.CosTransactions.NotSubtransaction;
import org.omg.CosTransactions.SubtransactionsUnavailable;
import org.omg.CosTransactions.SynchronizationUnavailable;
import org.omg.CosTransactions.TransIdentity;
import org.omg.CosTransactions.Unavailable;

public class Utility {
    private Utility() {
    }

    static otid_t toGrpcOtid(org.omg.CosTransactions.otid_t ots_otid) {
        return otid_t.newBuilder()
                .setFormatID(ots_otid.formatID)
                .setBqualLength(ots_otid.bqual_length)
                .setTid(ByteString.copyFrom(ots_otid.tid))
                .build();
    }

    static org.omg.CosTransactions.otid_t toOTSid(otid_t otid) {
        return new org.omg.CosTransactions.otid_t(
                otid.getFormatID(), otid.getBqualLength(), otid.getTid().toByteArray());
    }

    static Any toOtsAny(org.omg.CORBA.ORB orb, ByteString opaqueData) {
        Any any = orb.create_any();
        any.insert_wstring(new String(opaqueData.toByteArray()));

        return any;
    }

    static TransIdentity getTransaction(otid_t grpcOtsId) throws NoTransaction, Unavailable {
        org.omg.CosTransactions.otid_t otsid = Utility.toOTSid(grpcOtsId);

        Control control = OTSImpleManager.factory().getTransaction(otsid);
        return new TransIdentity(control.get_coordinator(), control.get_terminator(), otsid);
    }

    public static ControlResponse buildControlResponse(otid_t otid, Connection coordinator, Connection terminator) {
        return ControlResponse.newBuilder()
                .setCurrent(otid)
                .setCoordinator(coordinator)
                .setTerminator(terminator)
                .build();
    }

    public static void observeExceptionResponse(StreamObserver<ExceptionResponse> responseObserver, OTSException noTransaction) {
        responseObserver.onNext(ExceptionResponse.newBuilder().setExceptions(0, noTransaction).build());
        responseObserver.onCompleted();
    }

    static Connection getConnection(String target) {
        return Connection.newBuilder()
                .setTarget(target)
                .build();
    }

    static ManagedChannel getChannel(String target) {
        return ManagedChannelBuilder
                .forTarget(target)
                .usePlaintext() // otherwise configure TLS (https://github.com/grpc/grpc-java/blob/master/SECURITY.md)
                .build();
    }

    static otid_t getCurrentOtid() throws SystemException {
        CurrentImple current = OTSImpleManager.current();
        Control control = current.get_control();
        org.omg.CosTransactions.otid_t ots_otid = null;

        try {
            if (control == null) {
                throw new UnknownException(new Exception("No active transaction"));
            }

            ots_otid = control.get_coordinator().get_txcontext().current.otid;
        } catch (Unavailable unavailable) {
            throw new UnknownException(unavailable);
        }

        return Utility.toGrpcOtid(ots_otid);
    }

    static org.omg.CORBA.UserException checkForException(ExceptionResponse response) {
        if (response.getExceptionsCount() != 0) {

            switch (response.getExceptions(0).ordinal()) {
                case OTSException.HeuristicRollback_VALUE:
                    return new HeuristicRollback();
                case OTSException.HeuristicCommit_VALUE:
                    return new HeuristicCommit();
                case OTSException.HeuristicMixed_VALUE:
                    return new HeuristicMixed();
                case OTSException.HeuristicHazard_VALUE:
                    return new HeuristicHazard();
                case OTSException.SubtransactionsUnavailable_VALUE:
                    return new SubtransactionsUnavailable();
                case OTSException.NotSubtransaction_VALUE:
                    return new NotSubtransaction();
                case OTSException.Inactive_VALUE:
                    return new Inactive();
                case OTSException.NotPrepared_VALUE:
                    return new NotPrepared();
                case OTSException.NoTransaction_VALUE:
                    return new NoTransaction();
                case OTSException.InvalidControl_VALUE:
                    return new InvalidControl();
                case OTSException.Unavailable_VALUE:
                    return new Unavailable();
                case OTSException.SynchronizationUnavailable_VALUE:
                    return new SynchronizationUnavailable();
                default:
//                    return new SystemException();
            }
        }

        return null;
    }
}
