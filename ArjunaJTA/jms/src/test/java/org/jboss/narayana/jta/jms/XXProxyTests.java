/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
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
package org.jboss.narayana.jta.jms;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.jms.JMSContext;
import javax.jms.XAJMSContext;
import javax.transaction.Synchronization;
import javax.transaction.xa.XAResource;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class XXProxyTests {

    @Mock
    private XAJMSContext xaContextMock;

    @Mock
    private XAResource xaResourceMock;

    @Mock
    private TransactionHelper transactionHelperMock;

    private JMSContext context; //ContextProxy context;
//    @Mock private XASession xaSessionMock;
//    private Session session;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        context = new ContextProxy(xaContextMock, transactionHelperMock);
//        session = new SessionProxy(xaSessionMock, transactionHelperMock);
    }

    //@Test
    public void xshouldCloseContext() throws Exception {
        when(transactionHelperMock.isTransactionAvailable()).thenReturn(true);
        when(xaContextMock.getXAResource()).thenReturn(xaResourceMock);

        List<Synchronization> synchronizations = new ArrayList<>(1);
        doAnswer(i -> synchronizations.add(i.getArgument(0))).when(transactionHelperMock)
                .registerSynchronization(any(Synchronization.class));

        context.close();

        // Will check if the correct context was registered for closing
        synchronizations.get(0).afterCompletion(0);

        verify(transactionHelperMock, times(1)).isTransactionAvailable();
        verify(transactionHelperMock, times(1)).deregisterXAResource(xaResourceMock);
        verify(transactionHelperMock, times(1)).registerSynchronization(any(ContextClosingSynchronization.class));
        verify(xaContextMock, times(1)).close();
    }

    @Test
    public void shouldCloseContext() throws Exception {
        when(transactionHelperMock.isTransactionAvailable()).thenReturn(true);
        when(xaContextMock.getXAResource()).thenReturn(xaResourceMock);

        List<Synchronization> synchronizations = new ArrayList<>(1);
        doAnswer(i -> synchronizations.add(i.getArgument(0))).when(transactionHelperMock)
                .registerSynchronization(any(Synchronization.class));

        context.close();

        // Will check if the correct session was registered for closing
        synchronizations.get(0).afterCompletion(0);

        verify(transactionHelperMock, atLeast(1)).isTransactionAvailable();
        verify(transactionHelperMock, atLeast(1)).deregisterXAResource(xaResourceMock);
        verify(transactionHelperMock, atLeast(1)).registerSynchronization(any(SessionClosingSynchronization.class));
        verify(xaContextMock, atLeast(1)).close();
    }
}
