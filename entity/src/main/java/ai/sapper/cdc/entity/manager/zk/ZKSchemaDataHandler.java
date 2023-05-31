package ai.sapper.cdc.entity.manager.zk;

import ai.sapper.cdc.common.model.InvalidDataError;
import ai.sapper.cdc.common.utils.JSONUtils;
import ai.sapper.cdc.common.utils.PathUtils;
import ai.sapper.cdc.core.connections.ZookeeperConnection;
import ai.sapper.cdc.entity.manager.SchemaDataHandler;
import ai.sapper.cdc.entity.manager.SchemaDataHandlerSettings;
import ai.sapper.cdc.entity.manager.zk.model.ZkEntitySchema;
import ai.sapper.cdc.entity.manager.zk.model.ZkSchemaEntity;
import ai.sapper.cdc.entity.schema.Domain;
import ai.sapper.cdc.entity.schema.EntitySchema;
import ai.sapper.cdc.entity.schema.SchemaEntity;
import ai.sapper.cdc.entity.schema.SchemaVersion;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.curator.framework.CuratorFramework;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
@Accessors(fluent = true)
public class ZKSchemaDataHandler extends SchemaDataHandler {
    public static final String ZK_PATH_DOMAINS = "domains";

    private String zkBasePath;
    private ZookeeperConnection zkConnection;

    protected ZKSchemaDataHandler() {
        super(ZKSchemaDataHandlerSettings.class);
    }

    @Override
    public void init(@NonNull SchemaDataHandlerSettings settings) throws Exception {
        Preconditions.checkArgument(settings instanceof ZKSchemaDataHandlerSettings);
        connection = env().connectionManager()
                .getConnection(settings.getConnection(), ZookeeperConnection.class);
        if (connection == null) {
            throw new ConfigurationException(
                    String.format("ZooKeeper connection not found. [name=%s]", settings.getConnection()));
        }
        zkConnection = (ZookeeperConnection) connection;
        zkBasePath = new PathUtils.ZkPathBuilder(((ZKSchemaDataHandlerSettings) settings).getBasePath())
                .withPath((settings).getSource())
                .build();
        CuratorFramework client = zkConnection().client();
        if (client.checkExists().forPath(zkBasePath) == null) {
            client.create().creatingParentContainersIfNeeded().forPath(zkBasePath);
        }
    }

    @Override
    protected Domain saveDomain(@NonNull Domain domain) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        PathUtils.ZkPathBuilder path = getDomainPath(domain.getName());
        String zp = path.build();
        CuratorFramework client = zkConnection().client();
        if (client.checkExists().forPath(zp) == null) {
            client.create().forPath(zp);
        }
        domain.setUpdatedTime(System.currentTimeMillis());
        client.setData().forPath(zp, JSONUtils.asBytes(domain, domain.getClass()));
        return domain;
    }

    @Override
    protected Domain fetchDomain(@NonNull String domain) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        PathUtils.ZkPathBuilder path = getDomainPath(domain);
        String zp = path.build();
        CuratorFramework client = zkConnection().client();
        return JSONUtils.read(client, zp, Domain.class);
    }

    @Override
    protected boolean deleteDomain(@NonNull Domain domain) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        PathUtils.ZkPathBuilder path = getDomainPath(domain.getName());
        String zp = path.build();
        CuratorFramework client = zkConnection().client();
        if (client.checkExists().forPath(zp) != null) {
            client.delete().deletingChildrenIfNeeded().forPath(zp);
            return true;
        }
        return false;
    }

    @Override
    protected SchemaEntity fetchEntity(@NonNull String domain,
                                       @NonNull String entity) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        PathUtils.ZkPathBuilder path = getEntityPath(domain, entity);
        String zp = path.build();
        CuratorFramework client = zkConnection().client();
        ZkSchemaEntity ze = JSONUtils.read(client, zp, ZkSchemaEntity.class);
        if (ze != null) {
            return ze.getEntity();
        }
        return null;
    }

    @Override
    protected SchemaEntity saveEntity(@NonNull SchemaEntity entity) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        PathUtils.ZkPathBuilder path = getEntityPath(entity.getDomain(), entity.getEntity());
        String zp = path.build();
        CuratorFramework client = zkConnection().client();
        if (client.checkExists().forPath(zp) == null) {
            client.create().forPath(zp);
        }
        entity.setUpdatedTime(System.currentTimeMillis());
        ZkSchemaEntity ze = new ZkSchemaEntity();
        ze.setEntity(entity);
        ze.setZkPath(zp);
        client.setData().forPath(zp, JSONUtils.asBytes(ze, ze.getClass()));
        return entity;
    }

    @Override
    protected boolean deleteEntity(@NonNull SchemaEntity entity) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        PathUtils.ZkPathBuilder path = getEntityPath(entity.getDomain(), entity.getEntity());
        String zp = path.build();
        CuratorFramework client = zkConnection().client();
        if (client.checkExists().forPath(zp) != null) {
            client.delete().deletingChildrenIfNeeded().forPath(zp);
            return true;
        }
        return false;
    }

    @Override
    protected EntitySchema fetchSchema(@NonNull SchemaEntity entity,
                                       SchemaVersion version) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        CuratorFramework client = zkConnection().client();
        if (version == null) {
            version = getLatestVersion(entity, client);
        }
        if (version != null) {
            PathUtils.ZkPathBuilder path = getSchemaPath(entity.getDomain(), entity.getEntity(), version);
            String zp = path.build();
            ZkEntitySchema ze = JSONUtils.read(client, zp, ZkEntitySchema.class);
            if (ze != null) {
                SchemaEntity se = entity;
                if (!Strings.isNullOrEmpty(ze.getZkEntityPath())) {
                    se = JSONUtils.read(client, ze.getZkEntityPath(), SchemaEntity.class);
                }
                ze.getSchema().setSchemaEntity(se);
                return ze.getSchema();
            }
        }
        return null;
    }

    @Override
    protected EntitySchema fetchSchema(@NonNull String uri) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        CuratorFramework client = zkConnection().client();
        return JSONUtils.read(client, uri, EntitySchema.class);
    }

    @Override
    protected EntitySchema saveSchema(@NonNull EntitySchema schema) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        SchemaEntity se = schema.getSchemaEntity();
        CuratorFramework client = zkConnection().client();
        String zp = getSchemaPath(se.getDomain(), se.getEntity(), schema.getVersion())
                .build();
        if (Strings.isNullOrEmpty(schema.getUri())) {
            schema.setUri(zp);
        } else if (schema.getUri().compareTo(zp) != 0) {
            throw new InvalidDataError(EntitySchema.class,
                    String.format("URI Mismatch. [expected=%s][saved=%s]", zp, schema.getUri()));
        }
        schema.setUpdatedTime(System.currentTimeMillis());
        ZkEntitySchema ze = new ZkEntitySchema();
        ze.setZkSchemaPath(zp);
        ze.setZkEntityPath(getEntityPath(se.getDomain(), se.getEntity()).build());
        ze.setSchema(schema);
        String sp = getSchemaPath(se.getDomain(), se.getEntity()).build();
        if (client.checkExists().forPath(sp) == null) {
            client.create().forPath(sp);
        }
        client.setData().forPath(sp, JSONUtils.asBytes(schema.getVersion(), SchemaVersion.class));
        if (client.checkExists().forPath(zp) == null) {
            client.create().creatingParentContainersIfNeeded().forPath(zp);
        }
        client.setData().forPath(zp, JSONUtils.asBytes(ze, ze.getClass()));
        return schema;
    }

    @Override
    protected boolean deleteSchema(@NonNull SchemaEntity entity,
                                   @NonNull SchemaVersion version) throws Exception {
        Preconditions.checkNotNull(zkConnection);
        CuratorFramework client = zkConnection().client();
        String zp = getSchemaPath(entity.getDomain(), entity.getEntity(), version)
                .build();
        if (client.checkExists().forPath(zp) != null) {
            client.delete().forPath(zp);
            return true;
        }
        return false;
    }

    @Override
    protected List<Domain> listDomains() throws Exception {
        Preconditions.checkNotNull(zkConnection);
        CuratorFramework client = zkConnection.client();
        String path = new PathUtils.ZkPathBuilder(zkBasePath)
                .withPath(ZK_PATH_DOMAINS)
                .build();
        List<String> nodes = client.getChildren().forPath(path);
        if (nodes != null && !nodes.isEmpty()) {
            List<Domain> domains = new ArrayList<>();
            for (String node : nodes) {
                String zp = new PathUtils.ZkPathBuilder(path)
                        .withPath(node)
                        .build();
                Domain d = JSONUtils.read(client, zp, Domain.class);
                if (d != null) {
                    domains.add(d);
                } else {
                    throw new Exception(String.format("Invalid domain: [path=%s]", zp));
                }
            }
            return domains;
        }
        return null;
    }

    private SchemaVersion getLatestVersion(SchemaEntity entity,
                                           CuratorFramework client) throws Exception {
        String zp = getSchemaPath(entity.getDomain(), entity.getEntity())
                .build();
        return JSONUtils.read(client, zp, SchemaVersion.class);
    }

    private PathUtils.ZkPathBuilder getDomainPath(String domain) {
        return new PathUtils.ZkPathBuilder(zkBasePath)
                .withPath(ZK_PATH_DOMAINS)
                .withPath(domain);
    }

    private PathUtils.ZkPathBuilder getEntityPath(String domain, String entity) {
        return getDomainPath(domain)
                .withPath(entity);
    }

    private PathUtils.ZkPathBuilder getSchemaPath(String domain, String entity) {
        return getEntityPath(domain, entity)
                .withPath("schema");
    }

    private PathUtils.ZkPathBuilder getSchemaPath(String domain, String entity, SchemaVersion version) {
        return getSchemaPath(domain, entity)
                .withPath(String.valueOf(version.getMajorVersion()))
                .withPath(String.valueOf(version.getMinorVersion()));
    }

    @Override
    public void close() throws IOException {
        zkConnection = null;
    }
}