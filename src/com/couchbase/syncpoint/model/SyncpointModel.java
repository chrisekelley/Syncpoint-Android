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

import org.codehaus.jackson.annotate.JsonIgnore;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.support.OpenCouchDbDocument;

@SuppressWarnings("serial")
public class SyncpointModel extends OpenCouchDbDocument {

    @JsonIgnore
    protected CouchDbInstance server;

    @JsonIgnore
    protected CouchDbConnector database;

    private String state;
    private String error;
    private String type;

    public SyncpointModel(CouchDbInstance server, CouchDbConnector database) {
        this.server = server;
        this.database = database;
    }

    public SyncpointModel() {
        //leave detached
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonIgnore
    public boolean isActive() {
        return "active".equals(state);
    }

    @JsonIgnore
    public boolean isPaired() {
        return "paired".equals(state);
    }

    @JsonIgnore
    public boolean isReady() {
        return "ready".equals(state);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getName(), getId());
    }

    public void create() {
        if(database != null) {
            database.create(this);
        } else {
            throw new IllegalStateException("Cannot create a detached model");
        }
    }

    public void update() {
        if(database != null) {
            database.update(this);
        } else {
            throw new IllegalStateException("Cannot update a detached model");
        }
    }

    public void delete() {
        if(database != null) {
            database.delete(this);
        } else {
            throw new IllegalStateException("Cannot delete a detached model");
        }
    }

    public void attach(CouchDbInstance server, CouchDbConnector database) {
        this.server = server;
        this.database = database;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof SyncpointModel) {
            SyncpointModel other = (SyncpointModel)o;
            if(getId() != null && other.getId() != null && getRevision() != null && other.getRevision() != null) {
                return getId().equals(other.getId()) && getRevision().equals(other.getRevision());
            }
        }
        return false;
    }

}
