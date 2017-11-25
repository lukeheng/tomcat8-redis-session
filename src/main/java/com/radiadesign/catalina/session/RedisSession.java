package com.radiadesign.catalina.session;

/**
 * Created by lenovo on 2016/9/21.
 */

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;

import java.security.Principal;
import java.util.HashMap;

public class RedisSession extends StandardSession {
    private static final long serialVersionUID = 1L;
    protected static Boolean manualDirtyTrackingSupportEnabled = Boolean.valueOf(false);
    protected static String manualDirtyTrackingAttributeKey = "__changed__";
    protected HashMap<String, Object> changedAttributes;
    protected Boolean dirty;

    public void setManualDirtyTrackingSupportEnabled(Boolean enabled) {
        manualDirtyTrackingSupportEnabled = enabled;
    }

    public void setManualDirtyTrackingAttributeKey(String key) {
        manualDirtyTrackingAttributeKey = key;
    }

    public RedisSession(Manager manager) {
        super(manager);
        this.resetDirtyTracking();
    }

    public Boolean isDirty() {
        return !this.dirty.booleanValue() && this.changedAttributes.isEmpty()?Boolean.valueOf(false):Boolean.valueOf(true);
    }

    public HashMap<String, Object> getChangedAttributes() {
        return this.changedAttributes;
    }

    public void resetDirtyTracking() {
        this.changedAttributes = new HashMap();
        this.dirty = Boolean.valueOf(false);
    }

    public void setAttribute(String key, Object value) {
        if(manualDirtyTrackingSupportEnabled.booleanValue() && manualDirtyTrackingAttributeKey.equals(key)) {
            this.dirty = Boolean.valueOf(true);
        } else {
            Object oldValue = this.getAttribute(key);
            if(value == null && oldValue != null || oldValue == null && value != null || !value.getClass().isInstance(oldValue) || !value.equals(oldValue)) {
                this.changedAttributes.put(key, value);
            }

            super.setAttribute(key, value);
        }
    }

    public void removeAttribute(String name) {
        this.dirty = Boolean.valueOf(true);
        super.removeAttribute(name);
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPrincipal(Principal principal) {
        this.dirty = Boolean.valueOf(true);
        super.setPrincipal(principal);
    }
}
