package com.hp.mwtests.ts.jta.jts.tools.mgmt.jts;

import com.arjuna.ats.arjuna.state.InputObjectState;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.HeaderState;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.HeaderStateReader;

import java.io.IOException;

public class ServerTransactionHeaderReader extends HeaderStateReader {
    protected HeaderState unpackHeader(InputObjectState os) throws IOException {
        boolean haveRecCoord = os.unpackBoolean();

        if (haveRecCoord)
            os.unpackString(); // read ior

        return super.unpackHeader(os);
    }
}