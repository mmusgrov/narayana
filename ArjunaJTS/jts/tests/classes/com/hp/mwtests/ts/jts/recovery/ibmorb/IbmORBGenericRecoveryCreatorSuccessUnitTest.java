/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors 
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors. 
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
/*
 * Copyright (C) 2004,
 *
 * Arjuna Technologies Ltd,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.  
 *
 * $Id: xidcheck.java 2342 2006-03-30 13:06:17Z  $
 */

package com.hp.mwtests.ts.jts.recovery.ibmorb;

import com.arjuna.ats.internal.jts.orbspecific.coordinator.ArjunaTransactionImple;;
import com.arjuna.ats.internal.jts.orbspecific.ibmorb.recoverycoordinators.JavaIdlRCServiceInit;
import com.arjuna.ats.internal.jts.orbspecific.ibmorb.recoverycoordinators.JavaIdlRecoveryInit;
import com.arjuna.ats.internal.jts.recovery.RecoveryCreator;
import com.arjuna.ats.internal.jts.recovery.recoverycoordinators.GenericRecoveryCreator;
import com.hp.mwtests.ts.jts.orbspecific.resources.DemoResource;
import com.hp.mwtests.ts.jts.resources.TestBase;
import org.junit.Test;
import org.omg.CosTransactions.RecoveryCoordinator;

import static org.junit.Assert.assertTrue;

public class IbmORBGenericRecoveryCreatorSuccessUnitTest extends TestBase
{
    @Test
    public void testSuccess () throws Exception
    {
        JavaIdlRCServiceInit init = new JavaIdlRCServiceInit();
        JavaIdlRecoveryInit rinit = new JavaIdlRecoveryInit();
        
        init.startRCservice();
        
        RecoveryCreator creator = RecoveryCreator.getCreator();
        GenericRecoveryCreator generic = null;
        
        if (creator instanceof GenericRecoveryCreator)
            generic = (GenericRecoveryCreator) creator;
        
        assertTrue(generic != null);
        
        DemoResource demo = new DemoResource();
        ArjunaTransactionImple tx = new ArjunaTransactionImple(null);
        Object[] params = new Object[1];
        
        params[0] = tx;
        
        RecoveryCoordinator rc = generic.create(demo.getResource(), params);
        
        assertTrue(rc != null);
        
        generic.destroy(rc);
        
        generic.destroyAll(params);
        
        JavaIdlRCServiceInit.shutdownRCService();
    }
}
