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

package com.couchbase.syncpoint.impl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;
import org.ektorp.android.http.AndroidHttpClient;
import org.ektorp.android.util.ChangesFeedAsyncTask;
import org.ektorp.android.util.EktorpAsyncTask;
import org.ektorp.changes.ChangesCommand;
import org.ektorp.changes.DocumentChange;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.couchbase.syncpoint.SyncpointClient;
import com.couchbase.syncpoint.SyncpointState;
import com.couchbase.syncpoint.model.PairingUser;
import com.couchbase.syncpoint.model.SyncpointChannel;
import com.couchbase.syncpoint.model.SyncpointInstallation;
import com.couchbase.syncpoint.model.SyncpointSession;
import com.couchbase.syncpoint.model.SyncpointSubscription;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class SyncpointClientImpl implements SyncpointClient {

    public static final String TAG = "SyncpointClient";
    public static final String LOCAL_CONTROL_DATABASE_NAME = "sp_control";

    private CouchDbInstance localServer;
    private URL remoteServerURL;
    private String appId;
    private SyncpointState state;
    private SyncpointSession session;
    private CouchDbConnector localControlDatabase;
    private Context applicationContext;

    private static CouchDbInstance createLocalTouchDbInstance(Context context) {
        TDURLStreamHandlerFactory.registerSelfIgnoreError();
        String filesDir = context.getFilesDir().getAbsolutePath();
        TDServer server = null;
        try {
            server = new TDServer(filesDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HttpClient httpClient = new TouchDBHttpClient(server);

        CouchDbInstance newLocalServer = new StdCouchDbInstance(httpClient);
        return newLocalServer;
    }

    public SyncpointClientImpl(Context context, URL remoteServer, String syncpointAppId) {
        this(context, createLocalTouchDbInstance(context), remoteServer, syncpointAppId);
    }

    public SyncpointClientImpl(Context context, CouchDbInstance localServer, URL remoteServerURL, String syncpointAppId) {
        this.applicationContext = context.getApplicationContext();
        this.localServer = localServer;
        this.remoteServerURL = remoteServerURL;
        this.appId = syncpointAppId;

        // Create the control database on the first run of the app.
        localControlDatabase = localServer.createConnector(LOCAL_CONTROL_DATABASE_NAME, true);

        session = SyncpointSession.sessionInDatabase(applicationContext, localServer, localControlDatabase);
        if(session == null) {
            // if no session make one
            session = SyncpointSession.makeSessionInDatabase(applicationContext, localServer, localControlDatabase, appId, remoteServerURL);
            state = SyncpointState.UNAUTHENTICATED;
        }
        if(session.isPaired()) {
            Log.v(TAG, "Session is active");
            connectToControlDB();
        }
        else if(session.isReadyToPair()) {
            if(session.getError() != null) {
                Log.v(TAG, String.format("Session has error: %s", session.getError()));
                state = SyncpointState.HAS_ERROR;
            }
            Log.v(TAG, String.format("Begin pairing with cloud: %s", remoteServerURL.toExternalForm()));
            beginPairing();
        }
    }

    boolean isActivated() {
        return state.compareTo(SyncpointState.ACTIVATING) > 0;
    }

    public SyncpointChannel getMyChannel(String channelName) {
        SyncpointChannel channel = session.getMyChannel(channelName);
        if(channel == null) {
            channel = session.makeChannel(channelName);
            if(channel == null) {
                return null;
            }
        }
        return channel;
    }

    @Override
    public void pairSession(String pairingType, String pairingToken) {
        if(session.isPaired()) {
            return;
        }
        session.setPairingType(pairingType);
        session.setPairingToken(pairingToken);
        localControlDatabase.update(session);
        if(session.isReadyToPair()) {
            beginPairing();
        } else {
            state = SyncpointState.UNAUTHENTICATED;
        }
    }

    //************************************************
    // CONTROL DATABASE & SYNC:
    //************************************************

    void pairingDidComplete(final CouchDbConnector remote, final PairingUser userDoc) {
        session.setState("paired");
        session.setOwnerId(userDoc.getOwnerId());
        session.setControlDatabase(userDoc.getControlDatabase());

        EktorpAsyncTask task = new EktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                // TODO Auto-generated method stub
                localControlDatabase.update(session);
            }

            @Override
            protected void onSuccess() {
                Log.v(TAG, "Device is now paired");
                //FIXME this delete is not working, investigate later
                //remote.delete(userDoc);
                connectToControlDB();
            }
        };
        task.execute();
    }

    void beginPairing() {
        Log.v(TAG, "Pairing session...");
        if(session.isReadyToPair()) {
            session.clearState();
            savePairingUserToRemote();
        }
    }

    void savePairingUserToRemote() {
        HttpClient anonRemoteHttpClient = new AndroidHttpClient.Builder().url(remoteServerURL).maxConnections(100).build();
        CouchDbInstance anonRemote = new StdCouchDbInstance(anonRemoteHttpClient);

        //hard-coded to use "_users" for now because Ektorp doesn't have an interface to _session
        CouchDbConnector anonUserDb = anonRemote.createConnector("_users", false);
        final PairingUser pairingUser = session.getPairingUser();
        anonUserDb.update(pairingUser);

        //now connect as this user
        HttpClient remoteHttpClient = new AndroidHttpClient.Builder().url(remoteServerURL).username(session.getPairingCreds().getUsername()).password(session.getPairingCreds().getPassword()).maxConnections(100).build();
        CouchDbInstance remote = new StdCouchDbInstance(remoteHttpClient);

        final CouchDbConnector userDb = remote.createConnector("_users", false);

        EktorpAsyncTask task = new EktorpAsyncTask() {

            PairingUser result = null;

            @Override
            protected void doInBackground() {
                result = userDb.get(PairingUser.class, pairingUser.getId());
            }

            @Override
            protected void onSuccess() {
                waitForPairingToComplete(userDb, result);
            }

        };

        task.execute();
    }

    void waitForPairingToComplete(final CouchDbConnector remote, final PairingUser userDoc) {
        Log.v(TAG, "Waiting for pairing to complete...");
        Looper l = Looper.getMainLooper();
        Handler h = new Handler(l);
        h.postDelayed(new Runnable() {

            @Override
            public void run() {
                Log.v(TAG, "Checking to see if pairing completed...");
                PairingUser user = remote.get(PairingUser.class, userDoc.getId());
                if("paired".equals(user.getPairingState())) {
                    pairingDidComplete(remote, user);
                } else {
                    Log.v(TAG, "Pairing state is stuck at " + user.getPairingState());
                    waitForPairingToComplete(remote, user);
                }
            }
        }, 3000);

    }

    private ReplicationCommand pullControlData(String databaseName, boolean continuous) {
        URL remoteControlURL;
        try {
            remoteControlURL = new URL(remoteServerURL, databaseName);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return new ReplicationCommand.Builder()
        .source(remoteControlURL.toExternalForm())
        .target(localControlDatabase.getDatabaseName())
        .continuous(continuous)
        .build();
    }

    private ReplicationCommand pushControlData(String databaseName, boolean continuous) {
        URL remoteControlURL;
        try {
            remoteControlURL = new URL(remoteServerURL, databaseName);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return new ReplicationCommand.Builder()
        .source(localControlDatabase.getDatabaseName())
        .target(remoteControlURL.toExternalForm())
        .continuous(continuous)
        .build();
    }

    void connectToControlDB() {
        Log.v(TAG, String.format("connectToControlDB %s", localControlDatabase));
        if(!session.isControlDbSynced()) {
            doInitialSyncOfControlDB();
        } else {
            didInitialSyncOfControlDB();
        }
    }

    void doInitialSyncOfControlDB() {
        Log.v(TAG, "doInitialSyncOfControlDB");
        final ReplicationCommand controlPull = pullControlData(session.getControlDatabase(), false);
        EktorpAsyncTask controlPullTask = new EktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                //non-continuous replication will block till completion
                localServer.replicate(controlPull);
            }

            @Override
            protected void onSuccess() {
                //when the initial controlPull stops running, after doInitialSyncOfControlDB
                mergeExistingChannels();
                didInitialSyncOfControlDB();
            }
        };
        controlPullTask.execute();
    }

    void didInitialSyncOfControlDB() {
        Log.v(TAG, "didInitialSyncOfControlDB");
        // Now we can sync continuously & push
        ReplicationCommand controlPull = pullControlData(session.getControlDatabase(), true);
        localServer.replicate(controlPull);
        ReplicationCommand controlPush = pushControlData(session.getControlDatabase(), true);
        localServer.replicate(controlPush);
        session.didFirstSyncOfControlDB();
        Looper l = Looper.getMainLooper();
        Handler h = new Handler(l);
        h.postDelayed(new Runnable() {

            @Override
            public void run() {
                // switched the order here from iOS, appeared i was seeing a race-condition
                // where i missed seeing changes
                observeControlDatabase();
                getUpToDateWithSubscriptions();
            }
        }, 1000);
    }

    void getUpToDateWithSubscriptions() {
        Log.v(TAG, "getUpToDateWithSubscriptions");
        // Make installations for any subscriptions that don't have one:
        List<SyncpointSubscription> installedSubscriptions = session.getInstalledSubscriptions();
        List<SyncpointSubscription> activeSubscriptions = session.getActiveSubscriptions();
        for (SyncpointSubscription subscription : activeSubscriptions) {
            subscription.attach(localServer, localControlDatabase);
            if(!installedSubscriptions.contains(subscription)) {
                Log.v(SyncpointClientImpl.TAG, String.format("Making installation db for %s", subscription));
                subscription.makeInstallation(applicationContext, null);  // TODO: Report error
            }
        }

        //again this part of the implementation differs because we just
        //have channel ids and need to load the channels
        Map<String, SyncpointChannel> channelMap = new HashMap<String, SyncpointChannel>();
        List<SyncpointChannel> channels = (List<SyncpointChannel>)SyncpointModelFactory.getModelsOfType(localControlDatabase, "channel", SyncpointChannel.class);
        for (SyncpointChannel channel : channels) {
            channel.attach(localServer, localControlDatabase);
            channelMap.put(channel.getId(), channel);
        }


        // Sync all installations whose channels are ready:
        List<SyncpointInstallation> allInstallations = session.getAllInstallations();
        Log.v(TAG, String.format("There are %d installations here", allInstallations.size()));
        for (SyncpointInstallation installation : allInstallations) {
            SyncpointChannel channel = channelMap.get(installation.getChannelId());
            if(channel == null) {
                Log.e(TAG, String.format("Installation %s references missing channel %s", installation, channel));
            } else if(channel.isReady()) {
                Log.v(TAG, String.format("Channel %s is ready, calling sync", channel.getName()));
                installation.sync(session, channel);
            } else {
                Log.v(TAG, String.format("Channel %s is not ready", channel.getName()));
            }

        }
    }

    void mergeExistingChannels() {
        Log.v(TAG, "mergeExistingChannels");
        List<SyncpointChannel> pairedChannels = session.getMyChannels();
        List<SyncpointChannel> unpairedChannels = session.getUnpairedChannels();
        boolean matched = false;
        for (SyncpointChannel unpaired : unpairedChannels) {
            matched = false;
            for (SyncpointChannel paired : pairedChannels) {
                if(paired.getName().equals(unpaired.getName())) {
                    matched = true;
                    mergeChannel(unpaired, paired);
                }
            }
            if(!matched) {
                unpaired.setState("new");
                unpaired.setOwnerId(session.getOwnerId());
                unpaired.update();
            }
        }
    }

    void mergeChannel(SyncpointChannel unpaired, SyncpointChannel paired) {

        SyncpointSubscription unpairedSub = unpaired.getSubscription();
        SyncpointSubscription pairedSub = paired.getSubscription();
        SyncpointInstallation unpairedInst = unpaired.getInstallation(applicationContext);

        if(pairedSub != null) {
            if(unpairedInst != null) {
                unpairedInst.setSubscriptionId(pairedSub.getId());
            }
            unpairedSub.delete();
        } else {
            unpairedSub.setChannelId(paired.getId());
            unpairedSub.setOwnerId(paired.getOwnerId());
            unpairedSub.update();
        }

        if(unpairedInst != null) {
            unpairedInst.setOwnerId(paired.getOwnerId());
            unpairedInst.setChannelId(paired.getId());
            unpairedInst.update();
        }
        unpaired.delete();
    }

    void observeControlDatabase() {
        ChangesCommand changesCommand = new ChangesCommand.Builder().continuous(true).build();
        ChangesFeedAsyncTask asyncChangesTask = new ChangesFeedAsyncTask(localControlDatabase, changesCommand) {

            @Override
            protected void handleDocumentChange(DocumentChange change) {
                Log.v(TAG, "I see control db change");
                controlDatabaseChanged();
            }
        };
        asyncChangesTask.execute();
        Log.v(TAG, "Started control changes listener");
    }

    void controlDatabaseChanged() {
        // if we are done with first ever sync
        if(session.isControlDbSynced()) {
            Log.v(TAG, "Control DB changed");
            // collect 1 second of changes before acting
            // todo can we make these calls all collapse into one?
            Looper l = Looper.getMainLooper();
            Handler h = new Handler(l);
            h.postDelayed(new Runnable() {

                @Override
                public void run() {
                    getUpToDateWithSubscriptions();
                }
            }, 1000);
        }
    }
}
