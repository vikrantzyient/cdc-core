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

package ai.sapper.cdc.core.stores.impl;

import ai.sapper.cdc.common.cache.ThreadCache;
import ai.sapper.cdc.core.BaseEnv;
import ai.sapper.cdc.core.connections.Connection;
import ai.sapper.cdc.core.connections.ConnectionError;
import ai.sapper.cdc.core.connections.ConnectionManager;
import ai.sapper.cdc.core.connections.db.DbConnection;
import ai.sapper.cdc.core.connections.settings.ConnectionSettings;
import ai.sapper.cdc.core.connections.settings.EConnectionType;
import ai.sapper.cdc.core.keystore.KeyStore;
import ai.sapper.cdc.core.stores.AbstractConnection;
import ai.sapper.cdc.core.stores.impl.settings.MongoDSConnectionSettings;
import com.google.common.base.Preconditions;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.io.IOException;

@Getter
@Accessors(fluent = true)
public class MongoDSConnection extends AbstractConnection<MongoClient> {
    private MongoDSConnectionSettings settings;
    protected ConnectionManager connectionManager;
    @Getter(AccessLevel.NONE)
    private final ThreadCache<MongoClient> threadCache = new ThreadCache<>();
    private BaseEnv<?> env;

    public MongoDSConnection() {
        super(EConnectionType.db, MongoDSConnectionSettings.class);
    }

    @Override
    public Connection setup(@NonNull ConnectionSettings settings, @NonNull BaseEnv<?> env) throws ConnectionError {
        synchronized (state()) {
            try {
                if (state().isConnected()) {
                    close();
                }
                state().clear();
                this.settings = (MongoDSConnectionSettings) settings;
                this.connectionManager = env.connectionManager();
                state().setState(EConnectionState.Initialized);
                return this;
            } catch (Exception ex) {
                state().error(ex);
                throw new ConnectionError(ex);
            }
        }
    }

    @Override
    public Connection connect() throws ConnectionError {
        if (!state().isConnected()) {
            state().check(EConnectionState.Initialized);
            state().setState(EConnectionState.Connected);
        }
        return this;
    }

    public MongoClient getConnection() throws ConnectionError {
        state().check(EConnectionState.Connected);
        try {
            synchronized (threadCache) {
                if (threadCache.contains()) {
                    MongoClient session = threadCache.get();
                }
                MongoClient session = createConnection();
                return threadCache.put(session);
            }
        } catch (Throwable t) {
            throw new ConnectionError(t);
        }
    }

    private MongoClient createConnection() throws Exception {
        KeyStore keyStore = connectionManager().keyStore();
        Preconditions.checkNotNull(keyStore);
        String url = createConnectionUrl(keyStore);
        return MongoClients.create(url);
    }

    protected String createConnectionUrl(KeyStore keyStore) throws Exception {
        String url = "mongodb://";
        StringBuilder builder = new StringBuilder(url);
        builder.append(settings.getUser());
        String pk = settings.getPassword();
        builder.append(":")
                .append(keyStore.read(pk));
        builder.append("@")
                .append(settings.getHost())
                .append(":")
                .append(settings.getPort());
        builder.append("/")
                .append(settings.getDb());
        builder.append("/?")
                .append(DbConnection.Constants.DB_KEY_POOL_SIZE)
                .append(settings.getPoolSize());
        if (settings.getParameters() != null && !settings.getParameters().isEmpty()) {
            for (String param : settings.getParameters().keySet()) {
                builder.append("&")
                        .append(param)
                        .append("=")
                        .append(settings.getParameters().get(param));
            }
        }
        return builder.toString();
    }

    @Override
    public boolean hasTransactionSupport() {
        return false;
    }

    @Override
    public void close(@NonNull MongoClient connection) throws ConnectionError {
        try {
            connection.close();
            MongoClient client = threadCache.remove();
            if (client == null) {
                throw new ConnectionError("Connection not created via connection manager...");
            }
            if (!client.equals(connection)) {
                throw new ConnectionError("Connection handle passed doesn't match cached connection.");
            }
        } catch (Exception ex) {
            throw new ConnectionError(ex);
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (state()) {
            if (state().isConnected())
                state().setState(EConnectionState.Closed);
            threadCache.close();
        }
    }
}