package io.narayana.spi;

import io.narayana.spi.internal.DataSourceManagerImpl;
import io.narayana.spi.internal.TransactionServiceImpl;

import javax.naming.NamingException;

public class TransactionServiceFactory {
    private static DataSourceManager dataSourceManager = new DataSourceManagerImpl();
    private static TransactionServiceImpl transactionService;

    /**
     * Accessor for the transaction service. If no transaction service exists one will be created using the current
     * default configuration ({@link io.narayana.spi.TransactionServiceFactory#getDefaultEnvironment()}).
     *
     * @return an instance of the transaction service
     * @throws NamingException
     * @throws ConfigurationException
     */
    public static synchronized TransactionService getTransactionService() throws ConfigurationException {

        if (transactionService == null)
            return getTransactionService(null);

        return transactionService;
    }

    /**
     * Accessor for the transaction service. If a transaction service already exists then the configuration parameter
     * will be ignored otherwise one will be created with the passed in config.
     *
     * @param config the desired transaction service configuration
     * @return an instance of the transaction service
     * @throws NamingException
     * @throws ConfigurationException if the transaction service has already started or the requested config is invalid
     */
    public static synchronized TransactionService getTransactionService(ConfigurationHolder config) throws ConfigurationException {

        if (transactionService != null)
            throw new ConfigurationException(
                    ConfigurationException.REASON.REQUIRES_RESTART, "Transaction Service has already been initialized");

        if (config == null)
            config = new ConfigurationHolder();

        config.setJts(false).build();

        transactionService = new TransactionServiceImpl();

        return transactionService;
    }

    /**
     * Get the TransactionService configuration that will be used in calls to
     * {@link TransactionServiceFactory#getTransactionService()}.
     *
     * Note that configuration changes must be made before creating the transaction service otherwise they
     * will have no effect.
     *
     * Use {@link ConfigurationHolder} to define a new configuration when making calls to
     * {@link TransactionServiceFactory#getTransactionService(ConfigurationHolder config)}.
     *
     * @return the current configuration
     */
    public static EnvironmentConfig getDefaultEnvironment() {
        return new EnvironmentConfig();
    }

    /**
     * Obtain an interface for registering and looking up DataSources
     *
     * @return a DataSource management interface
     */
    public static DataSourceManager getDataSourceManager() {
        return  dataSourceManager;
    }
}
