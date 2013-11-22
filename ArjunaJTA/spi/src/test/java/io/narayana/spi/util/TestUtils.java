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
package io.narayana.spi.util;

import io.narayana.spi.DataSourceBindException;
import io.narayana.spi.DataSourceManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TestUtils {
    public static final String H2_BINDING = "h2";
    public static final String PG_BINDING = "postgresql";
    public static final String H2_DRIVER = "org.h2.Driver";
    public static final String PG_DRIVER = "org.postgresql.Driver";
    public static final String H2_URL = "jdbc:h2:~/ceylondb";

    public static int countRows(Connection connection, String tableName) throws SQLException {
        int count = 0;
        Statement s1 = connection.createStatement();
        ResultSet rs = s1.executeQuery("select count(*) from " + tableName);

        while (rs.next()){
            count = rs.getInt(1);
        }

        rs.close();
        s1.close();

        return count;
    }

    public static Map<String, Connection> getConnections(DataSourceManager dataSourceManager) throws SQLException, DataSourceBindException {
        Map<String, Connection> connections = new HashMap<>();

        connections.put("h2",  dataSourceManager.getDataSource(H2_BINDING, "sa", "sa").getConnection());
        connections.put("postgresql",  dataSourceManager.getDataSource(PG_BINDING, "sa", "sa").getConnection());

//        connections.put("h2",  dataSourceManager.getDataSource(H2_BINDING).getConnection("sa", "sa"));
//        connections.put("postgresql",  dataSourceManager.getDataSource(PG_BINDING).getConnection("sa", "sa"));

//        connections.put("h2", dataSourceManager.getConnection(H2_BINDING, "sa", "sa"));
//        connections.put("postgresql", dataSourceManager.getConnection(PG_BINDING, "sa", "sa"));

        createTables(connections);

        return connections;
    }

    public static void insertTable(Connection connection, String key, String val) throws SQLException {
        String insert = "INSERT INTO CEYLONKV(key ,val) values (?,?)";
        PreparedStatement statement = connection.prepareStatement(insert);

        statement.setString(1, key);
        statement.setString(2, val);

        statement.executeUpdate();

        statement.close();
    }

    public static void createTables(Map<String, Connection> connections) throws SQLException {
        String sql = "CREATE TABLE CEYLONKV " +
                "(key VARCHAR(255) not NULL, " +
                " val VARCHAR(255), " +
                " PRIMARY KEY ( key ))";

        for (Connection connection : connections.values()) {
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate(sql);
            } catch (SQLException e) {
            // ignore
            }
            statement.executeUpdate("delete from CEYLONKV");
            statement.close();
        }
    }

    public static void dropTables(Map<String, Connection> connections) throws SQLException {
        for (Connection connection : connections.values()) {
            Statement statement = connection.createStatement();

            statement.executeUpdate("DROP TABLE CEYLONKV");

            statement.close();
        }
    }

    public static void printResultSet(String title, ResultSet rs, String ... colNames) throws SQLException {
        System.out.println(title);

        while(rs.next()) {
            for (String colName : colNames) {
                String colVal = rs.getString(colName);
                System.out.printf("%s: %s ", colName, colVal);
            }

            System.out.println();
        }
    }
}
