/*
 *******************************************************************************
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package io.narayana.lra.client;

import org.eclipse.microprofile.lra.client.LRAInfo;

import java.net.URL;

public class LRAInfoImpl implements LRAInfo {
    private Exception jsonParseError;
    private String lraId;
    private String clientId;
    private boolean isComplete;
    private boolean isCompensated;
    private boolean isRecovering;
    private boolean isActive;
    private boolean isTopLevel;

    public LRAInfoImpl(String lraId) {
        this.lraId = lraId;
    }

    public LRAInfoImpl(URL lraId) {
        this.lraId = lraId.toString();
    }

    public LRAInfoImpl(String lraId, String clientId, boolean isComplete,
                       boolean isCompensated, boolean isRecovering,
                       boolean isActive, boolean isTopLevel) {
        this.lraId = lraId;
        this.clientId = clientId;
        this.isComplete = isComplete;
        this.isCompensated = isCompensated;
        this.isRecovering = isRecovering;
        this.isActive = isActive;
        this.isTopLevel = isTopLevel;
        this.jsonParseError = null;
    }

    public LRAInfoImpl(Exception e) {
        this.jsonParseError = e;
        this.lraId = "JSON Parse Error: " + e.getMessage();
        this.clientId = e.getMessage();
        this.isComplete = false;
        this.isCompensated = false;
        this.isRecovering = false;
        this.isActive = false;
        this.isTopLevel = false;
    }

    public String getLraId() {
        return this.lraId;
    }

    public String getClientId() {
        return this.clientId;
    }

    public boolean isComplete() {
        return this.isComplete;
    }

    public boolean isCompensated() {
        return this.isCompensated;
    }

    public boolean isRecovering() {
        return this.isRecovering;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public boolean isTopLevel() {
        return this.isTopLevel;
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;
        else if (!(o instanceof LRAInfo))
            return false;
        else {
            LRAInfo lraStatus = (LRAInfo)o;
            return this.getLraId().equals(lraStatus.getLraId());
        }
    }

    public int hashCode() {
        return this.getLraId().hashCode();
    }
}
