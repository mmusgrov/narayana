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

package com.hp.mwtests.ts.jts.interposition;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.omg.CosTransactions.PropagationContext;

import com.arjuna.ats.internal.jts.OTSImpleManager;
import com.arjuna.ats.internal.jts.interposition.resources.restricted.RestrictedInterposition;
import com.arjuna.ats.internal.jts.interposition.resources.restricted.RestrictedInterpositionCreator;
import com.arjuna.ats.internal.jts.orbspecific.ControlImple;
import com.hp.mwtests.ts.jts.resources.TestBase;

public class RestrictedInterpositionUnitTest extends TestBase
{
    @Test
    public void test () throws Exception
    {
        RestrictedInterposition inter = new RestrictedInterposition();
        
        OTSImpleManager.current().begin();
        OTSImpleManager.current().begin();
        
        PropagationContext ctx = OTSImpleManager.current().get_control().get_coordinator().get_txcontext();
        
        ControlImple cont = inter.setupHierarchy(ctx);
        
        RestrictedInterpositionCreator creator = new RestrictedInterpositionCreator();
        
        assertTrue(creator.recreateLocal(ctx) != null);       
        assertTrue(creator.recreate(ctx) != null);
        
        OTSImpleManager.current().rollback();
        OTSImpleManager.current().rollback();
    }
}