/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a full listing 
 * of individual contributors.
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
 * Copyright (C) 2002,
 *
 * Arjuna Technologies Limited,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.
 *
 * $Id: Committed.java,v 1.1 2002/11/25 11:00:53 nmcl Exp $
 */

package com.arjuna.mw.wstx.status;

import com.arjuna.mw.wsas.status.Status;

/**
 * The transaction has committed.
 *
 * @author Mark Little (mark.little@arjuna.com)
 * @version $Id: Committed.java,v 1.1 2002/11/25 11:00:53 nmcl Exp $
 * @since 1.0.
 */

public class Committed implements Status
{

    public static Committed instance ()
    {
	return _instance;
    }
    
    public String toString ()
    {
	return "Status.Committed";
    }

    private Committed ()
    {
    }

    private static final Committed _instance = new Committed();
    
}