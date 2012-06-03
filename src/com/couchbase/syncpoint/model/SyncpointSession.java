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

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.couchbase.syncpoint.impl.SyncpointClientImpl;
import com.couchbase.syncpoint.impl.SyncpointModelFactory;
import com.couchbase.touchdb.TDMisc;

@SuppressWarnings("serial")
public class SyncpointSession extends SyncpointModel {

    private String ownerId;
    private String appId;
    private String syncpointUrl;
    private OAuthCreds oauthCreds;
    private PairingCreds pairingCreds;
    private String pairingType;
    private String pairingToken;
    private String controlDatabase;
    private boolean controlDbSynced = false;

    @JsonProperty("app_id")
    public String getAppId() {
        return appId;
    }

    @JsonProperty("app_id")
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @JsonProperty("owner_id")
    public String getOwnerId() {
        return ownerId;
    }

    @JsonProperty("owner_id")
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    @JsonProperty("syncpoint_url")
    public String getSyncpointUrl() {
        return syncpointUrl;
    }

    @JsonProperty("syncpoint_url")
    public void setSyncpointUrl(String syncpointUrl) {
        this.syncpointUrl = syncpointUrl;
    }

    @JsonProperty("oauth_creds")
    public OAuthCreds getOauthCreds() {
        return oauthCreds;
    }

    @JsonProperty("oauth_creds")
    public void setOauthCreds(OAuthCreds oauthCreds) {
        this.oauthCreds = oauthCreds;
    }

    @JsonProperty("pairing_creds")
    public PairingCreds getPairingCreds() {
        return pairingCreds;
    }

    @JsonProperty("pairing_creds")
    public void setPairingCreds(PairingCreds pairingCreds) {
        this.pairingCreds = pairingCreds;
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

    @JsonProperty("control_database")
    public String getControlDatabase() {
        return controlDatabase;
    }

    @JsonProperty("control_database")
    public void setControlDatabase(String controlDatabase) {
        this.controlDatabase = controlDatabase;
    }

    @JsonProperty("control_db_synced")
    public boolean isControlDbSynced() {
        return controlDbSynced;
    }

    @JsonProperty("control_db_synced")
    public void setControlDbSynced(boolean controlDbSynced) {
        this.controlDbSynced = controlDbSynced;
    }

    public SyncpointSession(CouchDbInstance server, CouchDbConnector database) {
        super(server, database);
    }

    public SyncpointSession() {

    }

    public static SyncpointSession sessionInDatabase(Context context, CouchDbInstance server, CouchDbConnector database) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sessionId = prefs.getString("Syncpoint_SessionDocID", null);
        if(sessionId == null) {
            return null;
        }
        SyncpointSession result = database.get(SyncpointSession.class, sessionId);
        if(result != null) {
            result.attach(server, database);
        }
        return result;
    }

    public static SyncpointSession makeSessionInDatabase(Context context, CouchDbInstance server, CouchDbConnector database, String appId, URL remoteServerURL) {
        Log.v(SyncpointClientImpl.TAG, String.format("Creating session for %s in %s", appId, database));

        SyncpointSession result = new SyncpointSession(server, database);
        result.setAppId(appId);
        result.setSyncpointUrl(remoteServerURL.toExternalForm());
        result.setState("new");

        OAuthCreds oauthCreds = new OAuthCreds(randomString(), randomString(), randomString(), randomString());
        result.setOauthCreds(oauthCreds);

        PairingCreds pairingCreds = new PairingCreds(String.format("pairing-%s", randomString()), randomString());
        result.setPairingCreds(pairingCreds);

        result.create();

        String sessionID = result.getId();
        Log.v(SyncpointClientImpl.TAG, String.format("...session ID = %s", sessionID));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("Syncpoint_SessionDocID", sessionID);
        editor.commit();

        return result;
    }

    public boolean clearState() {
        setState("new");
        setError(null);

        update();
        return true;
    }

    public SyncpointChannel makeChannel(String name) {
        Log.v(SyncpointClientImpl.TAG, String.format("Create channel named '%s'", name));
        SyncpointChannel channel = new SyncpointChannel(server, database);
        channel.setType("channel");

        if(getOwnerId() != null) {
            channel.setOwnerId(getOwnerId());
            channel.setState("new");
        } else {
            channel.setOwnerId("unpaired");
            channel.setState("unpaired");
        }

        channel.setName(name);
        channel.create();
        return channel;
    }

    @JsonIgnore
    public SyncpointChannel getChannel(String name, String owner) {
        // TODO: Make this into a view query
        Log.v(SyncpointClientImpl.TAG, String.format("Looking for channel named %s with owner_id %s", name, owner));
        List<SyncpointChannel> channels = (List<SyncpointChannel>)SyncpointModelFactory.getModelsOfType(database, "channel", SyncpointChannel.class);
        for (SyncpointChannel channel : channels) {
            Log.v(SyncpointClientImpl.TAG, String.format("Saw channel named %s with owner_id %s and state %s", channel.getName(), channel.getOwnerId(), channel.getState()));

            if(!"error".equals(channel.getState()) && name.equals(channel.getName()) && owner.equals(channel.getOwnerId())) {
                channel.attach(server, database);
                return channel;
            }
        }
        Log.v(SyncpointClientImpl.TAG, String.format("channelWithName %s returning null", name));
        return null;
    }

    @JsonIgnore
    public SyncpointChannel getMyChannel(String name) {
        String owner = null;
        if(ownerId != null) {
            owner = ownerId;
        } else {
            owner = "unpaired";
        }
        return getChannel(name, owner);
    }

    @JsonIgnore
    public PairingUser getPairingUser() {
        String username = pairingCreds.getUsername();
        String password = pairingCreds.getPassword();

        return new PairingUser(String.format("org.couchdb.user:%s", username), username,
                "user", oauthCreds, "new", getPairingType(), getPairingToken(), getAppId(),
                new ArrayList<String>(), password);
    }

    public static String randomString() {
        byte[] randomBytes = new byte[16];
        Random r = new Random();
        r.nextBytes(randomBytes);
        return TDMisc.convertToHex(randomBytes);
    }


    public boolean isReadyToPair() {
        if(pairingToken != null) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public List<SyncpointSubscription> getActiveSubscriptions() {
        // TODO: Make this into a view query
        // TODO: ensure the subscription.owner_id matches the session.owner_id
        List<SyncpointSubscription> result = new ArrayList<SyncpointSubscription>();
        List<SyncpointSubscription> subscriptions = (List<SyncpointSubscription>)SyncpointModelFactory.getModelsOfType(database, "subscription", SyncpointSubscription.class);
        for (SyncpointSubscription subscription : subscriptions) {
            subscription.attach(server, database);
            if(subscription.isActive()) {
                result.add(subscription);
            }
        }
        return result;
    }

    @JsonIgnore
    public List<SyncpointSubscription> getInstalledSubscriptions() {
        //this impl is somewhat different from iOS because i just have the id of the subscription
        //i still have to fetch the subscriptions

        Map<String, SyncpointSubscription> subscriptionMap = new HashMap<String,SyncpointSubscription>();
        List<SyncpointSubscription> subscriptions = (List<SyncpointSubscription>)SyncpointModelFactory.getModelsOfType(database, "subscription", SyncpointSubscription.class);
        for (SyncpointSubscription subscription : subscriptions) {
            subscription.attach(server, database);
            subscriptionMap.put(subscription.getId(), subscription);
        }

        List<SyncpointSubscription> installedSubscriptions = new ArrayList<SyncpointSubscription>();
        List<SyncpointInstallation> installations = getAllInstallations();
        for (SyncpointInstallation installation : installations) {
            SyncpointSubscription subscription = subscriptionMap.get(installation.getSubscriptionId());
            if(subscription == null) {
                Log.e(SyncpointClientImpl.TAG, String.format("Installation %s references missing subscription %s", installation, subscription));
            } else {
                installedSubscriptions.add(subscription);
            }
        }
        return subscriptions;
    }

    @JsonIgnore
    public List<SyncpointInstallation> getAllInstallations() {
        // TODO: Make this into a view query
        List<SyncpointInstallation> result = new ArrayList<SyncpointInstallation>();
        List<SyncpointInstallation> installations = (List<SyncpointInstallation>)SyncpointModelFactory.getModelsOfType(database, "installation", SyncpointInstallation.class);
        for (SyncpointInstallation installation : installations) {
            installation.attach(server, database);
            if("created".equals(installation.getState()) && getId().equals(installation.getSessionId())) {
                result.add(installation);
            }
        }
        return result;
    }

    public void didFirstSyncOfControlDB() {
        setControlDbSynced(true);
        update();
    }

    @JsonIgnore
    public List<SyncpointChannel> getMyChannels() {
        List<SyncpointChannel> result = new ArrayList<SyncpointChannel>();
        List<SyncpointChannel> channels = (List<SyncpointChannel>)SyncpointModelFactory.getModelsOfType(database, "channel", SyncpointChannel.class);
        for (SyncpointChannel channel : channels) {
            channel.attach(server, database);
            if(ownerId.equals(channel.getOwnerId())) {
                result.add(channel);
            }
        }
        return result;
    }

    @JsonIgnore
    public List<SyncpointChannel> getUnpairedChannels() {
        List<SyncpointChannel> result = new ArrayList<SyncpointChannel>();
        List<SyncpointChannel> channels = (List<SyncpointChannel>)SyncpointModelFactory.getModelsOfType(database, "channel", SyncpointChannel.class);
        for (SyncpointChannel channel : channels) {
            channel.attach(server, database);
            if(!channel.isPaired()) {
                result.add(channel);
            }
        }
        return result;
    }
}
