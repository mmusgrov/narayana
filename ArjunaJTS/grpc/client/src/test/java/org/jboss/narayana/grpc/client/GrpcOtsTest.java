package org.jboss.narayana.grpc.client;

import io.grpc.ManagedChannel;
import org.jboss.narayana.grpc.server.OTSGrpcServer;
import org.jboss.narayana.grpc.resource.ResourceServiceImpl;
import org.jboss.narayana.ots.grpc.ControlResponse;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.jboss.narayana.grpc.client.OTSGrpcClient.GRPC_RESOURCE_SERVER_NAME;
import static org.jboss.narayana.grpc.server.OTSGrpcServer.GRPC_CONTROL_SERVER_NAME;

public class GrpcOtsTest {
    private static final Logger logger = Logger.getLogger(OTSGrpcClient.class.getName());

    private static final int SERVER_PORT = 8080;
    private static final int CLIENT_PORT = 8081;
    private static final String CLIENT_TARGET = String.format("localhost:%d", CLIENT_PORT);
    private static final String SERVER_TARGET = String.format("localhost:%d", SERVER_PORT);

    private static OTSGrpcServer otsGrpcServer;

    private static OTSGrpcClient otsClient;
    private static ManagedChannel serverChannel;
    private static OTSGrpcResourceServer grpcServer;

    @BeforeClass
    public static void beforeClass() throws Exception {
        otsGrpcServer = OTSGrpcServer.startControlServices(GRPC_CONTROL_SERVER_NAME, SERVER_PORT);
        startClientServices();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        stopClientServices();
        otsGrpcServer.stopServices();
    }

    private static void startClientServices() throws Exception {
        otsClient = new OTSGrpcClient();

        serverChannel = OTSGrpcClient.getChannel(SERVER_TARGET);

        /* start a grpcServer so that we can receive resource callbacks */
        grpcServer = otsClient.startResourceServices(GRPC_RESOURCE_SERVER_NAME, CLIENT_TARGET, CLIENT_PORT);

        logger.info(String.format(GRPC_RESOURCE_SERVER_NAME + " is ready%n"));
    }

    private static void stopClientServices() throws InterruptedException {
        logger.info(String.format(GRPC_RESOURCE_SERVER_NAME + " is shutting down ...%n"));

        serverChannel.shutdownNow();
        serverChannel.awaitTermination(1, TimeUnit.SECONDS);

        if (grpcServer != null) {
            grpcServer.stopServices();
        }
    }

    @Before
    public void beforeTest() {
        otsClient.getResourceService().resetCounts();
    }

    @Test
    public void testTransaction() throws Exception {
        ControlResponse control = otsClient.transactionFactoryCreate(serverChannel);

        otsClient.commitTransaction(serverChannel, control);
    }

    @Test
    public void testOnePhaseResource() throws Exception {
        TestResource resource = new TestResource();
        doInTransaction(serverChannel, resource);

        // verify that the resources were prepared and committed the correct number of times
        ResourceServiceImpl resourceService = otsClient.getResourceService();

        Assert.assertEquals("commitOnePhase wasn't called", 1, resourceService.getCommitOnePhaseCount());
        Assert.assertEquals("commit resource should not have been called", 0, resourceService.getCommitCount());
        Assert.assertEquals("prepare resource should not have been called", 0, resourceService.getPrepareCount());
    }

    @Test
    public void testTwoResources() throws Exception {
        TestResource[] resources = {new TestResource(), new TestResource()};

        doInTransaction(serverChannel, resources);

        // verify that the resources were prepared and committed the correct number of times
        ResourceServiceImpl resourceService = otsClient.getResourceService();

        for (TestResource resource : resources) {
            Assert.assertEquals("commitOnePhase wasn't called", 1, resource.getCommitOnePhaseCount());
            Assert.assertEquals("commit resource should not have been called", 0, resource.getCommitCount());
            Assert.assertEquals("prepare resource should not have been called", 0, resource.getPrepareCount());
        }

        // Assert.assertEquals("one or more resources wern't prepared", 2, resourceService.getPrepareCount());
        // Assert.assertEquals("one or more resources wern't committed", 2, resourceService.getCommitCount());
    }

    private void doInTransaction(ManagedChannel channel, TestResource...resources) throws Exception {
        ControlResponse control = otsClient.transactionFactoryCreate(channel);

        for (TestResource resource : resources) {
            otsClient.registerResource(control, resource);
        }

        otsClient.commitTransaction(channel, control);
    }

}
