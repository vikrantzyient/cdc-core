/*
 * Copyright(C) (2024) Zyient Inc. (open.source at zyient dot io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zyient.core.content.impl.mongo;

import dev.morphia.transactions.MorphiaSession;
import io.zyient.base.common.StateException;
import io.zyient.base.common.model.entity.IKey;
import io.zyient.base.core.processing.ProcessorState;
import io.zyient.core.content.DocumentContext;
import io.zyient.core.content.ManagedContentProvider;
import io.zyient.core.content.settings.ManagedProviderSettings;
import io.zyient.core.persistence.AbstractDataStore;
import io.zyient.core.persistence.Cursor;
import io.zyient.core.persistence.DataStoreException;
import io.zyient.core.persistence.impl.mongo.MongoDbCursor;
import io.zyient.core.persistence.impl.mongo.MongoDbDataStore;
import io.zyient.core.persistence.model.Document;
import io.zyient.core.persistence.model.DocumentId;
import io.zyient.core.persistence.model.DocumentState;
import lombok.NonNull;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;

import java.util.List;

public class MongoContentProvider extends ManagedContentProvider<MorphiaSession> {
    public MongoContentProvider() {
        super(ManagedProviderSettings.class);
    }

    @Override
    public void endSession() throws DataStoreException {
        try {
            checkState(ProcessorState.EProcessorState.Running);
            MongoDbDataStore dataStore = (MongoDbDataStore) dataStore();
            dataStore.endSession();
        } catch (StateException se) {
            throw new DataStoreException(se);
        }
    }

    @Override
    protected void doConfigure(@NonNull HierarchicalConfiguration<ImmutableNode> xmlConfig) throws ConfigurationException {
        super.doConfigure(xmlConfig);
        if (!(dataStore() instanceof MongoDbDataStore)) {
            throw new ConfigurationException(String.format("Invalid Data Store type. [type=%s]",
                    dataStore().getClass().getCanonicalName()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E extends DocumentState<?>, K extends IKey, D extends Document<E, K, D>> Document<E, K, D> findDoc(@NonNull String uri,
                                                                                                         @NonNull String collection,
                                                                                                         @NonNull Class<? extends Document<E, K, D>> entityType,
                                                                                                         DocumentContext context) throws DataStoreException {
        try {
            String condition = String.format("URI = %s", uri);
            AbstractDataStore.Q query = new AbstractDataStore.Q()
                    .where(condition);
            MongoDbDataStore dataStore = (MongoDbDataStore) dataStore();
            try (MongoDbCursor<DocumentId, Document<E, K, D>> cursor = (MongoDbCursor<DocumentId, Document<E, K, D>>) dataStore
                    .search(query,
                            8,
                            DocumentId.class,
                            entityType,
                            context)) {
                List<Document<E, K, D>> documents = cursor.nextPage();
                if (documents != null && !documents.isEmpty()) {
                    if (documents.size() > 1) {
                        throw new DataStoreException(String.format("Multiple documents found for path. [uri=%s]", uri));
                    }
                    return documents.get(0);
                }
            }
            return null;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E extends DocumentState<?>, K extends IKey, D extends Document<E, K, D>> Cursor<DocumentId, Document<E, K, D>> searchDocs(AbstractDataStore.@NonNull Q query,
                                                                                                                                         @NonNull Class<? extends Document<E, K, D>> entityType,
                                                                                                                                         boolean download,
                                                                                                                                         int currentPage,
                                                                                                                                         int batchSize,
                                                                                                                                         DocumentContext context) throws DataStoreException {
        MongoDbDataStore dataStore = (MongoDbDataStore) dataStore();
        MongoDbCursor<DocumentId, Document<E, K, D>> cursor = (MongoDbCursor<DocumentId, Document<E, K, D>>) dataStore
                .doSearch(query,
                        currentPage,
                        batchSize,
                        DocumentId.class,
                        entityType,
                        context);
        return new MongoContentCursor<>(cursor, fileSystem());
    }
}
