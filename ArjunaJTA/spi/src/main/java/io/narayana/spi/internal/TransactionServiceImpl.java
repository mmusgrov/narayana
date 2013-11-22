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

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import io.narayana.spi.*;

import javax.naming.NamingException;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

public class TransactionServiceImpl implements TransactionService {

    private TransactionManager tm;
    private EnvironmentConfig environmentConfig;

    /**
     * Get the TransactionService configuration.
     *
     * Any configuration changes must be made before creating the transaction service otherwise they
     * will have no effect
     *
     * @return the current configuration
     */
    public EnvironmentConfig getEnvironment() {
        return environmentConfig;
    }

    public TransactionServiceImpl() throws ConfigurationException {
        final JTAEnvironmentBean jtaEnvironmentBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);

        tm = jtaEnvironmentBean.getTransactionManager();
        environmentConfig = TransactionServiceFactory.getDefaultEnvironment();

        try {
            DataSourceManagerImpl.initJndi(environmentConfig.getVarDir());
        } catch (NamingException e) {
            throw new ConfigurationException(ConfigurationException.REASON.JNDI, e.getMessage(), e);
        }

//        RecoveryManager.manager();
    }

    /**
     * Create a running transaction
     *
     * @return an interface for managing the transaction
     *
     * @throws javax.transaction.SystemException
     * @throws javax.transaction.NotSupportedException
     */
    public Transaction beginTransaction() throws SystemException, NotSupportedException {
        return new TransactionImpl(tm);
    }

    /**
     * Create a running transaction
     *
     * @param timeout the maximum period, in seconds, that the transaction will remain active
     *
     * @return an interface for managing the transaction
     *
     * @throws javax.transaction.SystemException
     * @throws javax.transaction.NotSupportedException
     */
    public Transaction beginTransaction(int timeout) throws SystemException, NotSupportedException {
        return new TransactionImpl(tm, timeout);
    }

    @Override
    public void close() throws Exception {
//        RecoveryManager.manager().terminate();
    }
}