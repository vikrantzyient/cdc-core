/*
 * Copyright(C) (2023) Sapper Inc. (open.source at zyient dot io)
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

package io.zyient.base.core.content;

import com.google.common.base.Preconditions;
import io.zyient.base.common.model.Context;
import io.zyient.base.common.model.entity.IKey;
import io.zyient.base.common.utils.DefaultLogger;
import io.zyient.base.common.utils.JSONUtils;
import io.zyient.base.core.content.settings.ManagedProviderSettings;
import io.zyient.base.core.io.FileSystem;
import io.zyient.base.core.io.Reader;
import io.zyient.base.core.io.Writer;
import io.zyient.base.core.io.model.FileInode;
import io.zyient.base.core.io.model.PathInfo;
import io.zyient.base.core.stores.*;
import io.zyient.base.core.stores.impl.solr.SolrDataStore;
import io.zyient.base.core.stores.model.Document;
import io.zyient.base.core.stores.model.DocumentId;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Accessors(fluent = true)
public abstract class ManagedContentProvider<T> extends ContentProvider {
    private AbstractDataStore<T> dataStore;
    private FileSystem fileSystem;

    protected ManagedContentProvider(@NonNull Class<? extends ContentProviderSettings> settingsType) {
        super(settingsType);
    }


    @Override
    @SuppressWarnings("unchecked")
    protected void doConfigure(@NonNull HierarchicalConfiguration<ImmutableNode> xmlConfig) throws ConfigurationException {
        try {
            ManagedProviderSettings settings = (ManagedProviderSettings) settings();
            if (!(env() instanceof DataStoreProvider)) {
                throw new ConfigurationException(
                        String.format("Environment does not initialize DataStore manager. [type=%s]",
                                env().getClass().getCanonicalName()));
            }
            DataStoreManager dataStoreManager = ((DataStoreProvider) env()).getDataStoreManager();
            Preconditions.checkNotNull(dataStoreManager);
            dataStore = (AbstractDataStore<T>) dataStoreManager.getDataStore(settings.getDataStore(), settings.getDataStoreType());
            if (dataStore == null) {
                throw new ConfigurationException(String.format("DataStore not found. [name=%s][type=%s]",
                        settings.getDataStore(), SolrDataStore.class.getCanonicalName()));
            }
            fileSystem = env().fileSystemManager().get(settings.getFileSystem());
            if (fileSystem == null) {
                throw new ConfigurationException(String.format("FileSystem not found. [name=%s]",
                        settings.getFileSystem()));
            }
            DefaultLogger.info(String.format("Using FileSystem [%s]: [type=%s]",
                    fileSystem.settings().getName(), fileSystem.getClass().getCanonicalName()));
        } catch (Exception ex) {
            throw new ConfigurationException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E extends Enum<?>, K extends IKey, D extends Document<E, K, D>> Document<E, K, D> createDoc(@NonNull Document<E, K, D> document,
                                                                                                           @NonNull Context context) throws DataStoreException {
        try {
            FileInode fi = fileSystem.create(document.getId().getCollection(), document.getId().getId());
            try (Writer writer = fileSystem.writer(fi, document.getPath())) {
                writer.commit(true);
            }
            String uri = JSONUtils.asString(fi.getPath(), Map.class);
            document.setUri(uri);
            if (dataStore instanceof TransactionDataStore<?, ?>) {
                ((TransactionDataStore<?, ?>) dataStore).beingTransaction();
            }
            try {
                document = dataStore.create(document, document.getClass(), context);
                if (dataStore instanceof TransactionDataStore<?, ?>) {
                    ((TransactionDataStore<?, ?>) dataStore).commit();
                }
                return document;
            } catch (RuntimeException re) {
                if (dataStore instanceof TransactionDataStore<?, ?>) {
                    ((TransactionDataStore<?, ?>) dataStore).rollback(false);
                }
                throw re;
            } catch (Throwable t) {
                if (dataStore instanceof TransactionDataStore<?, ?>) {
                    ((TransactionDataStore<?, ?>) dataStore).rollback(false);
                }
                throw t;
            }
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E extends Enum<?>, K extends IKey, D extends Document<E, K, D>> Document<E, K, D> updateDoc(@NonNull Document<E, K, D> document,
                                                                                                           @NonNull Context context) throws DataStoreException {
        try {
            Map<String, String> map = JSONUtils.read(document.getUri(), Map.class);
            PathInfo pi = fileSystem.parsePathInfo(map);
            FileInode fi = (FileInode) fileSystem.getInode(pi);
            if (fi == null) {
                throw new DataStoreException(String.format("Document not found. [uri=%s]", document.getUri()));
            }
            try (Writer writer = fileSystem.writer(fi, document.getPath())) {
                writer.commit(true);
            }
            if (dataStore instanceof TransactionDataStore<?, ?>) {
                ((TransactionDataStore<?, ?>) dataStore).beingTransaction();
            }
            try {
                document = dataStore.update(document, document.getClass(), context);
                if (dataStore instanceof TransactionDataStore<?, ?>) {
                    ((TransactionDataStore<?, ?>) dataStore).commit();
                }
                return document;
            } catch (RuntimeException re) {
                if (dataStore instanceof TransactionDataStore<?, ?>) {
                    ((TransactionDataStore<?, ?>) dataStore).rollback(false);
                }
                throw re;
            } catch (Throwable t) {
                if (dataStore instanceof TransactionDataStore<?, ?>) {
                    ((TransactionDataStore<?, ?>) dataStore).rollback(false);
                }
                throw t;
            }
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E extends Enum<?>, K extends IKey, D extends Document<E, K, D>> boolean deleteDoc(@NonNull DocumentId id,
                                                                                                 @NonNull Class<? extends Document<E, K, D>> entityType,
                                                                                                 @NonNull Context context) throws DataStoreException {
        try {
            Document<?, ?, ?> doc = findDoc(id, entityType, context);
            if (doc != null) {
                Map<String, String> map = JSONUtils.read(doc.getUri(), Map.class);
                PathInfo pi = fileSystem.parsePathInfo(map);
                FileInode fi = (FileInode) fileSystem.getInode(pi);
                if (fi == null) {
                    DefaultLogger.warn(String.format("Document not found. [uri=%s]", doc.getUri()));
                } else {
                    if (!fileSystem.delete(fi.getPathInfo())) {
                        DefaultLogger.warn(String.format("Document delete returned false. [uri=%s]", doc.getUri()));
                    }
                }
                if (dataStore instanceof TransactionDataStore<?, ?>) {
                    ((TransactionDataStore<?, ?>) dataStore).beingTransaction();
                }
                try {
                    boolean r = dataStore.delete(id, Document.class, context);
                    if (dataStore instanceof TransactionDataStore<?, ?>) {
                        ((TransactionDataStore<?, ?>) dataStore).commit();
                    }
                    return r;
                } catch (RuntimeException re) {
                    if (dataStore instanceof TransactionDataStore<?, ?>) {
                        ((TransactionDataStore<?, ?>) dataStore).rollback(false);
                    }
                    throw re;
                } catch (Throwable t) {
                    if (dataStore instanceof TransactionDataStore<?, ?>) {
                        ((TransactionDataStore<?, ?>) dataStore).rollback(false);
                    }
                    throw t;
                }
            }
            return false;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @Override
    protected <E extends Enum<?>, K extends IKey, D extends Document<E, K, D>> Map<DocumentId, Boolean> deleteDocs(@NonNull List<DocumentId> ids,
                                                                                                                   @NonNull Class<? extends Document<E, K, D>> entityType,
                                                                                                                   @NonNull Context context) throws DataStoreException {
        try {
            Map<DocumentId, Boolean> response = new HashMap<>();
            for (DocumentId id : ids) {
                boolean r = deleteDoc(id, entityType, context);
                response.put(id, r);
            }
            return response;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E extends Enum<?>, K extends IKey, D extends Document<E, K, D>> Document<E, K, D> findDoc(@NonNull DocumentId docId,
                                                                                                         @NonNull Class<? extends Document<E, K, D>> entityType,
                                                                                                         Context context) throws DataStoreException {
        try {
            Document<E, K, D> doc = dataStore.find(docId, entityType, context);
            if (doc != null) {
                Map<String, String> map = JSONUtils.read(doc.getUri(), Map.class);
                PathInfo pi = fileSystem.parsePathInfo(map);
                FileInode fi = (FileInode) fileSystem.getInode(pi);
                if (fi == null) {
                    throw new DataStoreException(String.format("Document not found. [uri=%s]", doc.getUri()));
                }
                try (Reader reader = fileSystem.reader(pi)) {
                    File path = reader.copy();
                    doc.setPath(path);
                }
                return doc;
            }
            return null;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @Override
    protected void doClose() throws IOException {

    }
}
