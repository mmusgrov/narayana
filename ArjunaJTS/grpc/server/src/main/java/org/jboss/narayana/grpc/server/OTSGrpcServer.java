package org.jboss.narayana.grpc.server;

import com.arjuna.ats.internal.jts.orbspecific.javaidl.recoverycoordinators.JavaIdlRCServiceInit;
import com.arjuna.ats.internal.jts.orbspecific.javaidl.recoverycoordinators.JavaIdlRecoveryInit;
import com.arjuna.ats.internal.jts.recovery.RecoveryCreator;
import com.arjuna.ats.internal.jts.recovery.recoverycoordinators.GenericRecoveryCreator;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import com.arjuna.orbportability.common.OrbPortabilityEnvironmentBean;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.logging.Logger;
import java.util.stream.Stream;

public class OTSGrpcServer {
  private static final Logger logger = Logger.getLogger(OTSGrpcServer.class.getName());

  public static final String GRPC_CONTROL_SERVER_NAME = "grpc-ots-control-server"; // default server name

  private static final String JDKORB_CLASSNAME = com.arjuna.orbportability.internal.orbspecific.javaidl.orb.implementations.javaidl_1_4.class.getName();
  private static final String JDKORBDATA_CLASSNAME = com.arjuna.orbportability.internal.orbspecific.versions.javaidl_1_4.class.getName();

  private static final int CONTROL_SERVER_PORT = 8080;
  private static final String CONTROL_SERVER_TARGET = String.format("localhost:%d", CONTROL_SERVER_PORT);

  private final String name;
  private Server server;

  private OTSGrpcServer(String name) {
    this.name = name;
  }

  public static void main(String[] args) throws Exception {
    startControlServices(GRPC_CONTROL_SERVER_NAME, CONTROL_SERVER_PORT).awaitTermination();
  }

  public static OTSGrpcServer startControlServices(String name, int port) throws Exception {
    OTSGrpcServer.initTM();

    return new OTSGrpcServer(name).start(port,
            new RecoveryCoordinatorServiceImpl(CONTROL_SERVER_TARGET),
            new TransactionFactoryServiceImpl(CONTROL_SERVER_TARGET),
            new TerminatorServiceImpl(CONTROL_SERVER_TARGET),
            new CoordinatorServiceImpl(CONTROL_SERVER_TARGET));
  }

  public void stopServices() throws InterruptedException {
    server.shutdown();
    server.awaitTermination();
  }

  private OTSGrpcServer start(int port, BindableService... services) throws Exception {
    ServerBuilder builder = ServerBuilder.forPort(port);
    Stream.of(services).forEach(builder::addService);

    server = builder.build().start();

    logger.info(String.format("%s is ready", name));

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println(name + " is shutting down");
      try {
        OTSGrpcServer.this.stopServices();
      } catch (InterruptedException ignore) {
      } finally {
        JavaIdlRCServiceInit.shutdownRCService();
        System.err.println(name + " is shut down");
      }
    }));

    return this;
  }

  private static void initTM() throws Exception {
    BeanPopulator.getDefaultInstance(OrbPortabilityEnvironmentBean.class).setOrbImpleClassName(JDKORB_CLASSNAME);
    BeanPopulator.getDefaultInstance(OrbPortabilityEnvironmentBean.class).setOrbDataClassName(JDKORBDATA_CLASSNAME);

    JavaIdlRCServiceInit init = new JavaIdlRCServiceInit();
    JavaIdlRecoveryInit rinit = new JavaIdlRecoveryInit();

    init.startRCservice();

    RecoveryCreator creator = RecoveryCreator.getCreator();

    if (!(creator instanceof GenericRecoveryCreator)) {
      throw new Exception("Could not create recovery coordinator%n");
    }
  }

  private void shutdown() {
    server.shutdown();
  }

  private void awaitTermination() throws InterruptedException {
    server.awaitTermination();
  }
}
