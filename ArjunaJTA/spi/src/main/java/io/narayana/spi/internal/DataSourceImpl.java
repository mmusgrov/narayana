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

import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.jdbc.TransactionalDriver;
import io.narayana.spi.DataSourceBindException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class DataSourceImpl implements DataSource {
    private Properties props = new Properties();
    private String txDriverUrl;
    private XADataSource xaDataSource = null;

    private TransactionalDriver arjunaJDBC2Driver = new com.arjuna.ats.jdbc.TransactionalDriver();

    public DataSourceImpl(String jndiName) throws DataSourceBindException {
        txDriverUrl = TransactionalDriver.arjunaDriver + jndiName;

        // java.naming.provider.url and java.naming.factory.initial system properties should already be set.
        try {
            xaDataSource = (XADataSource) new InitialContext().lookup(jndiName);
        } catch (NamingException e) {
            tsLogger.logger.info("Unable to look up datasource (no such binding)", e);
            throw new DataSourceBindException("no such binding", e);
        }
    }

    public DataSourceImpl(String jndiName, String u, String p) throws DataSourceBindException {
        this(jndiName);

        props.put(TransactionalDriver.userName, u);
        props.put(TransactionalDriver.password, p);
    }

    public Connection getConnection() throws SQLException {
        return arjunaJDBC2Driver.connect(txDriverUrl, props);
    }

    public Connection getConnection(String u, String p) throws SQLException {
        Properties dbProperties = new Properties();

        dbProperties.put(TransactionalDriver.createDb, true);
        dbProperties.put(TransactionalDriver.userName, u);
        dbProperties.put(TransactionalDriver.password, p);

        return arjunaJDBC2Driver.connect(txDriverUrl, dbProperties);
    }

    public PrintWriter getLogWriter() throws SQLException {
        return xaDataSource.getLogWriter();
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
        xaDataSource.setLogWriter(out);
    }

    public void setLoginTimeout(int seconds) throws SQLException {
        xaDataSource.setLoginTimeout(seconds);
    }

    public int getLoginTimeout() throws SQLException {
        return xaDataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return xaDataSource.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> tClass) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return false;
    }
}
