/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat Middleware LLC, and individual contributors
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
package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.jts;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.internal.jts.recovery.transactions.*;
import org.junit.Before;
import org.junit.Test;

import javax.management.MalformedObjectNameException;

public class JTSOSBTransactionTypeTests extends JTSOSBTestBase {
    @Test
    public void testAssumedCompleteHeuristicServerTransaction() throws MalformedObjectNameException {
        assertBeanWasCreated(new AssumedCompleteHeuristicServerTransaction(new Uid()));
    }
    @Test
    public void testAssumedCompleteHeuristicTransaction() throws MalformedObjectNameException {
        assertBeanWasCreated(new AssumedCompleteHeuristicTransaction(new Uid()));
    }
    @Test
    public void testAssumedCompleteServerTransaction() throws MalformedObjectNameException {
        assertBeanWasCreated(new AssumedCompleteServerTransaction(new Uid()));
    }
    @Test
    public void testAssumedCompleteTransaction() throws MalformedObjectNameException {
        assertBeanWasCreated(new AssumedCompleteTransaction(new Uid()));
    }
    @Test
    public void testRecoveredServerTransaction() throws MalformedObjectNameException {
        assertBeanWasCreated(new RecoveredServerTransaction(new Uid()));
    }
    @Test
    public void testRecoveredTransaction() throws MalformedObjectNameException {
        assertBeanWasCreated(new RecoveredTransaction(new Uid()));
    }
    @Test
    public void testServerTransaction() throws MalformedObjectNameException {
        assertBeanWasCreated(new RecoveringServerTransaction(new Uid()));
    }

    private static class RecoveringServerTransaction extends com.arjuna.ats.internal.jts.orbspecific.interposition.coordinator.ServerTransaction {
        protected RecoveringServerTransaction(Uid recoveringActUid) {
            super(recoveringActUid);
        }
    }
}
