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

package ai.sapper.cdc.core.stores;

import ai.sapper.cdc.common.cache.MapThreadCache;
import ai.sapper.cdc.common.config.ConfigPath;
import ai.sapper.cdc.common.config.ConfigReader;
import ai.sapper.cdc.common.utils.DefaultLogger;
import ai.sapper.cdc.common.utils.JSONUtils;
import ai.sapper.cdc.common.utils.PathUtils;
import ai.sapper.cdc.common.utils.ReflectionUtils;
import ai.sapper.cdc.core.BaseEnv;
import ai.sapper.cdc.core.DistributedLock;
import ai.sapper.cdc.core.connections.Connection;
import ai.sapper.cdc.core.connections.ConnectionManager;
import ai.sapper.cdc.core.connections.ZookeeperConnection;
import ai.sapper.cdc.core.model.IEntity;
import ai.sapper.cdc.core.processing.ProcessorState;
import ai.sapper.cdc.core.stores.annotations.IShardProvider;
import ai.sapper.cdc.core.stores.annotations.SchemaSharded;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.curator.framework.CuratorFramework;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("rawtypes")
public class DataStoreManager {
    private final ProcessorState state = new ProcessorState();
    private final Map<Class<? extends IEntity<?>>, Map<Class<? extends AbstractDataStore<?>>, AbstractDataStoreSettings>> entityIndex = new HashMap<>();
    private final Map<String, AbstractDataStoreSettings> dataStoreConfigs = new HashMap<>();
    private final Map<Class<? extends IShardedEntity<?, ?>>, ShardConfigSettings> shardConfigs = new HashMap<>();
    private final MapThreadCache<String, AbstractDataStore<?>> openedStores = new MapThreadCache<>();
    private BaseEnv<?> env;
    private ConnectionManager connectionManager;
    private DataStoreManagerSettings settings;
    private DistributedLock updateLock;
    private ZookeeperConnection zkConnection;
    private String zkPath;

    public boolean isTypeSupported(@Nonnull Class<?> type) {
        if (ReflectionUtils.implementsInterface(IEntity.class, type)) {
            return entityIndex.containsKey(type);
        }
        return false;
    }

    public <T extends Connection> AbstractConnection<T> getConnection(@Nonnull String name,
                                                                      Class<? extends T> type) throws DataStoreException {
        try {
            state.check(ProcessorState.EProcessorState.Running);
            return connectionManager.getConnection(name, type);
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    public <T> AbstractConnection<T> getConnection(@Nonnull Class<? extends IEntity<?>> type) throws DataStoreException {
        return getConnection(type, false);
    }

    @SuppressWarnings({"unchecked"})
    public <T> AbstractConnection<T> getConnection(@Nonnull Class<? extends IEntity<?>> type,
                                                   boolean checkSuperTypes) throws DataStoreException {
        try {
            state.check(ProcessorState.EProcessorState.Running);
            Class<? extends IEntity<?>> ct = type;
            while (true) {
                if (entityIndex.containsKey(ct)) {
                    return (AbstractConnection<T>) entityIndex.get(ct);
                }
                if (checkSuperTypes) {
                    Class<?> t = ct.getSuperclass();
                    if (ReflectionUtils.implementsInterface(IEntity.class, t)) {
                        ct = (Class<? extends IEntity<?>>) t;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return null;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    public <T> AbstractDataStore<T> getDataStore(@Nonnull String name,
                                                 @Nonnull Class<? extends AbstractDataStore<T>> storeType) throws DataStoreException {
        return getDataStore(name, storeType, true);
    }

    public <T> AbstractDataStore<T> getDataStore(@Nonnull String name,
                                                 @Nonnull Class<? extends AbstractDataStore<T>> storeType,
                                                 boolean add) throws DataStoreException {
        try {
            AbstractDataStoreSettings config = dataStoreConfigs.get(name);
            if (config == null) {
                throw new DataStoreException(
                        String.format("No configuration found for data store type. [type=%s]", storeType.getCanonicalName()));
            }
            if (!config.getDataStoreClass().equals(storeType)) {
                throw new DataStoreException(
                        String.format("Invalid Data Store class. [store=%s][expected=%s][configured=%s]",
                                name, storeType.getCanonicalName(), config.getDataStoreClass().getCanonicalName()));
            }
            return getDataStore(config, storeType, add);
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    public <T, E extends IEntity> AbstractDataStore<T> getDataStore(@Nonnull Class<? extends AbstractDataStore<T>> storeType,
                                                                    Class<? extends E> type) throws DataStoreException {
        return getDataStore(storeType, type, true);
    }

    @SuppressWarnings({"rawtypes"})
    public <T, E extends IEntity> AbstractDataStore<T> getDataStore(@Nonnull Class<? extends AbstractDataStore<T>> storeType,
                                                                    Class<? extends E> type,
                                                                    boolean add) throws DataStoreException {
        Map<Class<? extends AbstractDataStore<?>>, AbstractDataStoreSettings> configs = entityIndex.get(type);
        if (configs == null) {
            throw new DataStoreException(String.format("No data store found for entity type. [type=%s]", type.getCanonicalName()));
        }
        AbstractDataStoreSettings config = configs.get(storeType);
        if (config == null) {
            throw new DataStoreException(String.format("No data store found. [type=%s][store type=%s]", type.getCanonicalName(), storeType.getCanonicalName()));
        }

        try {
            return getDataStore(config, storeType, add);
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> AbstractDataStore<T> getDataStore(AbstractDataStoreSettings config,
                                                  Class<? extends AbstractDataStore<T>> storeType,
                                                  boolean add) throws DataStoreException {
        Preconditions.checkNotNull(env);
        Map<String, AbstractDataStore<?>> stores = null;
        if (openedStores.containsThread()) {
            stores = openedStores.get();
            if (stores.containsKey(config.getName())) {
                return (AbstractDataStore<T>) stores.get(config.getName());
            }
        } else if (!add) {
            return null;
        }

        try {
            AbstractDataStore<T> store = ReflectionUtils.createInstance(storeType);
            store.configure(this, config, env);
            openedStores.put(store.name(), store);

            return store;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    @SuppressWarnings("rawtypes")
    public <T, E extends IShardedEntity> AbstractDataStore<T> getShard(@Nonnull Class<? extends AbstractDataStore<T>> storeType,
                                                                       @Nonnull Class<? extends E> type,
                                                                       Object shardKey) throws DataStoreException {
        try {
            if (type.isAnnotationPresent(SchemaSharded.class)) {
                ShardConfigSettings config = shardConfigs.get(type);
                if (config != null) {
                    IShardProvider provider = null;
                    if (config.getProvider() == null) {
                        SchemaSharded ss = type.getAnnotation(SchemaSharded.class);
                        Class<? extends IShardProvider> cls = ss.provider();
                        provider = ReflectionUtils.createInstance(cls);
                    } else {
                        Class<? extends IShardProvider> cls = config.getProvider();
                        provider = ReflectionUtils.createInstance(cls);
                    }
                    int shard = provider.withShardCount(config.getShards().size()).getShard(shardKey);
                    String name = config.getShards().get(shard);
                    if (Strings.isNullOrEmpty(name)) {
                        throw new DataStoreException(
                                String.format("Shard instance not found. [type=%s][index=%d]",
                                        type.getCanonicalName(), shard));
                    }
                    return getDataStore(name, storeType);
                } else {
                    throw new DataStoreException(
                            String.format("No Shard Config found. [type=%s]", type.getCanonicalName()));
                }
            }
            return null;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    public <T, E extends IShardedEntity> List<AbstractDataStore<T>> getShards(@Nonnull Class<? extends AbstractDataStore<T>> storeType,
                                                                              @Nonnull Class<? extends E> type) throws DataStoreException {
        try {
            if (type.isAnnotationPresent(SchemaSharded.class)) {
                List<AbstractDataStore<T>> stores = null;

                ShardConfigSettings config = shardConfigs.get(type);
                if (config != null) {
                    stores = new ArrayList<>();
                    for (int shard : config.getShards().keySet()) {
                        String name = config.getShards().get(shard);
                        if (Strings.isNullOrEmpty(name)) {
                            throw new DataStoreException(
                                    String.format("Shard instance not found. [type=%s][index=%d]",
                                            type.getCanonicalName(), shard));
                        }
                        stores.add(getDataStore(name, storeType));
                    }
                    return stores;
                } else {
                    throw new DataStoreException(String.format("No Shard Config found. [type=%s]", type.getCanonicalName()));
                }
            }
            return null;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    public void commit() throws DataStoreException {
        try {
            if (openedStores.containsThread()) {
                Map<String, AbstractDataStore<?>> stores = openedStores.get();
                for (String name : stores.keySet()) {
                    AbstractDataStore<?> store = stores.get(name);
                    if (store.auditLogger() != null) {
                        store.auditLogger().flush();
                    }
                }
                for (String name : stores.keySet()) {
                    AbstractDataStore<?> store = stores.get(name);
                    if (store instanceof TransactionDataStore) {
                        if (((TransactionDataStore) store).isInTransaction()) {
                            ((TransactionDataStore) store).commit();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            throw new DataStoreException(t);
        }
    }

    public void rollback() throws DataStoreException {
        try {
            if (openedStores.containsThread()) {
                Map<String, AbstractDataStore<?>> stores = openedStores.get();
                for (String name : stores.keySet()) {
                    AbstractDataStore<?> store = stores.get(name);
                    if (store.auditLogger() != null) {
                        store.auditLogger().discard();
                    }
                }
                for (String name : stores.keySet()) {
                    AbstractDataStore<?> store = stores.get(name);
                    if (store instanceof TransactionDataStore) {
                        if (((TransactionDataStore) store).isInTransaction()) {
                            ((TransactionDataStore) store).rollback();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            throw new DataStoreException(t);
        }
    }

    public void closeStores() throws DataStoreException {
        try {
            if (openedStores.containsThread()) {
                Map<String, AbstractDataStore<?>> stores = openedStores.get();
                List<AbstractDataStore> storeList = new ArrayList<>();
                for (String name : stores.keySet()) {
                    AbstractDataStore<?> store = stores.get(name);
                    if (store.auditLogger() != null) store.auditLogger().discard();
                    if (store instanceof TransactionDataStore) {
                        if (((TransactionDataStore) store).isInTransaction()) {
                            DefaultLogger.error(
                                    String.format("Store has pending transactions, rolling back. [name=%s][thread id=%d]",
                                            store.name(), Thread.currentThread().getId()));
                            ((TransactionDataStore) store).rollback();
                        }
                    }
                    storeList.add(store);
                }
                for (AbstractDataStore store : storeList) {
                    try {
                        store.close();
                    } catch (IOException e) {
                        DefaultLogger.error(e.getLocalizedMessage());
                    }
                }
                openedStores.clear();
                storeList.clear();
            }
        } catch (Throwable t) {
            throw new DataStoreException(t);
        }
    }

    public void close(@Nonnull AbstractDataStore dataStore) throws DataStoreException {
        try {
            if (openedStores.containsThread()) {
                Map<String, AbstractDataStore<?>> stores = openedStores.get();
                if (stores.containsKey(dataStore.name())) {
                    if (dataStore.auditLogger() != null) {
                        dataStore.auditLogger().discard();
                    }
                    if (dataStore instanceof TransactionDataStore) {
                        TransactionDataStore ts = (TransactionDataStore) dataStore;
                        if (ts.isInTransaction()) {
                            DefaultLogger.error(
                                    String.format("Data Store has un-committed transaction. [name=%s][thread=%d]",
                                            dataStore.name(), Thread.currentThread().getId()));
                            ts.rollback();
                        }
                    }
                    openedStores.remove(dataStore.name());
                }
            }
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    public void init(@NonNull HierarchicalConfiguration<ImmutableNode> xmlConfig,
                     @Nonnull BaseEnv<?> env,
                     String path) throws ConfigurationException {
        this.env = env;
        this.connectionManager = env.connectionManager();
        try {
            updateLock = env.createLock(getClass().getSimpleName());
            if (!ConfigReader.checkIfNodeExists(xmlConfig, DataStoreManagerSettings.CONFIG_NODE_DATA_STORES)) {
                state.setState(ProcessorState.EProcessorState.Running);
                return;
            }
            if (Strings.isNullOrEmpty(path)) {
                ConfigPath cp = AbstractDataStoreSettings.class.getAnnotation(ConfigPath.class);
                path = cp.path();
            }
            HierarchicalConfiguration<ImmutableNode> config
                    = xmlConfig.configurationAt(DataStoreManagerSettings.CONFIG_NODE_DATA_STORES);
            ConfigReader reader = new ConfigReader(xmlConfig, path, DataStoreManagerSettings.class);
            settings = (DataStoreManagerSettings) reader.settings();
            List<HierarchicalConfiguration<ImmutableNode>> dsnodes = config.configurationsAt(path);
            for (HierarchicalConfiguration<ImmutableNode> node : dsnodes) {
                readDataStoreConfig(node);
            }

            if (!ConfigReader.checkIfNodeExists(config, DataStoreManagerSettings.CONFIG_NODE_SHARDED_ENTITIES))
                return;
            List<HierarchicalConfiguration<ImmutableNode>> snodes
                    = config.configurationsAt(DataStoreManagerSettings.CONFIG_NODE_SHARDED_ENTITIES);
            for (HierarchicalConfiguration<ImmutableNode> node : snodes) {
                readShardConfig(node);
            }
            if (!Strings.isNullOrEmpty(settings.getZkConnection())) {
                zkConnection = connectionManager
                        .getConnection(settings.getZkConnection(), ZookeeperConnection.class);
                if (zkConnection == null) {
                    throw new Exception(String.format("ZooKeeper connection not found. [name=%s]",
                            settings.getZkConnection()));
                }
                if (!zkConnection.isConnected()) zkConnection.connect();
                zkPath = path;
                CuratorFramework client = zkConnection.client();
                String dspath = new PathUtils.ZkPathBuilder(path)
                        .withPath(DataStoreManagerSettings.CONFIG_NODE_DATA_STORES)
                        .build();
                if (client.checkExists().forPath(dspath) != null) {
                    List<String> types = client.getChildren().forPath(dspath);
                    if (types != null && !types.isEmpty()) {
                        for (String type : types) {
                            String tp = new PathUtils.ZkPathBuilder(dspath)
                                    .withPath(type)
                                    .build();
                            List<String> names = client.getChildren().forPath(tp);
                            if (names != null && !names.isEmpty()) {
                                for (String name : names) {
                                    if (dataStoreConfigs.containsKey(name) && settings.isOverride()) {
                                        continue;
                                    }
                                    String cp = new PathUtils.ZkPathBuilder(tp)
                                            .withPath(name)
                                            .build();
                                    readDataStoreConfig(client, cp, name);
                                }
                            }
                        }
                    }
                }
                String shpath = new PathUtils.ZkPathBuilder(path)
                        .withPath(DataStoreManagerSettings.CONFIG_NODE_SHARDED_ENTITIES)
                        .build();
                if (client.checkExists().forPath(shpath) != null) {
                    List<String> names = client.getChildren().forPath(shpath);
                    if (names != null && !names.isEmpty()) {
                        for (String name : names) {
                            String cp = new PathUtils.ZkPathBuilder(shpath)
                                    .withPath(name)
                                    .build();
                            byte[] data = client.getData().forPath(cp);
                            if (data == null || data.length == 0) {
                                throw new Exception(String.format("Shard Configuration not found. [path=%s]", cp));
                            }
                            ShardConfigSettings sc = JSONUtils.read(data, ShardConfigSettings.class);
                            if (shardConfigs.containsKey(sc.getEntityType()) && settings.isOverride()) {
                                continue;
                            }
                            sc.setSource(EConfigSource.Database);
                            shardConfigs.put(sc.getEntityType(), sc);
                        }
                    }
                }
            }
            state.setState(ProcessorState.EProcessorState.Running);
            if (settings.isAutoSave()) {
                save();
            }
        } catch (Exception ex) {
            state.error(ex);
            throw new ConfigurationException(ex);
        }
    }

    public void save() throws DataStoreException {
        if (zkConnection == null) {
            return;
        }
        try {
            state.check(ProcessorState.EProcessorState.Running);
            updateLock.lock();
            try {
                if (!dataStoreConfigs.isEmpty()) {
                    for (String name : dataStoreConfigs.keySet()) {
                        AbstractDataStoreSettings config = dataStoreConfigs.get(name);

                    }
                }
            } finally {
                updateLock.unlock();
            }
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }

    public void addShardConfig(@Nonnull ShardConfigSettings config) throws DataStoreException {
        shardConfigs.put(config.getEntityType(), config);
    }

    private void readDataStoreConfig(CuratorFramework client,
                                     String path,
                                     String name) throws Exception {
        byte[] data = client.getData().forPath(path);
        if (data == null || data.length == 0) {
            throw new Exception(String.format("DataStore configuration not found. [path=%s]", path));
        }
        AbstractDataStoreSettings settings = JSONUtils.read(data, AbstractDataStoreSettings.class);
        settings.setSource(EConfigSource.Database);
        addDataStoreConfig(settings);
    }

    private void readShardConfig(HierarchicalConfiguration<ImmutableNode> xmlConfig) throws ConfigurationException {
        try {
            ConfigReader reader = new ConfigReader(xmlConfig, ShardConfigSettings.class);
            ShardConfigSettings settings = ((ShardConfigSettings) reader.settings()).parse();
            settings.setSource(EConfigSource.File);
            shardConfigs.put(settings.getEntityType(), settings);
        } catch (Exception ex) {
            throw new ConfigurationException(ex);
        }
    }


    @SuppressWarnings("unchecked")
    public void addDataStoreConfig(AbstractDataStoreSettings config) throws ConfigurationException {
        try {
            dataStoreConfigs.put(
                    config.getName(), config);
            AbstractConnection<?> connection = connectionManager
                    .getConnection(config.getConnectionName(), config.getConnectionType());
            if (connection == null) {
                throw new ConfigurationException(
                        String.format("No connection found. [store=%s][connection=%s]",
                                config.getName(), config.getConnectionName()));
            }
            if (connection.getSupportedTypes() != null) {
                for (Class<?> t : connection.getSupportedTypes()) {
                    if (ReflectionUtils.implementsInterface(IEntity.class, t)) {
                        Map<Class<? extends AbstractDataStore<?>>, AbstractDataStoreSettings> ec = entityIndex.get(t);
                        if (ec == null) {
                            ec = new HashMap<>();
                            entityIndex.put((Class<? extends IEntity<?>>) t, ec);
                        }
                        ec.put(config.getDataStoreClass(), config);
                    }
                }
            }
        } catch (Exception ex) {
            throw new ConfigurationException(ex);
        }
    }

    private void readDataStoreConfig(HierarchicalConfiguration<ImmutableNode> xmlConfig) throws ConfigurationException {
        ConfigReader reader = new ConfigReader(xmlConfig, null, AbstractDataStoreSettings.class);
        AbstractDataStoreSettings settings = (AbstractDataStoreSettings) reader.settings();
        settings.setSource(EConfigSource.File);
        addDataStoreConfig(settings);
    }
}