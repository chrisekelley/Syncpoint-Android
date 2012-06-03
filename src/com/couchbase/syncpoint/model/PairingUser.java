/**
 * Original iOS version by  Jens Alfke & Chris Anderson
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.syncpoint.model;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.ektorp.support.OpenCouchDbDocument;

@SuppressWarnings("serial")
public class PairingUser extends OpenCouchDbDocument {

    private String name;
    private String type;
    private OAuthCreds oauthCreds;
    private String pairingState;
    private String pairingType;
    private String pairingToken;
    private String pairingAppId;
    private List<String> roles;
    private String password;
    private String ownerId;
    private String controlDatabase;

    public PairingUser() {

    }

    public PairingUser(String id, String name, String type, OAuthCreds oauthCreds, String pairingState, String pairingType, String pairingToken, String pairingAppId, List<String> roles, String password) {
        setId(id);
        this.name = name;
        this.type = type;
        this.oauthCreds = oauthCreds;
        this.pairingState = pairingState;
        this.pairingType = pairingType;
        this.pairingToken = pairingToken;
        this.pairingAppId = pairingAppId;
        this.roles = roles;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("sp_oauth")
    public OAuthCreds getOauthCreds() {
        return oauthCreds;
    }

    @JsonProperty("sp_oauth")
    public void setOauthCreds(OAuthCreds oauthCreds) {
        this.oauthCreds = oauthCreds;
    }

    @JsonProperty("pairing_state")
    public String getPairingState() {
        return pairingState;
    }

    @JsonProperty("pairing_state")
    public void setPairingState(String pairingState) {
        this.pairingState = pairingState;
    }

    @JsonProperty("pairing_type")
    public String getPairingType() {
        return pairingType;
    }

    @JsonProperty("pairing_type")
    public void setPairingType(String pairingType) {
        this.pairingType = pairingType;
    }

    @JsonProperty("pairing_token")
    public String getPairingToken() {
        return pairingToken;
    }

    @JsonProperty("pairing_token")
    public void setPairingToken(String pairingToken) {
        this.pairingToken = pairingToken;
    }

    @JsonProperty("pairing_app_id")
    public String getPairingAppId() {
        return pairingAppId;
    }

    @JsonProperty("pairing_app_id")
    public void setPairingAppId(String pairingAppId) {
        this.pairingAppId = pairingAppId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @JsonProperty("owner_id")
    public String getOwnerId() {
        return ownerId;
    }

    @JsonProperty("owner_id")
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    @JsonProperty("control_database")
    public String getControlDatabase() {
        return controlDatabase;
    }

    @JsonProperty("control_database")
    public void setControlDatabase(String controlDatabase) {
        this.controlDatabase = controlDatabase;
    }

}
