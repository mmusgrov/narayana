package org.jboss.narayana.grpc.client;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.logging.Logger;
import java.util.stream.Stream;

public class OTSGrpcResourceServer {
  private static final Logger logger = Logger.getLogger(OTSGrpcResourceServer.class.getName());
  private static final int SERVER_PORT = 8080;
  private static final String SERVER_TARGET = String.format("localhost:%d", SERVER_PORT);

  private final String name;
  private Server server;

  public OTSGrpcResourceServer(String name) {
    this.name = name;
  }

  public static void main(String[] args) throws Exception {
    startServices("grpc-ots-server", SERVER_PORT).awaitTermination();
  }

  public static OTSGrpcResourceServer startServices(String name, int port) throws Exception {
    OTSGrpcResourceServer otsGrpcResourceServer = new OTSGrpcResourceServer(name);

    return otsGrpcResourceServer;
  }

  public void stopServices() throws InterruptedException {
    server.shutdown();
    server.awaitTermination();
  }

  public void start(int port, BindableService... services) throws Exception {
    ServerBuilder builder = ServerBuilder.forPort(port);
    Stream.of(services).forEach(builder::addService);

    server = builder.build().start();

    logger.info(String.format("%s is ready", name));

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println(name + " is shutting down");
      try {
        OTSGrpcResourceServer.this.stopServices();
      } catch (Exception ignore) {
      }

      System.err.println(name + " is shut down");
    }));
  }

  private void shutdown() {
    server.shutdown();
  }

  private void awaitTermination() throws InterruptedException {
    server.awaitTermination();
  }
}
