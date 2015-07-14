package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.jts;

import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.internal.jts.resources.ExtendedResourceRecord;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.*;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.jts.XAResourceBeanImpl;

import javax.management.*;

public class ExtendedResourceRecordHandler implements ARPHandler {
    private String type;

    public ExtendedResourceRecordHandler() {
        type = new ExtendedResourceRecord().type();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public LogRecordBean createParticipantBean(BasicActionMXBean baa, AbstractRecord ar, ParticipantStatus listType) {
/*         if (ar instanceof com.arjuna.ats.internal.jta.resources.arjunacore.XAResourceRecord)
            return new com.arjuna.ats.internal.jta.tools.osb.mbean.jta.XAResourceRecordBean(this, ar, listType);
        else if (ar instanceof com.arjuna.ats.internal.jta.resources.arjunacore.CommitMarkableResourceRecord)
            return new com.arjuna.ats.internal.jta.tools.osb.mbean.jta.CommitMarkableResourceRecordBean(this, ar, listType);  */
        if (ar instanceof ExtendedResourceRecord) {
            //  return new XAResourceRecordBean(this, rec, listType);
            XAResourceBeanImpl bean = new XAResourceBeanImpl(null, baa, ar, listType);

            try {
                ObjectName name = new ObjectName(bean.getName());
                ObjectInstance oi = JMXServer.getAgent().getServer().registerMBean(bean, name);
                MBeanInfo info = JMXServer.getAgent().getServer().getMBeanInfo(name);
                MBeanAttributeInfo[] ai = info.getAttributes();
                ;
            } catch (Exception e) {
                e.printStackTrace();
            }


            return bean;
        }

        return new LogRecordBean(baa, ar, listType);
    }
}
