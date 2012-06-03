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

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.ektorp.CouchDbConnector;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.ViewResult.Row;

public class SyncpointModelFactory {

    public static <T> List<T> getModelsOfType(CouchDbConnector database, String type, Class<T> clazz) {
        List<T> results = new ArrayList<T>();
        ViewQuery query = new ViewQuery().allDocs().includeDocs(true);
        ViewResult result = database.queryView(query);
        for(Row row : result.getRows()) {
            JsonNode docNode = row.getDocAsNode();
            JsonNode docTypeNode = docNode.get("type");
            if(docTypeNode != null) {
                String docType = docTypeNode.asText();
                if(docType != null && docType.equals(type)) {
                    results.add(getModelForDocument(database, row.getId(), clazz));
                }
            }
        }

        return results;
    }

    public static <T> T getModelForDocument(CouchDbConnector database, String docId, Class<T> clazz) {
        if(clazz == null) {
            throw new IllegalStateException(String.format("Class is required for model instantiation"));
        }
        return database.get(clazz, docId);
    }

}
