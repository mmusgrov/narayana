package org.jboss.narayana.grpc.client;

import io.grpc.ManagedChannel;
import org.jboss.narayana.grpc.resource.Resource;
import org.jboss.narayana.grpc.resource.ResourceException;
import org.jboss.narayana.ots.grpc.ControlResponse;
import org.jboss.narayana.ots.grpc.ServiceInstance;

public interface OTSGrpcClientAPI {
  ServiceInstance registerResource(ControlResponse control, Resource resourceCallback) throws ResourceException;
  void commitTransaction(ManagedChannel channel, ControlResponse control) throws Exception;
  ControlResponse transactionFactoryCreate(ManagedChannel channel) throws Exception;
}
