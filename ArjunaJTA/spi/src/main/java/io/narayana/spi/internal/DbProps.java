package io.narayana.spi.internal;

import java.io.*;
import java.util.*;

public class DbProps {
    public static final String DB_PREFIXES_NAME = "DB_PREFIXES";

    public final static String BINDING = "Binding";
    public final static String DRIVER = "Driver";
    public final static String DATABASE_URL = "DatabaseURL";
    public final static String DATABASE_NAME = "DatabaseName";
    public final static String HOST = "Host";
    public final static String PORT = "Port";
    public final static String DATABASE_USER = "DatabaseUser";
    public final static String DATABASE_PASSWORD = "DatabasePassword";

    private String binding;
    private String driver;
    private String databaseURL;
    private String databaseName;
    private String host;
    private int port;
    private String databaseUser;
    private String databasePassword;

    public DbProps() {}

    public DbProps(String binding, String driver, String databaseURL, String databaseName, String host, String portName, String databaseUser, String databasePassword) {
        this.binding = binding;
        this.driver = driver;
        this.databaseURL = databaseURL;
        this.databaseName = databaseName;
        this.host = host;
        this.port = 0;
        this.databaseUser = databaseUser;
        this.databasePassword = databasePassword;

        if (binding == null || driver == null || databaseUser == null || databasePassword == null)
            throw new IllegalArgumentException("missing database properties for binding " + binding);

        if (databaseURL == null && (databaseName == null || host == null || portName == null))
            throw new IllegalArgumentException("missing database URL or (databaseName, host and port) for binding " + binding);

        if (portName != null) {
            try {
                port = Integer.parseInt(portName);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number for binding " + binding);
            }
        }
    }

    public String getBinding() {
        return binding;
    }

    public String getDriver() {
        return driver;
    }

    public String getDatabaseURL() {
        return databaseURL;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseUser() {
        return databaseUser;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    private Properties parseProperties(String fileName) {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(fileName);
        Properties props = new Properties();

        if (in == null) {
            String userDir = System.getProperty("user.dir");
            System.out.printf("Cannot locate %s on the classpath - looking in %s instead%n", fileName, userDir);

            try {
                in = new FileInputStream(userDir + '/' + fileName);
            } catch (FileNotFoundException e) {
                return props;
            }
        }

        try {
            props.load(in);
            in.close();
        } catch (IOException e) {
            throw new RuntimeException("Unable to configure Databases: " + e.getMessage(), e);
        }

        return props;
    }

    public Map<String, DbProps> getConfig(String fileName) {
        Properties props = parseProperties(fileName);
        Map<String, DbProps> dbConfigs = new HashMap<>();
        String dbProp = props.getProperty(DB_PREFIXES_NAME);

        if (dbProp == null)
            return dbConfigs;

        for (String prefix : dbProp.split(",")) {
            String binding = props.getProperty(prefix + '_' + BINDING);
            dbConfigs.put(binding, new DbProps(binding,
                    props.getProperty(prefix + '_' + DRIVER),
                    props.getProperty(prefix + '_' + DATABASE_URL),
                    props.getProperty(prefix + '_' + DATABASE_NAME),
                    props.getProperty(prefix + '_' + HOST),
                    props.getProperty(prefix + '_' + PORT),
                    props.getProperty(prefix + '_' + DATABASE_USER),
                    props.getProperty(prefix + '_' + DATABASE_PASSWORD))
            );
        }

        return dbConfigs;
    }
}
