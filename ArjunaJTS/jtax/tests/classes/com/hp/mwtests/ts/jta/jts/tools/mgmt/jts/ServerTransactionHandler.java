package com.hp.mwtests.ts.jta.jts.tools.mgmt.jts;

import com.hp.mwtests.ts.jta.jts.tools.mgmt.ARHandler;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.BAHandlerImpl;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.HeaderStateReader;

public class ServerTransactionHandler extends BAHandlerImpl implements ARHandler {
    public ServerTransactionHandler(String typeName) {
        super(typeName);
    }

    @Override
    public HeaderStateReader getHeaderStateReader(String typeName) {
        return new ServerTransactionHeaderReader();
    }
}
