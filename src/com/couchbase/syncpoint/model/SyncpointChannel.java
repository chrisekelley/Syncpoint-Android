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
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;

import android.content.Context;
import android.util.Log;

import com.couchbase.syncpoint.impl.SyncpointClientImpl;
import com.couchbase.syncpoint.impl.SyncpointModelFactory;

@SuppressWarnings("serial")
public class SyncpointChannel extends SyncpointModel {

    private String name;
    private String ownerId;

    @JsonProperty("cloud_database")
    private String cloudDatabase;

    public SyncpointChannel() {

    }

    public SyncpointChannel(CouchDbInstance server, CouchDbConnector database) {
        super(server, database);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("owner_id")
    public String getOwnerId() {
        return ownerId;
    }

    @JsonProperty("owner_id")
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getCloudDatabase() {
        return cloudDatabase;
    }

    public void setCloudDatabase(String cloudDatabase) {
        this.cloudDatabase = cloudDatabase;
    }

    public SyncpointSubscription getSubscription() {
        // TODO: Make this into a view query
        List<SyncpointSubscription> subscriptions = (List<SyncpointSubscription>)SyncpointModelFactory.getModelsOfType(database, "subscription", SyncpointSubscription.class);
        for (SyncpointSubscription sub : subscriptions) {
            if(sub.getChannelId() == getId()) {
                sub.attach(server, database);
                return sub;
            }
        }
        return null;
    }

    CouchDbConnector getLocalDatabase(Context context) {
        SyncpointInstallation inst = getInstallation(context);
        if(inst != null) {
            return inst.getLocalDatabase(context);
        } else {
            return null;
        }
    }

    public CouchDbConnector ensureLocalDatabase(Context context) {


        SyncpointSubscription sub = getSubscription();
        if(sub == null) {
            sub = subscribe();
        }
        if(sub == null) {
            return null;
        }
        if(getLocalDatabase(context) == null) {
            sub.makeInstallation(context, null);
        }
        CouchDbConnector localDatabase = getLocalDatabase(context);
        return localDatabase;
    }

    public SyncpointInstallation getInstallation(Context context) {
        // TODO: Make this into a view query
        List<SyncpointInstallation> installations = (List<SyncpointInstallation>)SyncpointModelFactory.getModelsOfType(database, "installation", SyncpointInstallation.class);
        for (SyncpointInstallation inst : installations) {
            inst.attach(server, database);
            if(inst.getChannelId().equals(getId()) && inst.isLocal(context)) {
                return inst;
            }
        }
        return null;
    }

    SyncpointSubscription subscribe() {
        Log.v(SyncpointClientImpl.TAG, String.format("Subscribing to %s", this));
        SyncpointSubscription sub = new SyncpointSubscription(server, database);
        sub.setType("subscription");
        sub.setState("active");
        sub.setOwnerId(getOwnerId());
        sub.setChannelId(getId());
        database.create(sub);
        return sub;
    }
}
