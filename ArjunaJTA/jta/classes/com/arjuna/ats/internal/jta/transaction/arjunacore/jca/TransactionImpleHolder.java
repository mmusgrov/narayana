/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package com.arjuna.ats.internal.jta.transaction.arjunacore.jca;

import com.arjuna.ats.internal.jta.transaction.arjunacore.subordinate.jca.TransactionImple;
import com.arjuna.ats.jta.logging.jtaLogger;

import javax.transaction.xa.XAException;

/**
 * This TransactionImple holder is a performance optimisation to avoid having to synchronize on the imported
 * transaction map in {@link com.arjuna.ats.internal.jta.transaction.arjunacore.jca.TransactionImporterImple}
 */
public class TransactionImpleHolder {
    private volatile Object imported;

    private TransactionImple checkValue(Object imported) throws XAException {
        if (imported instanceof XAException)
            throw (XAException) imported;

        return (TransactionImple) imported;
    }

    /**
     * Wait for the current value of this holder to be updated.
     *
     * @return the non null value held in this holder object
     * @throws XAException with the value XA_RETRY if the thread is interrupted
     */
    public TransactionImple getImported() throws XAException {
        Object imported = this.imported;

        if (imported != null) {
            return checkValue(imported);
        }

        synchronized (this) {
            for (;;) {
                imported = this.imported;

                if (imported != null) {
                    return checkValue(imported);
                }

                try {
                    wait();
                } catch (InterruptedException e) {
                    jtaLogger.i18NLogger.warn_transaction_import_interrupted(e);
                    Thread.currentThread().interrupt();
                    // throw XA_RETRY since once the interrupting condition has been cleared a retry may be possible
                    throw new XAException(XAException.XA_RETRY);
                }
            }
        }
    }

    public void setImported(TransactionImple imported) {
        synchronized (this) {
            if (imported == null)
                this.imported = new XAException(XAException.XA_RETRY);
            else
                this.imported = imported;

            notifyAll();
        }
    }
}
