package org.jboss.narayana.grpc.resource;

import org.jboss.narayana.ots.grpc.OTSException;

public class ResourceException extends Exception {
    private String name;
    private OTSException exception;

    public ResourceException(String name) {
        this.name = name;
    }

    @Override
    public String getMessage() {
        return exception.name();
    }

    public OTSException getException() {
        return exception;
    }
}
