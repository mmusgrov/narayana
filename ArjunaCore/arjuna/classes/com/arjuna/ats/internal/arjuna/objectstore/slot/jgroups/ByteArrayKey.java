package com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

public class  ByteArrayKey implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final byte[] key;

    public ByteArrayKey(byte[] key) {
        this.key = key != null ? key.clone() : null;
    }

    public byte[] getKey() {
        return key != null ? key.clone() : null;
    }

    @Override
    public int hashCode() {
        // Don't cache - recalculate each time to avoid issues with transient fields
        return key != null ? Arrays.hashCode(key) : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        // Handle cross-classloader comparison
        if (!obj.getClass().getName().equals(this.getClass().getName())) {
            return false;
        }
        // Use reflection to get the key field if it's from a different class loader
        try {
            if (obj instanceof ByteArrayKey) {
                return Arrays.equals(key, ((ByteArrayKey) obj).key);
            } else {
                // Cross-class loader case
                byte[] otherKey = (byte[]) obj.getClass().getMethod("getKey").invoke(obj);
                return Arrays.equals(key, otherKey);
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ByteArrayKey" + Arrays.toString(key);
    }
}
