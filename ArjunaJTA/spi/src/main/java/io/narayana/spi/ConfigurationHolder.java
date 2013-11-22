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

public class ConfigurationHolder {
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

    public ConfigurationHolder() {
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
//            hornetqStoreConfig = defaultConfig.hornetqStoreConfig;
//            jdbcStoreConfig = defaultConfig.jdbcStoreConfig;
    }

/*        public EnvironmentConfigBuilder setEnableStatistics(boolean enableStatistics) {
            this.enableStatistics = enableStatistics;
            return this;
        }*/

    public ConfigurationHolder setDefaultTimeout(int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        return this;
    }

    public ConfigurationHolder setObjectStorePath(String objectStorePath) {
        this.objectStorePath = objectStorePath;
        return this;
    }

    public ConfigurationHolder setVarDir(String varDir) {
        this.varDir = varDir;
        return this;
    }

    public ConfigurationHolder setSocketBinding(int socketBinding) {
        this.socketBinding = socketBinding;
        return this;
    }

    public ConfigurationHolder setStatusSocketBinding(int statusSocketBinding) {
        this.statusSocketBinding = statusSocketBinding;
        return this;
    }

/*        public EnvironmentConfigBuilder setRecoveryListener(boolean recoveryListener) {
            this.recoveryListener = recoveryListener;
            return this;
        }*/

    ConfigurationHolder setJts(boolean jts) {
        this.jts = jts;
        return this;
    }

    public ConfigurationHolder setNodeIdentifier(String nodeIdentifier) {
        this.nodeIdentifier = nodeIdentifier;
        return this;
    }

    public ConfigurationHolder setProcessIdSocketMaxPorts(int processIdSocketMaxPorts) {
        this.processIdSocketMaxPorts = processIdSocketMaxPorts;
        return this;
    }

/*        public EnvironmentConfigBuilder setProcessIdUuid(boolean processIdUuid) {
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
        }*/

    EnvironmentConfig build() {
        return new EnvironmentConfig(this);
    }
}
