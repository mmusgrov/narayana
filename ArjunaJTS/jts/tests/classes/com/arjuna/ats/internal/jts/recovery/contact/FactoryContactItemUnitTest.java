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

package com.arjuna.ats.internal.jts.recovery.contact;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import com.arjuna.ArjunaOTS.ArjunaFactory;
import com.arjuna.ArjunaOTS.ArjunaFactoryHelper;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.internal.jts.orbspecific.TransactionFactoryImple;
import com.hp.mwtests.ts.jts.resources.TestBase;

public class FactoryContactItemUnitTest extends TestBase
{
    @Test
    public void testNull () throws Exception
    {
        Uid dummyState = new Uid();
        FactoryContactItem item = FactoryContactItem.recreate(dummyState);
        
        assertTrue(item == null);
    }
    
    @Test
    public void test () throws Exception
    {
        TransactionFactoryImple factory = new TransactionFactoryImple("test");
        
        assertTrue(FactoryContactItem.createAndSave(ArjunaFactoryHelper.narrow(factory.getReference())));
        
        FactoryContactItem item = FactoryContactItem.recreate(com.arjuna.ats.arjuna.utils.Utility.getProcessUid());
        
        assertTrue(item != null);       
        assertTrue(item.getFactory() != null);
        
        item.markAsAlive();
        assertTrue(item.getAliveTime() != null);
        
        item.markAsDead();
        assertTrue(item.getCreationTime() != null);
        
        assertTrue(item.getUid() != Uid.nullUid());
    }
}
