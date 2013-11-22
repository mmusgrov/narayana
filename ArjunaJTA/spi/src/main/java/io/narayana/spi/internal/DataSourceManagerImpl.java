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
import com.arjuna.ats.jdbc.common.jdbcPropertyManager;
import io.narayana.spi.DataSourceBindException;
import io.narayana.spi.DataSourceManager;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

public class DataSourceManagerImpl implements DataSourceManager {
    private static final String JNDIBASE = "/narayana/jndi/";

    static  {
        try {
            DriverManager.registerDriver(new TransactionalDriver());

        } catch (SQLException e) {
            tsLogger.logger.info("Unable to register TransactionalDriver", e);
        }
    }

    @Override
    public DataSource getDataSource(String bindingName) throws DataSourceBindException {
        return new DataSourceImpl(bindingName);
    }

    @Override
    public DataSource getDataSource(String bindingName, String userName, String password) throws DataSourceBindException {
        return new DataSourceImpl(bindingName, userName, password);
    }

    public void registerDbResource(String driver, String binding, String databaseUrl) throws DataSourceBindException {
        registerDbResource(driver, binding, databaseUrl, null, -1);
    }

    public void registerDbResource(String driver, String binding, String databaseName, String host, long port) throws DataSourceBindException {
        XADataSource xaDataSourceToBind;
        Properties p = System.getProperties();
        String driversProp = (String) p.get("jdbc.drivers");

        if (driversProp == null)
            p.put("jdbc.drivers", driver);
        else
            p.put("jdbc.drivers", driversProp + ":" + driver);

        System.setProperties(p);

        try {
            xaDataSourceToBind = getXADataSource(driver, databaseName, host, (int) port);
        } catch (Exception e) {
            tsLogger.logger.info("Cannot bind " + databaseName + " for driver " + driver, e);

            throw new DataSourceBindException("Cannot bind " + databaseName + " for driver " + driver, e);
        }

        try
        {
            // initJndi should have already been called resulting in suitable java.naming.provider.url and java.naming.factory.initial
            // system properties being set.
            InitialContext ctx = new InitialContext();

            ctx.rebind(binding, xaDataSourceToBind);

//            tsLogger.logger.trace("bound " + binding);
        }
        catch (Exception e)
        {
            throw new DataSourceBindException("Cannot find a suitable JNDI context", e);
        }
    }

    public static InitialContext initJndi(String path) throws NamingException {
        File jndiDir = new File(path + JNDIBASE);
        String jndiUrl = "file://" + jndiDir.getAbsolutePath();

        jndiDir.mkdirs();

        // expose the jndi settings via system properties:
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.fscontext.RefFSContextFactory");
        System.setProperty(Context.PROVIDER_URL, jndiUrl);

        Hashtable<String, String> env = new Hashtable<String, String> ();
        env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.fscontext.RefFSContextFactory");
        env.put(javax.naming.Context.PROVIDER_URL, jndiUrl);

        jdbcPropertyManager.getJDBCEnvironmentBean().setJndiProperties(env);

        return new InitialContext(env);
    }

    private static XADataSource getXADataSource(String driver, String databaseName, String host, Integer port)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        XADataSource xaDataSourceToBind = null;

        if (driver.equals("oracle.jdbc.driver.OracleDriver"))
        {
            XADataSourceReflectionWrapper wrapper = new XADataSourceReflectionWrapper("oracle.jdbc.xa.client.OracleXADataSource");

            wrapper.setProperty("databaseName", databaseName);
            wrapper.setProperty("serverName", host);
            wrapper.setProperty("portNumber", port);
            wrapper.setProperty("driverType", "thin");

            xaDataSourceToBind = wrapper.getWrappedXADataSource();
        }
        else if( driver.equals("com.microsoft.sqlserver.jdbc.SQLServerDriver")) {

            XADataSourceReflectionWrapper wrapper = new XADataSourceReflectionWrapper("com.microsoft.sqlserver.jdbc.SQLServerXADataSource");

            wrapper.setProperty("databaseName", databaseName);
            wrapper.setProperty("serverName", host);
            wrapper.setProperty("portNumber", port);
            wrapper.setProperty("sendStringParametersAsUnicode", false);

            xaDataSourceToBind = wrapper.getWrappedXADataSource();
        }
        else if( driver.equals("org.postgresql.Driver")) {

            XADataSourceReflectionWrapper wrapper = new XADataSourceReflectionWrapper("org.postgresql.xa.PGXADataSource");

            wrapper.setProperty("databaseName", databaseName);
            wrapper.setProperty("serverName", host);
            wrapper.setProperty("portNumber", port);

            xaDataSourceToBind = wrapper.getWrappedXADataSource();
        }
        else if( driver.equals("com.mysql.jdbc.Driver")) {

            // Note: MySQL XA only works on InnoDB tables.
            // set 'default-storage-engine=innodb' in e.g. /etc/my.cnf
            // so that the 'CREATE TABLE ...' statments behave correctly.
            // doing this config on a per connection basis instead is
            // possible but would require lots of code changes :-(

            XADataSourceReflectionWrapper wrapper = new XADataSourceReflectionWrapper("com.mysql.jdbc.jdbc2.optional.MysqlXADataSource");

            wrapper.setProperty("databaseName", databaseName);
            wrapper.setProperty("serverName", host);
            wrapper.setProperty("pinGlobalTxToPhysicalConnection", true); // Bad Things happen if you forget this bit.

            xaDataSourceToBind = wrapper.getWrappedXADataSource();
        }
        else if( driver.equals("com.ibm.db2.jcc.DB2Driver")) {

            // for DB2 version 8.2

            XADataSourceReflectionWrapper wrapper = new XADataSourceReflectionWrapper("com.ibm.db2.jcc.DB2XADataSource");

            wrapper.setProperty("databaseName", databaseName);
            wrapper.setProperty("serverName", host);
            wrapper.setProperty("driverType", 4);
            wrapper.setProperty("portNumber", port);

            xaDataSourceToBind = wrapper.getWrappedXADataSource();
        }
        else if( driver.equals("com.sybase.jdbc3.jdbc.SybDriver")) {

            XADataSourceReflectionWrapper wrapper = new XADataSourceReflectionWrapper("com.sybase.jdbc3.jdbc.SybXADataSource");

            wrapper.setProperty("databaseName", databaseName);
            wrapper.setProperty("serverName", host);
            wrapper.setProperty("portNumber", port);

            xaDataSourceToBind = wrapper.getWrappedXADataSource();
        } else if( driver.equals("org.h2.Driver")) {

            XADataSourceReflectionWrapper wrapper = new XADataSourceReflectionWrapper("org.h2.jdbcx.JdbcDataSource");


            wrapper.setProperty("URL", databaseName);

            xaDataSourceToBind = wrapper.getWrappedXADataSource();
        }
        else
        {
            throw new RuntimeException("JDBC2 driver " + driver + " not recognised");
        }

        return xaDataSourceToBind;
    }
}

class XADataSourceReflectionWrapper {
    private XADataSource xaDataSource;
    XADataSourceReflectionWrapper(String classname) {
        try {
            xaDataSource = (XADataSource)Class.forName(classname).newInstance();
        } catch(Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public void setProperty(String name, Object value)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        name = "set"+name.substring(0,1).toUpperCase()+name.substring(1);

        Class type = value.getClass();
        if(value instanceof Integer) {
            type = Integer.TYPE;
        }
        if(value instanceof Boolean) {
            type = Boolean.TYPE;
        }

        Method method = xaDataSource.getClass().getMethod(name, type);
        method.invoke(xaDataSource, value);
    }

    public XADataSource getWrappedXADataSource() {
        return xaDataSource;
    }
}