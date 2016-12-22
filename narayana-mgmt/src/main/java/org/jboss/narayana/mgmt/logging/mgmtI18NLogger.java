/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and/or its affiliates,
 * and individual contributors as indicated by the @author tags.
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
 * (C) 2010,
 * @author JBoss, by Red Hat.
 */
package org.jboss.narayana.mgmt.logging;

import com.arjuna.ats.arjuna.common.Uid;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.FATAL;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.TRACE;
import static org.jboss.logging.Logger.Level.WARN;
import static org.jboss.logging.annotations.Message.Format.MESSAGE_FORMAT;

/**
 * i18n log messages for the arjuna module.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com) 2010-06
 */
@MessageLogger(projectCode = "ARJUNA")
public interface mgmtI18NLogger {

    // TODO find the next free range of ids
    /*
        Message IDs are unique and non-recyclable.
        Don't change the purpose of existing messages.
          (tweak the message text or params for clarification if you like).
        Allocate new messages by following instructions at the bottom of the file.
     */

	@Message(id = 12202, value = "registering bean {0}.", format = MESSAGE_FORMAT)
	@LogMessage(level = INFO)
	public void info_tools_osb_util_JMXServer_m_1(String arg0);

	@Message(id = 12203, value = "Instance already exists: {0}.", format = MESSAGE_FORMAT)
	@LogMessage(level = INFO)
	public void info_tools_osb_util_JMXServer_m_2(String arg0);

	@Message(id = 12204, value = "Error registering {0}", format = MESSAGE_FORMAT)
	@LogMessage(level = WARN)
	public void warn_tools_osb_util_JMXServer_m_3(String arg0, @Cause() Throwable arg1);

	@Message(id = 12206, value = "Unable to unregister bean {0}", format = MESSAGE_FORMAT)
	@LogMessage(level = WARN)
	public void warn_tools_osb_util_JMXServer_m_5(String arg0, @Cause() Throwable arg1);

	@Message(id = 12207, value = "Unable to unregister bean {0}", format = MESSAGE_FORMAT)
	@LogMessage(level = WARN)
	public void warn_tools_osb_util_JMXServer_m_6(String arg0, @Cause() Throwable arg1);

	@Message(id = 12389, value = "OSB: Error constructing record header reader: {0}", format = MESSAGE_FORMAT)
    @LogMessage(level = INFO)
    public void info_osb_HeaderStateCtorInfo(String reason);

    /*
        Allocate new messages directly above this notice.
          - id: use the next id number in numeric sequence. Don't reuse ids.
          The first two digits of the id(XXyyy) denote the module
            all message in this file should have the same prefix.
          - value: default (English) version of the log message.
          - level: according to severity semantics defined at http://docspace.corp.redhat.com/docs/DOC-30217
          Debug and trace don't get i18n. Everything else MUST be i18n.
          By convention methods with String return type have prefix get_,
            all others are log methods and have prefix <level>_
     */
    

}
