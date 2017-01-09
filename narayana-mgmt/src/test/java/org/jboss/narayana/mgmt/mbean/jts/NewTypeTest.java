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
package org.jboss.narayana.mgmt.mbean.jts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.management.MBeanException;

import org.jboss.narayana.mgmt.internal.arjuna.OSBTypeHandler;
import org.jboss.narayana.mgmt.internal.arjuna.ObjStoreBrowser;
import org.jboss.narayana.mgmt.internal.arjuna.UidWrapper;
import org.junit.Test;

import com.arjuna.ats.arjuna.common.Uid;

/**
 * An example of how to instrument new record types.
 *
 * @author Mike Musgrove
 */
public class NewTypeTest extends JTSOSBTestBase {

    @Test
    public void testInstrumentNewType() throws MBeanException {
        OSBTypeHandler osbTypeHandler = new OSBTypeHandler(
                true,
                true,
                "org.jboss.narayana.mgmt.internal.jts.ArjunaTransactionImpleWrapper",
                "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/ServerTransaction/JCA",
                JCAServerTransactionHeaderReader.class.getName()
        );

        JCAServerTransactionHeaderReader headerStateReader = (JCAServerTransactionHeaderReader) osbTypeHandler.getHeaderStateReader();
        RecoveringSubordinateServerTransaction txn = new RecoveringSubordinateServerTransaction(new Uid()); // , new XidImple(new Uid()))

        ObjStoreBrowser osb = createObjStoreBrowser(false);

        osb.registerHandler(osbTypeHandler);

        generatedHeuristicHazard(txn);

        osb.probe();

        UidWrapper w = osb.findUid(txn.get_uid());

        assertNotNull(w);
        assertTrue(headerStateReader.isWasInvoked());
    }

    private static class RecoveringSubordinateServerTransaction
            extends com.arjuna.ats.internal.jta.transaction.jts.subordinate.jca.coordinator.ServerTransaction {

        public RecoveringSubordinateServerTransaction(Uid recoveringActUid) {
            super(recoveringActUid); //, new XidImple(new Uid()));
        }
    }
}
