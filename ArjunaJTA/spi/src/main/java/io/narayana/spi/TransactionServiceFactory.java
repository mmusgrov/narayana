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
package io.narayana.spi;

import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.jdbc.TransactionalDriver;
import com.arjuna.ats.jdbc.common.jdbcPropertyManager;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.ats.jta.utils.JNDIManager;
import io.narayana.spi.internal.DataSourceManagerImpl;
import io.narayana.spi.internal.DbProps;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

public class TransactionServiceFactory {
    public static final String DB_PROPERTIES_NAME = "db.properties";

    private static RecoveryManager recoveryManager;

    public static synchronized void start(boolean startRecoveryService) throws InitializationException {
        try {
            jdbcPropertyManager.getJDBCEnvironmentBean().setJndiProperties(new InitialContext().getEnvironment());
        } catch (NamingException e) {
            throw new InitializationException("No suitable JNDI provider available", e);
        }

        registerJndiBindings();

        try {
            DriverManager.registerDriver(new TransactionalDriver());
        } catch (SQLException e) {
            throw new InitializationException("Cannot initialize TransactionalDriver", e);
        }

        if (startRecoveryService) {
            RecoveryManager.delayRecoveryManagerThread();

            recoveryManager = RecoveryManager.manager();
        }
    }

    private static void registerJndiBindings() throws InitializationException {

        try {
            JNDIManager.bindJTAImplementation();
        } catch (NamingException e) {
            throw new InitializationException("Unable to bind TM into JNDI", e);
        }

        Map<String, DbProps> dbConfigs = new DbProps().getConfig(DB_PROPERTIES_NAME);
        DataSourceManagerImpl dataSourceManager = new DataSourceManagerImpl();

        for (DbProps props : dbConfigs.values()) {
            String url = props.getDatabaseURL();

            if (url != null && url.length() > 0)
                dataSourceManager.registerDataSource(props.getBinding(), props.getDriver(), url,
                        props.getDatabaseUser(), props.getDatabasePassword());
            else
                dataSourceManager.registerDataSource(props.getBinding(), props.getDriver(), props.getDatabaseName(),
                        props.getHost(), props.getPort(), props.getDatabaseUser(),props.getDatabasePassword());
        }
    }

    public static String getUserTransactionJNDIContext() {
        return jtaPropertyManager.getJTAEnvironmentBean().getUserTransactionJNDIContext();
    }
    public static String getTransactionManagerJNDIContext() {
        return jtaPropertyManager.getJTAEnvironmentBean().getTransactionManagerJNDIContext();
    }

    public static synchronized void stop() {
        if (recoveryManager != null) {
            recoveryManager.terminate();
            recoveryManager = null;
        }
    }
}
