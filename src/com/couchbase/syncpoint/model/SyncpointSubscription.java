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

import java.util.Random;

import org.codehaus.jackson.annotate.JsonProperty;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;

import android.content.Context;
import android.util.Log;

import com.couchbase.syncpoint.impl.SyncpointClientImpl;
import com.couchbase.touchdb.TDMisc;

@SuppressWarnings("serial")
public class SyncpointSubscription extends SyncpointModel {

    @JsonProperty("owner_id")
    private String ownerId;

    @JsonProperty("channel_id")
    private String channelId;


    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public SyncpointSubscription() {

    }

    public SyncpointSubscription(CouchDbInstance server, CouchDbConnector database) {
        super(server, database);
    }

    public SyncpointInstallation makeInstallation(Context context, CouchDbConnector localDatabase) {
        String name = null;
        if(localDatabase != null) {
            name = localDatabase.getDatabaseName();
        } else {
            name = String.format("channel-%s", randomString());
            localDatabase = server.createConnector(name, true);
        }

        Log.v(SyncpointClientImpl.TAG, String.format("Installing %s to %s", this, localDatabase.path()));

        SyncpointInstallation inst = new SyncpointInstallation(server, database);
        inst.setType("installation");
        inst.setState("created");
        inst.setSessionId((SyncpointSession.sessionInDatabase(context, server, database).getId()));
        inst.setOwnerId(getOwnerId());
        inst.setChannelId(getChannelId());
        inst.setSubscriptionId(getId());
        inst.setLocalDbName(name);
        inst.create();
        return inst;
    }

    public static String randomString() {
        byte[] randomBytes = new byte[16];
        Random r = new Random();
        r.nextBytes(randomBytes);
        return TDMisc.convertToHex(randomBytes);
    }

}
