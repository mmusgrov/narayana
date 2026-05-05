/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.ByteArrayKey;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlotKeyGenerator;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JGroupsClusterMemberId implements JGroupsSlotKeyGenerator {
    String nodeId;
    String groupId;
    Uid uid;
    String id;
    String keyPattern;

    public JGroupsClusterMemberId() {
        this.uid = new Uid();
        this.groupId = "";
        this.nodeId = "";
    }

    @Override
    public void init(JGroupsStoreEnvironmentBean config) {
        this.groupId = config.getGroupName();
        this.nodeId = config.getNodeAddress();
        this.uid = new Uid();
        this.id = String.format("%s:%s:%s", groupId, nodeId, uid.stringForm());
        this.keyPattern = String.format("{%s}:%s:*", groupId, nodeId); // matches all keys in this group
    }

    /*
     * @return a pattern that matches all keys that share the same groupId and nodeId
     */
    public String allKeysPattern() {
        return keyPattern;
    }

    public ByteArrayKey generateUniqueKey(int index) {
        return new ByteArrayKey(String.format("{%s};%s;%s;%d", groupId, nodeId, uid.stringForm(), index)
                .getBytes(StandardCharsets.UTF_8));
    }

    static final Pattern CB_DELIMITER_REGEX = Pattern.compile("\\{(\\w+)}");

    public static JGroupsClusterMemberId fromUniqueKey(String uniqueKey) {
        String[] split = uniqueKey.split(";");
        if (split.length != 4)
            return null;

        JGroupsClusterMemberId id = new JGroupsClusterMemberId();
        // the groupId is enclosed in curly braces (see the generateUniqueKey method)
        Matcher matcher = CB_DELIMITER_REGEX.matcher(split[0]);
        if (matcher.find()) {
            id.groupId = matcher.group(1);
        }
        id.nodeId = split[1];
        id.uid = new Uid(split[2]);

        return id;
    }

    public static JGroupsClusterMemberId fromUniqueKey(byte[] bytes) {
        return fromUniqueKey(new String(bytes));
    }
}
