/*
 *  Copyright (2020) Subhabrata Ghosh (subho dot ghosh at outlook dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ai.sapper.cdc.core.stores.impl;

import ai.sapper.cdc.common.model.Context;
import ai.sapper.cdc.common.model.entity.EEntityState;
import ai.sapper.cdc.common.model.entity.IEntity;
import ai.sapper.cdc.core.model.BaseEntity;
import ai.sapper.cdc.core.stores.BaseSearchResult;
import ai.sapper.cdc.core.stores.DataStoreException;
import ai.sapper.cdc.core.stores.IDGenerator;
import ai.sapper.cdc.core.stores.TransactionDataStore;
import ai.sapper.cdc.core.stores.impl.settings.RdbmsStoreSettings;
import com.google.common.base.Preconditions;
import lombok.NonNull;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RdbmsDataStore extends TransactionDataStore<Session, Transaction> {
    public void flush() throws DataStoreException {
        checkState();
        Session session = sessionManager().session();
        if (session != null) {
            session.flush();
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <E extends IEntity<?>> E createEntity(@NonNull E entity,
                                                 @NonNull Class<? extends E> type,
                                                 Context context) throws
            DataStoreException {
        checkState();
        Preconditions.checkState(isInTransaction());
        RdbmsSessionManager sessionManager = (RdbmsSessionManager) sessionManager();
        Session session = sessionManager.session();
        if (entity instanceof BaseEntity) {
            if (((BaseEntity) entity).getState().getState() != EEntityState.New) {
                entity = (E) sessionManager.checkCache((BaseEntity<?>) entity);
            }
        }
        IDGenerator.process(entity, this);
        Object result = session.save(entity);
        if (result == null) {
            throw new DataStoreException(String.format("Error saving entity. [type=%s][key=%s]", type.getCanonicalName(), entity.getKey()));
        }
        if (entity instanceof BaseEntity) {
            entity = (E) sessionManager.updateCache((BaseEntity<?>) entity, EEntityState.Synced);
        }
        return entity;
    }

    @Override
    public void configure() throws ConfigurationException {
        Preconditions.checkState(settings instanceof RdbmsStoreSettings);
        try {
            HibernateConnection hibernateConnection = (HibernateConnection) connection();
            sessionManager(new RdbmsSessionManager(hibernateConnection,
                    ((RdbmsStoreSettings) settings).getSessionTimeout()));
        } catch (Exception ex) {
            throw new ConfigurationException(ex);
        }
    }


    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <E extends IEntity<?>> E updateEntity(@NonNull E entity,
                                                 @NonNull Class<? extends E> type,
                                                 Context context) throws
            DataStoreException {
        checkState();
        Preconditions.checkState(isInTransaction());
        RdbmsSessionManager sessionManager = (RdbmsSessionManager) sessionManager();
        Session session = sessionManager.session();
        if (entity instanceof BaseEntity) {
            if (((BaseEntity) entity).getState().getState() != EEntityState.New) {
                entity = (E) sessionManager.checkCache((BaseEntity<?>) entity);
            }
        }
        Object result = session.save(entity);
        if (result == null) {
            throw new DataStoreException(String.format("Error updating entity. [type=%s][key=%s]", type.getCanonicalName(), entity.getKey()));
        }
        if (entity instanceof BaseEntity) {
            entity = (E) sessionManager.updateCache((BaseEntity<?>) entity, EEntityState.Synced);
        }
        return entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends IEntity<?>> boolean deleteEntity(@NonNull Object key,
                                                       @NonNull Class<? extends E> type,
                                                       Context context) throws
            DataStoreException {
        checkState();
        Preconditions.checkState(isInTransaction());
        RdbmsSessionManager sessionManager = (RdbmsSessionManager) sessionManager();
        Session session = sessionManager.session();
        E entity = findEntity(key, type, context);
        if (entity != null) {
            session.delete(entity);
            if (entity instanceof BaseEntity) {
                entity = (E) sessionManager.updateCache((BaseEntity<?>) entity, EEntityState.Deleted);
            }
            return true;
        }
        return false;
    }

    @Override
    public <E extends IEntity<?>> E findEntity(@NonNull Object key,
                                               @NonNull Class<? extends E> type,
                                               Context context) throws
            DataStoreException {
        checkState();
        Preconditions.checkState(isInTransaction());
        RdbmsSessionManager sessionManager = (RdbmsSessionManager) sessionManager();
        Session session = sessionManager.session();

        E entity = session.find(type, key);
        if (entity instanceof BaseEntity) {
            ((BaseEntity<?>) entity).getState().setState(EEntityState.Synced);
        }
        return entity;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends IEntity<?>> BaseSearchResult<E> doSearch(@NonNull String query,
                                                               int offset,
                                                               int maxResults,
                                                               @NonNull Class<? extends E> type,
                                                               Context context)
            throws DataStoreException {
        checkState();
        Preconditions.checkState(isInTransaction());
        RdbmsSessionManager sessionManager = (RdbmsSessionManager) sessionManager();
        Session session = sessionManager.session();
        Query qq = session.createQuery(query, type).setMaxResults(maxResults).setFirstResult(offset);
        List<E> result = qq.getResultList();
        if (result != null && !result.isEmpty()) {
            for (E entity : result) {
                if (entity instanceof BaseEntity) {
                    ((BaseEntity<?>) entity).getState().setState(EEntityState.Synced);
                }
            }
            EntitySearchResult<E> er = new EntitySearchResult<>(type);
            er.setQuery(query);
            er.setOffset(offset);
            er.setCount(result.size());
            er.setEntities(result);
            return er;
        }
        return null;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends IEntity<?>> BaseSearchResult<E> doSearch(@NonNull String query,
                                                               int offset, int maxResults,
                                                               Map<String, Object> parameters,
                                                               @NonNull Class<? extends E> type,
                                                               Context context)
            throws DataStoreException {
        checkState();
        Preconditions.checkState(isInTransaction());
        RdbmsSessionManager sessionManager = (RdbmsSessionManager) sessionManager();
        Session session = sessionManager.session();

        Query qq = session.createQuery(query, type).setMaxResults(maxResults).setFirstResult(offset);
        if (parameters != null && !parameters.isEmpty()) {
            for (String key : parameters.keySet())
                qq.setParameter(key, parameters.get(key));
        }
        List<E> result = qq.getResultList();
        if (result != null && !result.isEmpty()) {
            for (E entity : result) {
                if (entity instanceof BaseEntity) {
                    ((BaseEntity) entity).getState().setState(EEntityState.Synced);
                }
            }
            EntitySearchResult<E> er = new EntitySearchResult<>(type);
            er.setQuery(query);
            er.setOffset(offset);
            er.setCount(result.size());
            er.setEntities((Collection<E>) result);
            return er;
        }
        return null;
    }

    @Override
    public DataStoreAuditContext context() {
        DataStoreAuditContext ctx = new DataStoreAuditContext();
        ctx.setType(getClass().getCanonicalName());
        ctx.setName(name());
        ctx.setConnectionType(connection().getClass().getCanonicalName());
        ctx.setConnectionName(connection().name());
        return ctx;
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }
}
