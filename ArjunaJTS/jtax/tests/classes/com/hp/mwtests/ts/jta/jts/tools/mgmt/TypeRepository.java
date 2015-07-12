package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.state.InputObjectState;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TypeRepository {
    static Map<String, ARHandler> handlers = new HashMap<> ();
    private RecoveryStore store;

    public TypeRepository(RecoveryStore store) {
        this.store = store;
        registerTypeHandler(new BAHandlerImpl());
    }

    public static void registerTypeHandler(ARHandler... newHandlers) {
        for (ARHandler handler : newHandlers) {
            handlers.put(ObjStoreMBeanON.canonicalType(handler.getType()), handler);
        }
    }

    public static void registerTypeHandler(String typeName, ARHandler handler) {
        handlers.put(ObjStoreMBeanON.canonicalType(typeName), handler);
    }

    public ARHandler lookupType(String typeName) {
        if (handlers.containsKey(typeName))
            return handlers.get(typeName);

        return null;
    }

    public void checkForNewTypes(boolean exposeAllLogRecords) {
        InputObjectState types = new InputObjectState();

        try {
            if (store.allTypes(types)) {

                while (true) {
                    try {
                        String typeName = ObjStoreMBeanON.canonicalType(types.unpackString());

                        if (typeName.length() == 0)
                            break;

                        if (exposeAllLogRecords && !handlers.containsKey(typeName)) {
                            ARHandler handler = null;

                            // search for a handler that is in the same type hierarchy as typeName
                            for (Map.Entry<String, ARHandler> e : handlers.entrySet()) {
                                handler = e.getValue().getHandler(typeName);

                                if (handler != null)
                                    break;
                            }

                            if (handler == null)
                                handler = new GenericHandler(typeName);

                            registerTypeHandler(typeName, handler);
                        }
                    } catch (IOException e1) {
                        break;
                    }
                }
            }
        } catch (ObjectStoreException e) {
            if (tsLogger.logger.isTraceEnabled())
                tsLogger.logger.trace(e.toString());
        }
    }

    public Set<String> getTypes() {
        return handlers.keySet();
    }

    public void clear() {
        handlers.clear();
    }
}