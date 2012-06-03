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

import org.codehaus.jackson.annotate.JsonProperty;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;

import android.content.Context;

@SuppressWarnings("serial")
public class SyncpointInstallation extends SyncpointModel {

    @JsonProperty("channel_id")
    private String channelId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("owner_id")
    private String ownerId;

    @JsonProperty("subscription_id")
    private String subscriptionId;

    @JsonProperty("local_db_name")
    private String localDbName;

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getLocalDbName() {
        return localDbName;
    }

    public void setLocalDbName(String localDbName) {
        this.localDbName = localDbName;
    }

    public SyncpointInstallation() {

    }

    public SyncpointInstallation(CouchDbInstance server, CouchDbConnector database) {
        super(server, database);
    }

    public boolean isLocal(Context context) {
        SyncpointSession session = SyncpointSession.sessionInDatabase(context, server, database);
        if(session.getId().equals(getSessionId())) {
            return true;
        }
        return false;
    }

    public CouchDbConnector getLocalDatabase(Context context) {
        if(!isLocal(context)) {
            return null;
        }
        String name = getLocalDbName();
        if(name != null) {
            return server.createConnector(name, false);
        }
        return null;
    }

    /**
     * Starts bidirectional sync of an application database with its server counterpart.
     */
    public void sync(SyncpointSession session, SyncpointChannel channel) {

        String cloudChannelURL = String.format("%s/%s", session.getSyncpointUrl(), channel.getCloudDatabase());

        ReplicationCommand pull = new ReplicationCommand.Builder()
        .source(cloudChannelURL)
        .target(localDbName)
        .continuous(true)
        .build();

        ReplicationCommand push = new ReplicationCommand.Builder()
        .source(localDbName)
        .target(cloudChannelURL)
        .continuous(true)
        .build();

        server.replicate(pull);
        server.replicate(push);
    }

}
