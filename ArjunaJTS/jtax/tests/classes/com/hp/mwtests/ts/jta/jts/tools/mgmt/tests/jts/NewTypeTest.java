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

import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.jta.xa.XidImple;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.*;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.jts.ServerTransactionHeaderReader;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * An example of how to instrument new record types.
 *
 * @author Mike Musgrove
 */
public class NewTypeTest extends JTSOSBTestBase {

    @Test
    public void testInstrumentNewType() {
        TypeRepository.registerTypeHandler(RecoveringSubordinateServerTransaction.typeName(),
                new RecoveringSubordinateServerTransactionHandler());

        generatedHeuristicHazard(new RecoveringSubordinateServerTransaction(new Uid()));

        osb.probe();

        assertTrue(JCAServerTransactionHeaderReader.isWasInvoked());
    }

    private static class JCAServerTransactionHeaderReader extends ServerTransactionHeaderReader {
        private static boolean wasInvoked;

        public JCAServerTransactionHeaderReader() {
            this.wasInvoked = false;
        }

        protected HeaderState unpackHeader(InputObjectState os) throws IOException {
            wasInvoked = true;

            if (os.unpackBoolean())
                new XidImple().unpackFrom(os);

            return super.unpackHeader(os);
        }

        public static boolean isWasInvoked() {
            return wasInvoked;
        }
    }

    private static class RecoveringSubordinateServerTransactionHandler extends BAHandlerImpl implements ARHandler {
        @Override
        public HeaderStateReader getHeaderStateReader(String typeName) {
            return new JCAServerTransactionHeaderReader();
        }
    }

    private static class RecoveringSubordinateServerTransaction
            extends com.arjuna.ats.internal.jta.transaction.jts.subordinate.jca.coordinator.ServerTransaction {

        public RecoveringSubordinateServerTransaction(Uid recoveringActUid) {
            super(recoveringActUid); //, new XidImple(new Uid()));
        }

        public static String typeName() {
            return "/StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/ServerTransaction/JCA";
        }

    }
}
