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

public class ConfigurationException extends Exception {

    REASON reason;

    public REASON getReason() {
        return reason;
    }

    public ConfigurationException(REASON reason, String message) {
        super(message);
        this.reason = reason;
    }
    public ConfigurationException(REASON reason, String message, Throwable e) {
        super(message, e);
        this.reason = reason;
    }

    public enum REASON {
        REQUIRES_RESTART, INVALID_VALUE, JNDI, OTHER
    }
}
