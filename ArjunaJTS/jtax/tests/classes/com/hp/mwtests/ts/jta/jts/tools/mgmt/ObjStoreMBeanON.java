package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.common.Uid;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.File;
import java.util.Hashtable;

public class ObjStoreMBeanON {
    public static final String OSB_INST_KEY = "itype";
    public static final String OSB_UID_KEY = "uid";
    public static final String OSB_PARTICIPANT_KEY = "puid";
    public static final String STORE_MBEAN_NAME = "jboss.jta:type=ObjectStore"; // TODO extract type

    public static String ARP_BEAN_NAME_FMT = "%s,itype=%s,uid=%s";
    public static String ARC_BEAN_NAME_FMT = "%s,itype=%s,uid=%s,puid=%s";
    public static String ARP_WC_BEAN_NAME_FMT = "%s,itype=%s,uid=%s,*";
    public static String ARC_WC_BEAN_NAME_FMT = "%s,itype=%s,uid=%s,puid=%s,*";

    private String objectName;
    private String recordType;
    private String parentUid;
    private String participantUid;

    public ObjStoreMBeanON(String recordType, String parentUid) {
        this(recordType, parentUid, null);
    }

    public ObjStoreMBeanON(String recordType, String parentUid, String participantUid) {
        this.recordType = canonicalType(recordType);
        this.parentUid = parentUid;
        this.participantUid = participantUid;

        if (participantUid != null)
            objectName = String.format(ARC_BEAN_NAME_FMT, STORE_MBEAN_NAME, this.recordType, parentUid, participantUid);
        else
            objectName = String.format(ARP_BEAN_NAME_FMT, STORE_MBEAN_NAME, this.recordType, parentUid);
    }

    public static ObjStoreMBeanON fromObjStoreMBeanON(ObjectName childObjectName) throws MalformedObjectNameException {
        return fromObjStoreMBeanON(childObjectName.toString());
    }

    public static ObjStoreMBeanON fromObjStoreMBeanON(String objectName) throws MalformedObjectNameException {
        if (!isObjStoreBeanType(objectName))
            throw new MalformedObjectNameException(objectName);

        ObjectName on = new ObjectName(objectName);

        Hashtable<String, String> propertyList = on.getKeyPropertyList();

        String type = propertyList.get(OSB_INST_KEY);
        String uid = propertyList.get(OSB_UID_KEY);
        String pUid = propertyList.get(OSB_PARTICIPANT_KEY);

/*        for (String pair : objectName.split(",")) {
            String[] kv = pair.split("=");

            if (kv.length != 2)
                throw new MalformedObjectNameException(objectName);

            if (OSB_INST_KEY.equals(kv[0]))
                type = kv[1];
            else if (OSB_UID_KEY.equals(kv[0]))
                uid = kv[1];
            else if (OSB_PARTICIPANT_KEY.equals(kv[0]))
                pUid = kv[1];
        }*/

        if (type == null || uid == null)
            throw new MalformedObjectNameException("Object name is missing an instant type or uid");

        if (pUid == null)
            return new ObjStoreMBeanON(type, uid);

        return new ObjStoreMBeanON(type, uid, pUid);
    }

    public static String generateObjectName(String type, String id) {
        return String.format(ARP_BEAN_NAME_FMT, STORE_MBEAN_NAME, canonicalType(type), id);
    }

    public static String generateObjectName(String type, Uid uid) {
        return String.format(ARP_BEAN_NAME_FMT, STORE_MBEAN_NAME, canonicalType(type), uid.fileStringForm());
    }

    public static String generateParticipantObjectName(String type, Uid parentUid, Uid childUid) {
        return String.format(ARC_BEAN_NAME_FMT, STORE_MBEAN_NAME,
                canonicalType(type), parentUid.fileStringForm(), childUid.fileStringForm());
    }

    public static String canonicalType(String type) {
        if (type == null)
            return "";

        type = type.replace(File.separator, "/");

        while (type.startsWith("/"))
            type = type.substring(1);

        return type;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getRecordType() {
        return recordType;
    }

    public String getParentUid() {
        return parentUid;
    }

    public String getParticipantUid() {
        return participantUid;
    }

    public static boolean isObjStoreBeanType(String objectName) {
        return objectName.startsWith(STORE_MBEAN_NAME);
    }
}
