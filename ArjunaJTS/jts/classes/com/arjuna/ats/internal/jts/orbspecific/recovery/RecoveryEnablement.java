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
 * Copyright (C) 2001,
 *
 * Arjuna Solutions Limited,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.
 *
 * $Id: RecoveryEnablement.java 2342 2006-03-30 13:06:17Z  $
 *
 */

package com.arjuna.ats.internal.jts.orbspecific.recovery;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.arjuna.ats.arjuna.recovery.RecoveryActivator;
import com.arjuna.ats.internal.arjuna.common.ClassloadingUtility;
import com.arjuna.ats.internal.jts.Implementations;
import com.arjuna.ats.internal.jts.recovery.RecoveryInit;
import com.arjuna.ats.internal.jts.recovery.recoverycoordinators.RecoveryServiceInit;
import com.arjuna.ats.jts.logging.jtsLogger;
import com.arjuna.orbportability.ORBInfo;
import com.arjuna.orbportability.ORBType;

/**
 * Registers the appropriate classes with the ORB.
 *   An application using the Transaction Service should load an instance of this class
 *   prior to orb-initialisation.
 *   Loading an instance can be achieved by naming the class in an OrbPreInit
 *   property.
 *   <p>Orb-specific details of recovery are handled by this class.
 *  <p>
 *  The class also includes the static startRCservice method (package access),
 *  used by the RecoveryManager, which is orb-specific
 *
 *
 * @author Peter Furniss (peter.furniss@arjuna.com), Mark Little (mark@arjuna.com), Malik Saheb (malik.saheb@arjuna.com)
 * @version $Id: RecoveryEnablement.java 2342 2006-03-30 13:06:17Z  $
 * @since JTS 2.1.
 *
 */

public class RecoveryEnablement implements RecoveryActivator
{

    private static boolean _isNormalProcess = true;
    private static String  _RecoveryManagerTag = null;

    /**
     * Constructor does the work as a result of being registered as an ORBPreInit
     * class
     */

    public RecoveryEnablement ()
    {
        Implementations.initialise();
    }

    /**
     * This static method is used by the RecoveryManager to suppress
     * aspects of recovery enablement in it's own
     * process, without requiring further property manipulations
     * 
     * @deprecated Only used by tests
     */

    public static void isNotANormalProcess()
    {
        _isNormalProcess = false;
    }

    /**
     * 
     * @deprecated Only used by tests
     */
    public static boolean isNormalProcess ()
    {
        return _isNormalProcess;
    }

    /**
     *  Used by the RecoveryManager to start the recoverycoordinator
     *  service, using whatever orb-specific techniques are appropriate.
     *  This is placed here because it may need to set post-orbinitialisation
     *  properties, like the regular enabler.
     */

    public boolean startRCservice()
    {
        int orbType = ORBInfo.getOrbEnumValue();
        RecoveryServiceInit recoveryService = null;
        boolean outcome = false;
        String theClassName = null;

        // The class that should start the service shall not be called directly. An intermediate class shall be used
        switch (orbType)
        {
            case ORBType.JACORB:
                theClassName = "com.arjuna.ats.internal.jts.orbspecific.jacorb.recoverycoordinators.JacOrbRCServiceInit";
                break;
            case ORBType.JAVAIDL:
                theClassName = "com.arjuna.ats.internal.jts.orbspecific.javaidl.recoverycoordinators.JavaIdlRCServiceInit";
                break;
            case ORBType.IBMORB:
                // ibm jdk7 bundles the sun orb
                theClassName = "com.arjuna.ats.internal.jts.orbspecific.javaidl.recoverycoordinators.JavaIdlRCServiceInit";
                break;
            default: {
                jtsLogger.i18NLogger.warn_recovery_RecoveryEnablement_1();
                return false;
            }
        }

        recoveryService = ClassloadingUtility.loadAndInstantiateClass(RecoveryServiceInit.class, theClassName, null);

        if(recoveryService == null) {
            jtsLogger.i18NLogger.warn_recovery_RecoveryEnablement_6();
        } else {
            outcome = recoveryService.startRCservice();
        }

        return outcome;
    }

    /**
     * Return the RecoveryManager tag. This can be set by a property.
     */
    public static String getRecoveryManagerTag()
    {
        if (_RecoveryManagerTag != null) {
            return _RecoveryManagerTag;
        } else {
            return null;
        }
    }

    /**
     * Static block informs RecoveryInit class that this is not a normal TS user.
     * Also, initializes recovery manager's tag.
     */
    static{

        // tell the recovery init we aren't a normal TS-user
        RecoveryEnablement.isNotANormalProcess();
        RecoveryInit.isNotANormalProcess();

        // see if there is a property defining the recoverymanager
        // servicename

        //	_RecoveryManagerTag = System.getProperty(RecoveryEnvironment.RECOVERY_MANAGER_TAG);

        if (_RecoveryManagerTag == null)
        {
            // if no property, use the hostname/ip address

            InetAddress thisAddress = null;
            try
            {
                thisAddress = InetAddress.getLocalHost();
                _RecoveryManagerTag = thisAddress.getHostName();
            }
            catch (UnknownHostException uhe)
            {
                uhe.printStackTrace();
            }

        }
        // prune off any spaces
        if (_RecoveryManagerTag != null)
        {
            _RecoveryManagerTag =_RecoveryManagerTag.trim();
        }
    }

}

