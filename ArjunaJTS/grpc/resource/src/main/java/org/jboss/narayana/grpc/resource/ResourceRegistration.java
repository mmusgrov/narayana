package org.jboss.narayana.grpc.resource;

import org.jboss.narayana.ots.grpc.ServiceInstance;
import org.jboss.narayana.ots.grpc.otid_t;

class ResourceRegistration {
    private otid_t current;
    private ServiceInstance recovery;
    private Resource resourceCallback;

    public otid_t getCurrent() {
        return current;
    }

    public ServiceInstance getRecovery() {
        return recovery;
    }

    public Resource getResourceCallback() {
        return resourceCallback;
    }

    public ResourceRegistration(otid_t current, ServiceInstance recovery, Resource resourceCallback) {
        this.current = current;
        this.recovery = recovery;
        this.resourceCallback = resourceCallback;
    }
}
