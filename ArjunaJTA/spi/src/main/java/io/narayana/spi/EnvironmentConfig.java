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

import com.arjuna.ats.arjuna.common.*;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowser;
import com.arjuna.ats.internal.arjuna.objectstore.hornetq.HornetqJournalEnvironmentBean;
import com.arjuna.ats.internal.arjuna.utils.UuidProcessId;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import io.narayana.spi.internal.ProcessIdType;

/**
 * Configuration settings for the transaction service. Any changes must be applied before creating an instance of the
 * transaction service.
 */
public class EnvironmentConfig {
    public static final int NODE_NAME_SIZE = CoreEnvironmentBean.NODE_NAME_SIZE;

    boolean enableStatistics;
    int defaultTimeout;
    String objectStorePath;
    String varDir;
    int socketBinding;
    int statusSocketBinding;
    boolean recoveryListener;
    boolean jts;
    String nodeIdentifier;
    int processIdSocketMaxPorts;
    boolean processIdUuid;
    HornetqStoreConfig hornetqStoreConfig;
    JdbcStoreConfig jdbcStoreConfig;

    public static EnvironmentConfig getConfig() {
        return new EnvironmentConfig();
    }

    /**
     * Whether to enable transaction statistics. These statistics can be viewed in the Management Console in the Subsystem Metrics section of the Runtime tab.
     */
    public boolean isEnableStatistics() {
        return enableStatistics;
    }

    /**
     The default transaction timeout. This defaults to 300 seconds. You can override this programmatically, on a per-transaction basis.
     */
    public int getDefaultTimeout() {
        return defaultTimeout;
    }

    /**
     * Returns the 'var' directory path.
     *
     * Default: {user.dir}/var/tmp
     *
     * @return the 'var' directory name.
     */
    public String getObjectStorePath() {
        return objectStorePath;
    }

    /**
     * A relative or absolute filesystem path where the TM object store stores data. By default relative to the object-store-relative-to parameter's value.
     */
    public String getVarDir() {
        return varDir;
    }

    /**
     * Specifies the socket binding used by the Transaction Manager for recovery and generating transaction identifiers, when the socket-based mechanism is used.
     * Refer to processIdSocketMaxPorts for more information on unique identifier generation.
     */
    public int getSocketBinding() {
        return socketBinding;
    }
    /**
     * Specifies the socket binding to use for the Transaction Status manager.
     */
    public int getStatusSocketBinding() {
        return statusSocketBinding;
    }
    /**
     * Whether or not the Transaction Recovery process should listen on a network socket. Defaults to false.
     */
    public boolean isRecoveryListener() {
        return recoveryListener;
    }

    /**
     * Whether to use the Java Transaction Service (JTS) transactions. Defaults to false, which uses JTA transactions only.
     */
    public boolean isJts() {
        return jts;
    }

    /**
     * The Transaction Manager creates a unique identifier for each transaction log. Two different mechanisms are provided
     * for generating unique identifiers:
     * a socket-based mechanism and a mechanism based on the process identifier of the process.
     * The length of the nodeIdentfier must be >=  EnvironmentConfig.NODE_NAME_SIZE
     */
    public String getNodeIdentifier() {
        return nodeIdentifier;
    }

    /**
     * In the case of the socket-based identifier, a socket is opened and its port number is used for the identifier.
     * If the port is already in use, the next port is probed, until a free one is found.
     * The processIdSocketMaxPorts represents the maximum number of sockets the TM will try before failing. The default value is 10.
     */
    public int getProcessIdSocketMaxPorts() {
        return processIdSocketMaxPorts;
    }
    /**
     * Returns true if the process identifier is used to create a unique identifier for each transaction.
     * Otherwise, the socket-based mechanism is used. Defaults to true. Refer to processIdSocketMaxPorts for more information.
     */
    public boolean isProcessIdUuid() {
        return processIdUuid;
    }

    /**
     * If the HornetQ journaled storage mechanism for transaction logs is active then return its configuration.
     * Note that the type of storage mechanism is not configurable so this method is informational only.
     */
    public HornetqStoreConfig getHornetqStoreConfig() {
        return hornetqStoreConfig;
    }

    /**
     * If the JDBC storage mechanism for transaction logs is active then return its configuration.
     * Note that the type of storage mechanism is not configurable so this method is informational only.
     */
    public JdbcStoreConfig getJdbcStoreConfig() {
        return jdbcStoreConfig;
    }

    public EnvironmentConfig() {
        final JTAEnvironmentBean jtaEnvironmentBean =
                BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        final CoordinatorEnvironmentBean coordinatorEnvironmentBean =
                BeanPopulator.getDefaultInstance(CoordinatorEnvironmentBean.class);
        final CoreEnvironmentBean coreEnvironmentBean =
                BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class);
        final ObjectStoreEnvironmentBean defaultActionStoreObjectStoreEnvironmentBean =
                BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "default");
//        final ObjectStoreEnvironmentBean stateStoreObjectStoreEnvironmentBean =
//                BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore");
//        final ObjectStoreEnvironmentBean communicationStoreObjectStoreEnvironmentBean =
//                BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore");
        final RecoveryEnvironmentBean recoveryEnvironmentBean =
                BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class);

        enableStatistics = coordinatorEnvironmentBean.isEnableStatistics();
        defaultTimeout = coordinatorEnvironmentBean.getDefaultTimeout();
        objectStorePath = defaultActionStoreObjectStoreEnvironmentBean.getObjectStoreDir();
        varDir = coreEnvironmentBean.getVarDir();
        socketBinding  = coreEnvironmentBean.getSocketProcessIdPort();
        statusSocketBinding = recoveryEnvironmentBean.getTransactionStatusManagerPort();
        recoveryListener = recoveryEnvironmentBean.isRecoveryListener();
        jts = jtaEnvironmentBean.getTransactionManagerClassName().equals("com.arjuna.ats.internal.jta.transaction.jts.TransactionManagerImple");
        nodeIdentifier = coreEnvironmentBean.getNodeIdentifier();
        processIdSocketMaxPorts = coreEnvironmentBean.getSocketProcessIdMaxPorts();
        processIdUuid = true; // TODO see if coreEnvironmentBean.getProcessImplementation(); is socket based
        hornetqStoreConfig = null;
        jdbcStoreConfig = null;
    }

//    private EnvironmentConfig(EnvironmentConfigBuilder b) {
    EnvironmentConfig(ConfigurationHolder b) {
        final CoreEnvironmentBean coreEnvironmentBean =
                BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class);
        final ObjectStoreEnvironmentBean defaultActionStoreObjectStoreEnvironmentBean =
                BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "default");
        final ObjectStoreEnvironmentBean stateStoreObjectStoreEnvironmentBean =
                BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore");
        final ObjectStoreEnvironmentBean communicationStoreObjectStoreEnvironmentBean =
                BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore");
        final RecoveryEnvironmentBean recoveryEnvironmentBean =
                BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class);
        final JTAEnvironmentBean jtaEnvironmentBean =
                BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
        final CoordinatorEnvironmentBean coordinatorEnvironmentBean =
                BeanPopulator.getDefaultInstance(CoordinatorEnvironmentBean.class);

        coordinatorEnvironmentBean.setEnableStatistics(b.enableStatistics);
        coordinatorEnvironmentBean.setDefaultTimeout(b.defaultTimeout);
//        coordinatorEnvironmentBean.setTransactionStatusManagerEnable(b.);

        // Object Store Browser bean
        ObjStoreBrowser objStoreBrowser = new ObjStoreBrowser();
        objStoreBrowser.setExposeAllRecordsAsMBeans(true);

        if (!b.jts) {
            jtaEnvironmentBean.setTransactionManagerClassName(com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple.class.getName());
            jtaEnvironmentBean.setUserTransactionClassName(com.arjuna.ats.internal.jta.transaction.arjunacore.UserTransactionImple.class.getName());
            jtaEnvironmentBean.setTransactionSynchronizationRegistryClassName(com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple.class.getName());

        } else {
            //           final ORB orb = ;  // TODO
//            new PostInitLoader(PostInitLoader.generateORBPropertyName("com.arjuna.orbportability.orb"), orb);

            jtaEnvironmentBean.setTransactionManagerClassName(com.arjuna.ats.internal.jta.transaction.jts.TransactionManagerImple.class.getName());
            jtaEnvironmentBean.setUserTransactionClassName(com.arjuna.ats.internal.jta.transaction.jts.UserTransactionImple.class.getName());
            jtaEnvironmentBean.setTransactionSynchronizationRegistryClassName(com.arjuna.ats.internal.jta.transaction.jts.TransactionSynchronizationRegistryImple.class.getName());
        }

        try {
            objStoreBrowser.start();
        } catch (Exception e) {
            System.err.printf("Failed to configure object store browser bean");
        }

        if(coreEnvironmentBean.getProcessImplementationClassName() == null) {
            UuidProcessId id = new UuidProcessId();
            coreEnvironmentBean.setProcessImplementation(id);
        }

        if (b.nodeIdentifier != null) {
            try {
                coreEnvironmentBean.setNodeIdentifier(b.nodeIdentifier);
            } catch (CoreEnvironmentBeanException e) {
                // ignore because it has already been validated
            }
        }

        coreEnvironmentBean.setVarDir(b.varDir);
        recoveryEnvironmentBean.setRecoveryListener(b.recoveryListener);
        recoveryEnvironmentBean.setTransactionStatusManagerPort(b.statusSocketBinding);

        if (!b.processIdUuid) {
            // Use the UUID based id
            UuidProcessId id = new UuidProcessId();
            coreEnvironmentBean.setProcessImplementation(id);
        } else {
            // Use the socket process id
            coreEnvironmentBean.setProcessImplementationClassName(ProcessIdType.SOCKET.getClazz());
            if (b.socketBinding != -1)
                coreEnvironmentBean.setSocketProcessIdPort(b.socketBinding);

            coreEnvironmentBean.setSocketProcessIdMaxPorts(b.processIdSocketMaxPorts);
        }

/*        if(b.hornetqStoreConfig != null) {
            HornetqJournalEnvironmentBean hornetqJournalEnvironmentBean = BeanPopulator.getDefaultInstance(
                    com.arjuna.ats.internal.arjuna.objectstore.hornetq.HornetqJournalEnvironmentBean.class
            );
            hornetqJournalEnvironmentBean.setAsyncIO(b.hornetqStoreConfig.enableAsyncIO);
            hornetqJournalEnvironmentBean.setStoreDir(b.objectStorePath+"/HornetqObjectStore");
            defaultActionStoreObjectStoreEnvironmentBean.setObjectStoreType(
                    "com.arjuna.ats.internal.arjuna.objectstore.hornetq.HornetqObjectStoreAdaptor"
            );
        } else {*/
            defaultActionStoreObjectStoreEnvironmentBean.setObjectStoreDir(b.objectStorePath);
//        }

        stateStoreObjectStoreEnvironmentBean.setObjectStoreDir(b.objectStorePath);

        communicationStoreObjectStoreEnvironmentBean.setObjectStoreDir(b.objectStorePath);

/*        if(b.jdbcStoreConfig != null) {
            String storeType =  com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCStore.class.getName();

            defaultActionStoreObjectStoreEnvironmentBean.setObjectStoreType(storeType);
            stateStoreObjectStoreEnvironmentBean.setObjectStoreType(storeType);
            communicationStoreObjectStoreEnvironmentBean.setObjectStoreType(storeType);

            String jdbcAccessClassName =  com.arjuna.ats.internal.arjuna.objectstore.jdbc.accessors.DataSourceJDBCAccess.class.getName();

            defaultActionStoreObjectStoreEnvironmentBean.setJdbcAccess(jdbcAccessClassName + ";datasourceName=" + b.jdbcStoreConfig.dataSourceJndiName);
            stateStoreObjectStoreEnvironmentBean.setJdbcAccess(jdbcAccessClassName + ";datasourceName=" + b.jdbcStoreConfig.dataSourceJndiName);
            communicationStoreObjectStoreEnvironmentBean.setJdbcAccess(jdbcAccessClassName + ";datasourceName=" + b.jdbcStoreConfig.dataSourceJndiName);


            defaultActionStoreObjectStoreEnvironmentBean.setTablePrefix(b.jdbcStoreConfig.getActionTablePrefix());
            stateStoreObjectStoreEnvironmentBean.setTablePrefix(b.jdbcStoreConfig.getStateTablePrefix());
            communicationStoreObjectStoreEnvironmentBean.setTablePrefix(b.jdbcStoreConfig.getCommunicationTablePrefix());


            defaultActionStoreObjectStoreEnvironmentBean.setDropTable(b.jdbcStoreConfig.isActionDropTable());
            stateStoreObjectStoreEnvironmentBean.setDropTable(b.jdbcStoreConfig.isStateDropTable());
            communicationStoreObjectStoreEnvironmentBean.setDropTable(b.jdbcStoreConfig.isCommunicationDropTable());

        }*/
    }

 /*   public static class EnvironmentConfigBuilder {
        boolean enableStatistics;
        int defaultTimeout;
        String objectStorePath;
        String varDir;
        int socketBinding = -1;
        int statusSocketBinding;
        boolean recoveryListener;
        boolean jts;
        String nodeIdentifier;
        int processIdSocketMaxPorts;
        boolean processIdUuid;
        HornetqStoreConfig hornetqStoreConfig = null;
        JdbcStoreConfig jdbcStoreConfig = null;

        public EnvironmentConfigBuilder() {
            EnvironmentConfig defaultConfig = new EnvironmentConfig();

            enableStatistics = defaultConfig.enableStatistics;
            defaultTimeout = defaultConfig.defaultTimeout;
            objectStorePath = defaultConfig.objectStorePath;
            varDir = defaultConfig.varDir;
            socketBinding = defaultConfig.socketBinding;
            statusSocketBinding = defaultConfig.statusSocketBinding;
            recoveryListener = defaultConfig.recoveryListener;
            jts = defaultConfig.jts;
            nodeIdentifier = defaultConfig.nodeIdentifier;
            processIdSocketMaxPorts = defaultConfig.processIdSocketMaxPorts;
            processIdUuid = defaultConfig.processIdUuid;
            hornetqStoreConfig = defaultConfig.hornetqStoreConfig;
            jdbcStoreConfig = defaultConfig.jdbcStoreConfig;
        }

*//*        public EnvironmentConfigBuilder setEnableStatistics(boolean enableStatistics) {
            this.enableStatistics = enableStatistics;
            return this;
        }*//*

        public EnvironmentConfigBuilder setDefaultTimeout(int defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
            return this;
        }

        public EnvironmentConfigBuilder setObjectStorePath(String objectStorePath) {
            this.objectStorePath = objectStorePath;
            return this;
        }

        public EnvironmentConfigBuilder setVarDir(String varDir) {
            this.varDir = varDir;
            return this;
        }

        public EnvironmentConfigBuilder setSocketBinding(int socketBinding) {
            this.socketBinding = socketBinding;
            return this;
        }

        public EnvironmentConfigBuilder setStatusSocketBinding(int statusSocketBinding) {
            this.statusSocketBinding = statusSocketBinding;
            return this;
        }

*//*        public EnvironmentConfigBuilder setRecoveryListener(boolean recoveryListener) {
            this.recoveryListener = recoveryListener;
            return this;
        }*//*

        EnvironmentConfigBuilder setJts(boolean jts) {
            this.jts = jts;
            return this;
        }

        public EnvironmentConfigBuilder setNodeIdentifier(String nodeIdentifier) throws ConfigurationException {
            if (nodeIdentifier.getBytes().length > NODE_NAME_SIZE) {
                tsLogger.i18NLogger.fatal_nodename_too_long(nodeIdentifier);

                throw new ConfigurationException(ConfigurationException.REASON.INVALID_VALUE, tsLogger.i18NLogger.get_fatal_nodename_too_long(nodeIdentifier));
            }

            this.nodeIdentifier = nodeIdentifier;
            return this;
        }

        public EnvironmentConfigBuilder setProcessIdSocketMaxPorts(int processIdSocketMaxPorts) {
            this.processIdSocketMaxPorts = processIdSocketMaxPorts;
            return this;
        }

*//*        public EnvironmentConfigBuilder setProcessIdUuid(boolean processIdUuid) {
            this.processIdUuid = processIdUuid;
            return this;
        }

        public EnvironmentConfigBuilder setJdbcStoreConfig(JdbcStoreConfig jdbcStoreConfig) {
            this.jdbcStoreConfig = jdbcStoreConfig;
            return this;
        }

        public EnvironmentConfigBuilder setHornetqStoreConfig(HornetqStoreConfig hornetqStoreConfig) {
            this.hornetqStoreConfig = hornetqStoreConfig;
            return this;
        }*//*

        EnvironmentConfig build() {
            return new EnvironmentConfig(this);
        }
    }
*/
    public static final class JdbcStoreConfig {
        private String dataSourceJndiName;
        private final String actionTablePrefix;
        private final boolean actionDropTable;
        private final String stateTablePrefix;
        private final boolean stateDropTable;
        private final String communicationTablePrefix;
        private final boolean communicationDropTable;

        private JdbcStoreConfig(final JdbcStoreConfigBulder jdbcStoreConfigBulder) {
            this.dataSourceJndiName = jdbcStoreConfigBulder.dataSourceJndiName;
            this.actionTablePrefix = jdbcStoreConfigBulder.actionTablePrefix;
            this.actionDropTable = jdbcStoreConfigBulder.actionDropTable;
            this.stateTablePrefix = jdbcStoreConfigBulder.stateTablePrefix;
            this.stateDropTable = jdbcStoreConfigBulder.stateDropTable;
            this.communicationTablePrefix = jdbcStoreConfigBulder.communicationTablePrefix;
            this.communicationDropTable = jdbcStoreConfigBulder.communicationDropTable;
        }

        public String getActionTablePrefix() {
            return actionTablePrefix;
        }

        public boolean isActionDropTable() {
            return actionDropTable;
        }

        public String getStateTablePrefix() {
            return stateTablePrefix;
        }

        public boolean isStateDropTable() {
            return stateDropTable;
        }

        public String getCommunicationTablePrefix() {
            return communicationTablePrefix;
        }

        public boolean isCommunicationDropTable() {
            return communicationDropTable;
        }
        public String getDataSourceJndiName() {
            return dataSourceJndiName;
        }
    }

    public static final class JdbcStoreConfigBulder {
        private String dataSourceJndiName;
        private String actionTablePrefix;
        private boolean actionDropTable;
        private String stateTablePrefix;
        private boolean stateDropTable;
        private String communicationTablePrefix;
        private boolean communicationDropTable;

        public JdbcStoreConfigBulder setActionTablePrefix(String actionTablePrefix) {
            this.actionTablePrefix = actionTablePrefix;
            return this;
        }

        public JdbcStoreConfigBulder setActionDropTable(boolean actionDropTable) {
            this.actionDropTable = actionDropTable;
            return this;
        }

        public JdbcStoreConfigBulder setStateTablePrefix(String stateTablePrefix) {
            this.stateTablePrefix = stateTablePrefix;
            return this;
        }

        public JdbcStoreConfigBulder setStateDropTable(boolean stateDropTable) {
            this.stateDropTable = stateDropTable;
            return this;
        }

        public JdbcStoreConfigBulder setCommunicationTablePrefix(String communicationTablePrefix) {
            this.communicationTablePrefix = communicationTablePrefix;
            return this;
        }

        public JdbcStoreConfigBulder setCommunicationDropTable(boolean communicationDropTable) {
            this.communicationDropTable = communicationDropTable;
            return this;
        }

        public JdbcStoreConfigBulder setDataSourceJndiName(String dataSourceJndiName) {
            this.dataSourceJndiName = dataSourceJndiName;
            return this;
        }

        public JdbcStoreConfig build() {
            return new JdbcStoreConfig(this);
        }
    }


    public static final class HornetqStoreConfig {
        private final boolean enableAsyncIO;

        private HornetqStoreConfig(final HornetqStoreConfigBulder hornetqStoreConfigBulder) {
            this.enableAsyncIO = hornetqStoreConfigBulder.enableAsyncIO;
        }

        public boolean getEnableAsyncIO() {
            return enableAsyncIO;
        }
    }

    public static final class HornetqStoreConfigBulder {
        private boolean enableAsyncIO = false;

        public HornetqStoreConfigBulder setEnableAsyncIO(boolean enableAsyncIO) {
            this.enableAsyncIO = enableAsyncIO;
            return this;
        }

        public HornetqStoreConfig build() {
            return new HornetqStoreConfig(this);
        }
    }
}


