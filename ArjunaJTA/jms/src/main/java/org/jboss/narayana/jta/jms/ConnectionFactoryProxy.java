/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.narayana.jta.jms;

import com.arjuna.ats.jta.logging.jtaLogger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.XAConnectionFactory;
import javax.jms.XAJMSContext;
import java.util.function.Supplier;

/**
 * Proxy connection factory to wrap around provided {@link XAConnectionFactory}.
 *
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class ConnectionFactoryProxy implements ConnectionFactory {

    private final XAConnectionFactory xaConnectionFactory;

    private final TransactionHelper transactionHelper;

    /**
     * @param xaConnectionFactory factory to get XA connection instances, not null.
     * @param transactionHelper utility to make transaction resources registration easier.
     */
    public ConnectionFactoryProxy(XAConnectionFactory xaConnectionFactory, TransactionHelper transactionHelper) {
        this.xaConnectionFactory = xaConnectionFactory;
        this.transactionHelper = transactionHelper;
    }

    /**
     * Get XA connection from the provided factory and wrap it with {@link ConnectionProxy}.
     *
     * @return XA connection wrapped with {@link ConnectionProxy}.
     * @throws JMSException if failure occurred creating XA connection.
     */
    @Override
    public Connection createConnection() throws JMSException {
        Connection connection = new ConnectionProxy(xaConnectionFactory.createXAConnection(), transactionHelper);

        if (jtaLogger.logger.isTraceEnabled()) {
            jtaLogger.logger.trace("Created new proxied connection: " + connection);
        }

        return connection;
    }

    /**
     * Get XA connection from the provided factory with credentials and wrap it with {@link ConnectionProxy}.
     * 
     * @param userName
     * @param password
     * @return XA connection wrapped with {@link ConnectionProxy}.
     * @throws JMSException if failure occurred creating XA connection.
     */
    @Override
    public Connection createConnection(String userName, String password) throws JMSException {
        Connection connection = new ConnectionProxy(xaConnectionFactory.createXAConnection(userName, password),
                transactionHelper);

        if (jtaLogger.logger.isTraceEnabled()) {
            jtaLogger.logger.trace("Created new proxied connection: " + connection);
        }

        return connection;
    }

    public JMSContext createContext(Supplier<XAJMSContext> contextSupplier) {
        try {
            if (transactionHelper.isTransactionAvailable()) {
                XAJMSContext context = contextSupplier.get();

                transactionHelper.registerXAResource(context.getXAResource());

                return new ContextProxy(context, transactionHelper);
            }

            return new ContextProxy(contextSupplier.get(), transactionHelper);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage());
        }
    }

    @Override
    public JMSContext createContext() {
        return createContext(xaConnectionFactory::createXAContext);
    }

    @Override
    public JMSContext createContext(String userName, String password) {
        return createContext(() -> xaConnectionFactory.createXAContext(userName, password));
    }

    @Override
    public JMSContext createContext(String userName, String password, int sessionMode) {
        // note that in the following call chain there is an intermediate createXAContext which is not explicitly closed
        // the assumption is closing the second context cleans up any resources
        return createContext(() -> xaConnectionFactory.createXAContext(userName, password)).createContext(sessionMode);
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        // note that in the following call chain there is an intermediate createXAContext which is not explicitly closed
        // the assumption is closing the second context cleans up any resources
        return createContext(xaConnectionFactory::createXAContext).createContext(sessionMode);
    }
}
