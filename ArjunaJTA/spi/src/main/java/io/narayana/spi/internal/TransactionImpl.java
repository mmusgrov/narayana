/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package io.narayana.spi.internal;

import javax.transaction.*;
import javax.transaction.xa.XAResource;

public class TransactionImpl implements javax.transaction.Transaction {
    private TransactionManager tm;

    TransactionImpl(TransactionManager tm) throws SystemException, NotSupportedException {
        this.tm = tm;
        tm.begin();
    }

    TransactionImpl(TransactionManager tm, int timeout) throws SystemException, NotSupportedException {
        this.tm = tm;
        tm.setTransactionTimeout(timeout);
        tm.begin();
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
        tm.commit();
    }

    @Override
    public void rollback() throws IllegalStateException, SystemException {
        tm.rollback();
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        tm.setRollbackOnly();
    }

    @Override
    public int getStatus() throws SystemException {
        return tm.getStatus();
    }

    @Override
    public boolean enlistResource(XAResource xaResource) throws RollbackException, IllegalStateException, SystemException {
        return tm.getTransaction().enlistResource(xaResource);
    }

    @Override
    public boolean delistResource(XAResource xaResource, int i) throws IllegalStateException, SystemException {
        return tm.getTransaction().delistResource(xaResource, i);
    }

    @Override
    public void registerSynchronization(Synchronization synchronization) throws RollbackException, IllegalStateException, SystemException {
        tm.getTransaction().registerSynchronization(synchronization);
    }
}
