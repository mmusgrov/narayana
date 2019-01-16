package org.jboss.narayana.grpc.resource;

public interface Resource {
    LocalVote prepare(Otid otid);
    void rollback(Otid otid) throws ResourceException; //SystemException, HeuristicCommit, HeuristicMixed, HeuristicHazard;
    void commit(Otid otid) throws ResourceException; //SystemException, NotPrepared, HeuristicRollback, HeuristicMixed, HeuristicHazard;
    void forget(Otid otid) throws ResourceException; //SystemException;
    void commit_one_phase(Otid otid) throws ResourceException; //HeuristicHazard, SystemException;
}
