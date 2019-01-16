package org.jboss.narayana.grpc.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.jboss.narayana.grpc.resource.LocalVote;
import org.jboss.narayana.grpc.resource.Otid;
import org.jboss.narayana.grpc.resource.Resource;
import org.jboss.narayana.grpc.resource.ResourceException;
import org.jboss.narayana.grpc.resource.ResourceServiceImpl;
import org.jboss.narayana.grpc.resource.SubtransactionAwareResourceServiceImpl;
import org.jboss.narayana.grpc.resource.SynchronizationServiceImpl;
import org.jboss.narayana.ots.grpc.Connection;
import org.jboss.narayana.ots.grpc.ControlResponse;
import org.jboss.narayana.ots.grpc.CoordinatorResourceRequest;
import org.jboss.narayana.ots.grpc.CoordinatorServiceGrpc;
import org.jboss.narayana.ots.grpc.ExceptionResponse;
import org.jboss.narayana.ots.grpc.OTSRecoveryCoordinatorResponse;
import org.jboss.narayana.ots.grpc.OTSStatusValue;
import org.jboss.narayana.ots.grpc.RecoveryCoordinatorServiceGrpc;
import org.jboss.narayana.ots.grpc.ResourceId;
import org.jboss.narayana.ots.grpc.ServiceInstance;
import org.jboss.narayana.ots.grpc.Status;
import org.jboss.narayana.ots.grpc.TerminatorRequest;
import org.jboss.narayana.ots.grpc.TerminatorServiceGrpc;
import org.jboss.narayana.ots.grpc.TransactionFactoryServiceGrpc;
import org.jboss.narayana.ots.grpc.ULongValue;
import org.jboss.narayana.ots.grpc.otid_t;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class OTSGrpcClient implements OTSGrpcClientAPI {
  private static final Logger logger = Logger.getLogger(OTSGrpcClient.class.getName());

  static final String GRPC_RESOURCE_SERVER_NAME = "grpc-ots-resource-server";

  private static final int OTS_RESOURCE_SERVER_PORT = 8081;
  private static final String OTS_RESOURCE_SERVER_TARGET = String.format("localhost:%d", OTS_RESOURCE_SERVER_PORT);
  private static final int OTS_TM_SERVER_PORT = 8080;
  private static final String OTS_TM_SERVER_TARGET = String.format("localhost:%d", OTS_TM_SERVER_PORT);

  private ResourceServiceImpl resourceService;
  private SubtransactionAwareResourceServiceImpl subtransactionAwareResourceService;
  private SynchronizationServiceImpl synchronizationService;

  public static void main(String[] args) throws InterruptedException {
    OTSGrpcClient otsClient = new OTSGrpcClient();
    ManagedChannel otsServerChannel = getChannel(OTS_TM_SERVER_TARGET);
    OTSGrpcResourceServer otsGrpcResourceServer = null;
    int resourceCount = 1;

    try {
      /* start a otsGrpcResourceServer so that we can receive resource callbacks */
      otsGrpcResourceServer = otsClient.startResourceServices(GRPC_RESOURCE_SERVER_NAME, OTS_RESOURCE_SERVER_TARGET, OTS_RESOURCE_SERVER_PORT);

      logger.info(String.format(GRPC_RESOURCE_SERVER_NAME + " is ready%n"));

      ControlResponse control = otsClient.transactionFactoryCreate(otsServerChannel);
      Resource resource = new Resource() {
        @Override
        public LocalVote prepare(Otid otid) {
          logger.info("prepare");
          return LocalVote.VoteCommit;
        }

        @Override
        public void rollback(Otid otid) throws ResourceException {
          logger.info("rollback");
        }

        @Override
        public void commit(Otid otid) throws ResourceException {
          logger.info("commit");
        }

        @Override
        public void forget(Otid otid) throws ResourceException {
          logger.info("forget");
        }

        @Override
        public void commit_one_phase(Otid otid) throws ResourceException {
          logger.info("commit_one_phase");
        }
      };

      for (int i = 0; i < resourceCount; i++) {
        otsClient.registerResource(control, resource);
      }

      otsClient.commitTransaction(otsServerChannel, control);

      logger.info(String.format(GRPC_RESOURCE_SERVER_NAME + " is shuting down ...%n"));
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      otsServerChannel.shutdownNow();
      otsServerChannel.awaitTermination(1, TimeUnit.SECONDS);

      if (otsGrpcResourceServer != null) {
        try {
          otsGrpcResourceServer.stopServices();
        } catch (Exception ignore) {
        }
      }
    }
  }

  OTSGrpcResourceServer startResourceServices(String name, String target, int port) throws Exception {
    OTSGrpcResourceServer grpcServer = new OTSGrpcResourceServer(name);

    resourceService = new ResourceServiceImpl(target);
    subtransactionAwareResourceService = new SubtransactionAwareResourceServiceImpl(target);
    synchronizationService = new SynchronizationServiceImpl(target);

    grpcServer.start(port,
            resourceService,
            subtransactionAwareResourceService,
            synchronizationService);

    return grpcServer;
  }

  @Override
  public ServiceInstance registerResource(ControlResponse control, Resource resourceCallback) throws ResourceException {
    ServiceInstance recovery = null;

    if (!resourceService.hasRegistrations(control.getCurrent())) {
      // only register one resource per JVM
      ManagedChannel coordinatorChannel = getChannel(control.getCoordinator().getTarget());

      CoordinatorResourceRequest request = CoordinatorResourceRequest.newBuilder()
              .setCurrent(control.getCurrent())
              .setR(ResourceId.newBuilder()
                      .setInstance(ServiceInstance.newBuilder()
                              .setUid(UUID.randomUUID().toString())
                              .setLocation(getConnection(OTS_RESOURCE_SERVER_TARGET))
                              .build())
                      .build())
              .build();

      CoordinatorServiceGrpc.CoordinatorServiceBlockingStub stub =
              CoordinatorServiceGrpc.newBlockingStub(coordinatorChannel);

      OTSRecoveryCoordinatorResponse recoveryResponse = stub.registerResource(request);

      if (recoveryResponse.getExceptionsCount() != 0) {
        logger.info(String.format("registerResource failed with error: %s%n", recoveryResponse.getExceptions(0)));
        throw new ResourceException(recoveryResponse.getExceptions(0).name());
      } else {
        recovery = recoveryResponse.getId().getInstance();
        logger.info(String.format("registerResource succeeded with response: %s%n", recovery.getUid()));
      }
    }

    // there may be many resource callbacks in this JVM for this control so remember each one so that each
    // can be notified when the single registered resource receives is notified by a remote coordinator that
    // the transaction is ending
    return resourceService.addResourceCallback(control.getCurrent(), recovery, resourceCallback);
  }

  @Override
  public void commitTransaction(ManagedChannel channel, ControlResponse control) throws Exception {
    logger.info(String.format("transaction %s committing ...%n", otidToString(control.getCurrent())));

    TerminatorServiceGrpc.TerminatorServiceBlockingStub stub =
            TerminatorServiceGrpc.newBlockingStub(channel);

    try {
      ExceptionResponse response = stub.commit(
              TerminatorRequest.newBuilder()
                      .setCurrent(control.getCurrent())
                      .setReportHeuristics(true)
                      .build()
      );

      if (response.getExceptionsCount() != 0) {
        throw new Exception(response.getExceptions(0).name());
      }


    } catch (Exception e) {
      logger.info(String.format("transaction %s failed to commit with exception %s%n",
              otidToString(control.getCurrent()),
              e.getMessage()));
    }
  }

  @Override
  public ControlResponse transactionFactoryCreate(ManagedChannel channel) throws Exception {
    TransactionFactoryServiceGrpc.TransactionFactoryServiceBlockingStub stub =
            TransactionFactoryServiceGrpc.newBlockingStub(channel);

    ControlResponse controlResponse = stub.create(
            ULongValue.newBuilder()
                    .setTimeOut(300)
                    .build());

    if (controlResponse.getExceptionsCount() != 0) {
      logger.info(String.format("could not create transaction: %s%n", controlResponse.getExceptions(0)));
      throw new Exception(controlResponse.getExceptions(0).name());
    } else {
      otid_t otid = controlResponse.getCurrent();
      Connection coordinator = controlResponse.getCoordinator();
      Connection terminator = controlResponse.getTerminator();

      logger.info(String.format("created transaction %s with coordinator at %s and terminator at %s%n",
              otidToString(otid),
              coordinator.getTarget(),
              terminator.getTarget()));
    }

    return controlResponse;
  }

  private void recoveryCoordinatorReplayCompletion(ManagedChannel channel) {
    RecoveryCoordinatorServiceGrpc.RecoveryCoordinatorServiceBlockingStub stub =
            RecoveryCoordinatorServiceGrpc.newBlockingStub(channel);

    OTSStatusValue response = stub.replayCompletion(
            ResourceId.newBuilder()
                    .setInstance(getInstance(UUID.randomUUID().toString(), OTS_RESOURCE_SERVER_TARGET)) // the client should be the target of the replay request
                    .build());

    Status status = response.getResult();

    logger.info(String.format("replay completion status: %s%n", status.name()));
  }

  private ServiceInstance getInstance(String uid, String target) {
    return ServiceInstance.newBuilder()
            .setUid(uid)
            .setLocation(Connection.newBuilder()
                    .setTarget(target)
                    .build())
            .build();
  }

  private Connection getConnection(String target) {
    return Connection.newBuilder()
            .setTarget(target)
            .build();
  }

  private String otidToString(otid_t otid) {
    return new String(otid.toByteArray(), StandardCharsets.UTF_8);
  }

  static ManagedChannel getChannel(String target) {
    return ManagedChannelBuilder
            .forTarget(target)
            .usePlaintext() // otherwise configure TLS (https://github.com/grpc/grpc-java/blob/master/SECURITY.md)
            .build();
  }

  ResourceServiceImpl getResourceService() {
    return resourceService;
  }

  SubtransactionAwareResourceServiceImpl getSubtransactionAwareResourceService() {
    return subtransactionAwareResourceService;
  }

  SynchronizationServiceImpl getSynchronizationService() {
    return synchronizationService;
  }
}
