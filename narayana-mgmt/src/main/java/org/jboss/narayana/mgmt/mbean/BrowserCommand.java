/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates,
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
 * (C) 2014,
 * @author JBoss, by Red Hat.
 */
package org.jboss.narayana.mgmt.mbean;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.*;

import com.arjuna.orbportability.OA;
import com.arjuna.orbportability.ORB;
import org.jboss.narayana.mgmt.util.JMXServer;

import javax.management.*;

public abstract class BrowserCommand {
    private static final String SYNTAX = "syntax: [-s <store location>] | [-f <command file>] | [-c <command>]";

    private static LogBrowser lb;

    private static String currentStoreDir;// = "/home/mmusgrov/tmp/tx-object-store";
    private static String currentType = "";
    private static List<String> recordTypes = new ArrayList<String> ();
    private static InputStream cmdSource;
    private static boolean isHQStore;

    private static ORB orb;
    private static OA oa;

    private enum CommandName {
        HELP("show command options and syntax"),
        SELECT("<type> - start browsing a particular transaction type"),
        STORE_DIR("get/set the location of the object store (set fails due to JBTM-2654"),
        START(null),
        TYPES("list record types"),
        PROBE("refresh the view of the object store"),
        LS("[type] - list transactions of type type. Use the select command to set the default type"),
        QUIT("exit the browser"),
        EXCEPTION_TRACE("true | false - show full exception traces");

        String cmdHelp;

        CommandName(String cmdHelp) {
            this.cmdHelp = cmdHelp;
        }
    }

    private static BrowserCommand getCommand(CommandName name) {
        return getCommand(name.name());
    }

    private static BrowserCommand getCommand(String name) {
        name = name.toUpperCase();

        for (BrowserCommand command : commands) {
            if (command.name.name().startsWith(name))
                return command;
        }

        return getCommand(CommandName.HELP);
    }

    private static void parseArgs(String[] args) throws FileNotFoundException {
        String validOpts = "fsch";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if (i + 1 >= args.length || args[i].length() != 2 || validOpts.indexOf(args[i].charAt(1)) == -1)
                    throw new IllegalArgumentException(SYNTAX);

                switch (args[i++].charAt(1)) {
                    case 'f':
                        File f = validateFile(args[i], false);
                        Scanner s = new Scanner(new FileInputStream(f));

                        while (s.hasNext()) {
                            String ln = s.nextLine();

                            if (!setCurrentStoreDir(ln))
                                sb.append(ln.trim()).append(System.lineSeparator());
                        }

                        break;
                    case 's':
                        currentStoreDir = args[i];

                        break;
                    case 'c':
                        if (!setCurrentStoreDir(args[i]))
                            sb.append(args[i].trim()).append(System.lineSeparator());

                        break;
                    case 'h':
                        isHQStore = Boolean.valueOf(args[i]);
                        break;
                    default:
                        throw new IllegalArgumentException(SYNTAX);
                }
            }
        }

        if (cmdSource == null)
            cmdSource = sb.length() == 0 ? System.in : new ByteArrayInputStream(sb.toString().getBytes());
    }

    private static File validateFile(String name, boolean isDir) {
        File f = new File(name);

        if (!f.exists() || isDir == f.isFile())
            throw new IllegalArgumentException("File " + name + " does not exist");

        return f;
    }

    private static boolean setCurrentStoreDir(String val) {
        if (val.trim().toUpperCase().startsWith(CommandName.STORE_DIR.name())) {
            String[] aa = val.split("\\s+");

            if (aa.length < 2)
                throw new IllegalArgumentException("Invalid syntax for command " + CommandName.STORE_DIR.name());

            currentStoreDir = aa[1];

            return true;
        }

        return false;
    }


    public static void main(String[] args) throws Exception {
        parseArgs(args);
        BrowserCommand.getCommand(CommandName.START).execute(new PrintStream(System.out, true), null);
    }

    CommandName name;
    boolean verbose;

    private BrowserCommand(CommandName name) {
        this.name = name;
    }

    abstract void execute(PrintStream printStream, List<String> args) throws Exception;

    protected void help(PrintStream printStream) {
        if (name.cmdHelp != null)
            printStream.printf("%s - %s%n", name.name().toLowerCase(), name.cmdHelp);
    }

    protected boolean cancel() {return true;}

    private static BrowserCommand[] commands = {

            new BrowserCommand(CommandName.HELP) {
                @Override
                void execute(PrintStream printStream, List<String> args) {
                    for (BrowserCommand command : commands)
                        command.help(printStream);
                }
            },

            new BrowserCommand(CommandName.START) {
                boolean finished;

                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    String st = isHQStore ? StoreKey.StoreType.HornetqObjectStoreAdaptor.name() : null;
                    lb = LogBrowser.getBrowser(new StoreKey(st, currentStoreDir));

                    Scanner scanner = new Scanner(cmdSource);

                    getCommand(CommandName.PROBE).execute(printStream, null);
                    getCommand(CommandName.TYPES).execute(printStream, null);

                    while (!finished)
                        processCommand(printStream, scanner);

                    scanner.close();

                    lb.dispose();
                 }

                protected boolean cancel() {
                    finished = true;
                    try {
                        cmdSource.close();
                    } catch (IOException ignore) {
                    }
                    return true;
                }

                private void processCommand(PrintStream printStream, Scanner scanner) {
                    printStream.printf("%s> ", currentType);

                    List<String> args = new ArrayList<String> (Arrays.asList(scanner.nextLine().split("\\s+")));
                    BrowserCommand command = args.size() == 0 ? getCommand(CommandName.HELP) : getCommand(args.remove(0));

                    try {
                        command.execute(printStream, args);
                    } catch (Exception e) {
                        printStream.printf("%s%n", e.getMessage());

                        if (verbose)
                            e.printStackTrace(printStream);
                    }
                }
            },

            new BrowserCommand(CommandName.QUIT) {

                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    getCommand(CommandName.START).cancel();
                }
            },

            new BrowserCommand(CommandName.STORE_DIR) {

                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    if (args.size() == 0)
                        printStream.print(currentStoreDir);

//                    setupStore(args.get(0)); // TODO replace browser
                }
            },

            new BrowserCommand(CommandName.PROBE) {

                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    lb.probe();
                }
            },

            new BrowserCommand(CommandName.EXCEPTION_TRACE) {

                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    BrowserCommand startCmd = getCommand(CommandName.START);

                    if (args.size() == 1)
                        startCmd.verbose = Boolean.parseBoolean(args.get(0));

                    printStream.printf("exceptionTrace is %b", startCmd.verbose);
                }
            },

            new BrowserCommand(CommandName.TYPES) {
                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    recordTypes = lb.getRecordTypes();
                }
            },

            new BrowserCommand(CommandName.SELECT) {

                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    if (args.size() < 1)
                        currentType = "";
                    else if (!recordTypes.contains(args.get(0)))
                        printStream.printf("%s is not a valid transaction type%n", args.get(0));
                    else
                        currentType = args.get(0);
                }
            },

            new BrowserCommand(CommandName.LS) {
                @Override
                void execute(PrintStream printStream, List<String> args) throws Exception {
                    if (args.size() > 0)
                        getCommand(CommandName.SELECT).execute(printStream, args);

                    if (currentType.length() == 0) {
                        for (String type : recordTypes)
                            listMBeans(printStream, type);
//                        printStream.printf("No type selected. Choose one of:%n");
//                        getCommand(CommandName.TYPES).execute(printStream, null);
//                        help(printStream);
                    } else {
                        //List<UidWrapper> uids = osb.probe(currentType);
                        listMBeans(printStream, currentType);
                    }
                }

                void listMBeans(PrintStream printStream, String itype) throws MalformedObjectNameException, ReflectionException, InstanceNotFoundException, IntrospectionException {
                    MBeanServer mbs = JMXServer.getAgent().getServer();
                    String osMBeanName = "jboss.jta:type=ObjectStore,itype=" + itype;
                    //Set<ObjectInstance> allTransactions = mbs.queryMBeans(new ObjectName("jboss.jta:type=ObjectStore,*"), null);
                    Set<ObjectInstance> transactions = mbs.queryMBeans(new ObjectName(osMBeanName + ",*"), null);

                    printStream.printf("Transactions of type %s%n", osMBeanName);
                    for (ObjectInstance oi : transactions) {
                        String transactionId = oi.getObjectName().getCanonicalName();

                        if (!transactionId.contains("puid") && transactionId.contains("itype")) {
                            printStream.printf("Transaction: %s%n", oi.getObjectName());
                            String participantQuery =  transactionId + ",puid=*";
                            Set<ObjectInstance> participants = mbs.queryMBeans(new ObjectName(participantQuery), null);

                            printAtrributes(printStream, "\t", mbs, oi);

                            printStream.printf("\tParticipants:%n");
                            for (ObjectInstance poi : participants) {
                                printStream.printf("\t\tParticipant: %s%n", poi);
                                printAtrributes(printStream, "\t\t\t", mbs, poi);
                            }
                        }
                    }
                }

                void printAtrributes(PrintStream printStream, String printPrefix, MBeanServer mbs, ObjectInstance oi)
                        throws IntrospectionException, InstanceNotFoundException, ReflectionException {
                    MBeanInfo info = mbs.getMBeanInfo( oi.getObjectName() );
                    MBeanAttributeInfo[] attributeArray = info.getAttributes();
                    int i = 0;
                    String[] attributeNames = new String[attributeArray.length];

                    for (MBeanAttributeInfo ai : attributeArray)
                        attributeNames[i++] = ai.getName();

                    AttributeList attributes = mbs.getAttributes(oi.getObjectName(), attributeNames);

                    for (javax.management.Attribute attribute : attributes.asList()) {
                        Object value = attribute.getValue();
                        String v =  value == null ? "null" : value.toString();

                        printStream.printf("%s%s=%s%n", printPrefix, attribute.getName(), v);
                    }
                }
            },
    };
}
