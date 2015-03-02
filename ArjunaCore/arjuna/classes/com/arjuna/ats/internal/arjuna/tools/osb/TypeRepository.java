package com.arjuna.ats.internal.arjuna.tools.osb;

import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.InputObjectState;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TypeRepository {
    static Map<String, ARTypeHandler> handlers = new HashMap<> ();

    public static void registerTypeHandler(ARTypeHandler ... newHandlers) {
        for (ARTypeHandler handler : newHandlers) {
            handler.setCanonicalType(TypeRepository.canonicalType(handler.getType()));
            handlers.put(TypeRepository.canonicalType(handler.getType()), handler);
        }
    }

    public static void registerTypeHandler(String typeName, ARTypeHandler handler) {
        handlers.put(typeName, handler);
    }

    public static ARTypeHandler lookupType(String typeName) {
        if (handlers.containsKey(typeName))
            return handlers.get(typeName);

        return null;
    }

    public static String canonicalType(String type) {
        if (type == null)
            return "";

        type = type.replace(File.separator, "/");

        while (type.startsWith("/"))
            type = type.substring(1);

        return type;
    }

    public void checkForNewTypes(boolean exposeAllLogRecords) {
        InputObjectState types = new InputObjectState();

        try {
            if (StoreManager.getRecoveryStore().allTypes(types)) {

                while (true) {
                    try {
                        String typeName = canonicalType(types.unpackString());

                        if (typeName.length() == 0)
                            break;

                        if (exposeAllLogRecords && !handlers.containsKey(typeName)) {
                            ARTypeHandler handler = null;

                            // search for a handler that is in the same type hierarchy as typeName
                            for (Map.Entry<String, ARTypeHandler> e : handlers.entrySet()) {
                                if (typeName.startsWith(e.getKey())) {
                                    handler = e.getValue().getHandler(typeName);

                                    if (handler != null)
                                        break;
                                }
                            }

                            if (handler == null)
                                handler = new ARTypeHandlerImpl(typeName);

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