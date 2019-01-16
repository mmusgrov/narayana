package org.jboss.narayana.grpc.resource;

public class Otid {
    private int formatID;
    private int bqual_length;
    private byte[] tid;

    public Otid(int formatID, int bqualLength, byte[] tid) {
        this.formatID = formatID;
        this.bqual_length = bqualLength;
        this.tid = tid;
    }

    public int getFormatID() {
        return formatID;
    }

    public int getBqual_length() {
        return bqual_length;
    }

    public byte[] getTid() {
        return tid;
    }
}
