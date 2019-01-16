package org.jboss.narayana.grpc.resource;

public enum LocalVote {
    VoteCommit,
    VoteRollback,
    VoteReadOnly;
}
