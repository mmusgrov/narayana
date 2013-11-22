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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * This class will be removed in a subsequent release in favour of using an embedded JCA implementation
 */
@Deprecated
public interface DataSourceManager {
    /**
     * Obtain the DataSource bound to a JDNI naming context. The credentials for obtaining connections
     * will need to be supplied when calling getConnection on the returned DataSource.
     *
     * @param bindingName the name that datsource is bound to
     * @return the datasource
     * @throws DataSourceBindException
     */
    DataSource getDataSource(String bindingName) throws DataSourceBindException;

    /**
     * Obtain the DataSource bound to a JDNI naming context. The credentials for obtaining connections
     * will not need to be supplied when calling getConnection on the returned DataSource since they are
     * passed in as parameters to this method call.
     * @param bindingName the name that DataSource is bound to
     * @param userName credentials
     * @param password credentials
     * @return the DataSource
     * @throws DataSourceBindException
     */
    DataSource getDataSource(String bindingName, String userName, String password) throws DataSourceBindException;

    /**
     * Bind a DataSource to the default JNDI context
     * In a future release this method will be deprecated in favour of using XML DataSource definitions
     * @param driver the class name of the JDBC driver for the resource
     * @param binding the jndi name the DataSource will be bound to
     * @param databaseName the database name that this binding will refer to
     * @param host the host where the DataSource can be accessed
     * @param port the port where the DataSource can be accessed
     * @throws DataSourceBindException
     */
    void registerDbResource(String driver, String binding, String databaseName, String host, long port) throws DataSourceBindException;

    /**
     * Bind a DataSource to the default JNDI context
     * In a future release this method will be deprecated in favour of using XML DataSource definitions
     * @param driver the class name of the JDBC driver for the resource
     * @param binding the jndi name the DataSource will be bound to
     * @param databaseUrl the url on which the DataSource can be accessed
     * @throws DataSourceBindException
     */
    void registerDbResource(String driver, String binding, String databaseUrl) throws DataSourceBindException;
}
