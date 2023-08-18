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

package ai.sapper.cdc.core.connections.ws;

import ai.sapper.cdc.common.config.ZkConfigReader;
import ai.sapper.cdc.common.utils.PathUtils;
import ai.sapper.cdc.core.BaseEnv;
import ai.sapper.cdc.core.connections.Connection;
import ai.sapper.cdc.core.connections.ConnectionConfig;
import ai.sapper.cdc.core.connections.ConnectionError;
import ai.sapper.cdc.core.connections.ZookeeperConnection;
import ai.sapper.cdc.core.connections.settings.ConnectionSettings;
import ai.sapper.cdc.core.connections.settings.EConnectionType;
import ai.sapper.cdc.core.connections.settings.WebServiceConnectionSettings;
import ai.sapper.cdc.core.connections.ws.auth.WebServiceAuthHandler;
import com.google.common.base.Preconditions;
import jakarta.ws.rs.client.Invocation;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.curator.framework.CuratorFramework;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.JerseyWebTarget;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Getter
@Accessors(fluent = true)
public class WebServiceConnection implements Connection {
    @Getter(AccessLevel.NONE)
    protected final ConnectionState state = new ConnectionState();
    private URL endpoint;
    private WebServiceConnectionConfig config;
    private JerseyClient client;
    private String name;
    private WebServiceConnectionSettings settings;
    private WebServiceAuthHandler handler;

    public WebServiceConnection() {
    }

    public WebServiceConnection(@NonNull String name,
                                @NonNull String endpoint) throws MalformedURLException {
        this.name = name;
        this.endpoint = new URL(endpoint);
        client = new JerseyClientBuilder().build();
        state.setState(EConnectionState.Initialized);
    }

    /**
     * @return
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * @param xmlConfig
     * @return
     * @throws ConnectionError
     */
    @Override
    public Connection init(@NonNull HierarchicalConfiguration<ImmutableNode> xmlConfig,
                           @NonNull BaseEnv<?> env) throws ConnectionError {
        synchronized (state) {
            try {
                config = new WebServiceConnectionConfig(xmlConfig);
                config.read();
                settings = (WebServiceConnectionSettings) config.settings();
                JerseyClientBuilder builder = builder();
                if (this.settings.getAuthHandler() != null) {
                    handler = this.settings.getAuthHandler().getDeclaredConstructor()
                            .newInstance();
                    HierarchicalConfiguration<ImmutableNode> node = config.config()
                            .configurationAt(WebServiceConnectionSettings.Constants.CONFIG_AUTH_PATH);
                    handler.init(node, env);
                    handler.build(builder);
                    this.settings.setAuthSettings(handler.settings());
                }
                client = builder.build();
                name = this.settings.getName();
                endpoint = new URL(this.settings.getEndpoint());
                state.setState(EConnectionState.Initialized);
                return this;
            } catch (Exception ex) {
                state.error(ex);
                throw new ConnectionError(ex);
            }
        }
    }

    @Override
    public Connection init(@NonNull String name,
                           @NonNull ZookeeperConnection connection,
                           @NonNull String path,
                           @NonNull BaseEnv<?> env) throws ConnectionError {
        synchronized (state) {
            try {
                if (state.isConnected()) {
                    close();
                }
                state.clear();

                CuratorFramework client = connection.client();
                String zkPath = new PathUtils.ZkPathBuilder(path)
                        .withPath(WebServiceConnectionConfig.__CONFIG_PATH)
                        .build();
                ZkConfigReader reader = new ZkConfigReader(client, WebServiceConnectionSettings.class);
                if (!reader.read(zkPath)) {
                    throw new ConnectionError(
                            String.format("WebService Connection settings not found. [path=%s]", zkPath));
                }
                settings = (WebServiceConnectionSettings) reader.settings();
                settings.validate();

                setup(settings, env);
                state.setState(EConnectionState.Initialized);
            } catch (Exception ex) {
                state.error(ex);
                throw new ConnectionError(ex);
            }
        }
        return this;
    }

    @Override
    public Connection setup(@NonNull ConnectionSettings settings,
                            @NonNull BaseEnv<?> env) throws ConnectionError {
        Preconditions.checkArgument(settings instanceof WebServiceConnectionSettings);
        synchronized (state) {
            try {
                this.settings = (WebServiceConnectionSettings) settings;
                JerseyClientBuilder builder = builder();
                if (this.settings.getAuthHandler() != null) {
                    handler = this.settings.getAuthHandler().getDeclaredConstructor()
                            .newInstance();
                    handler.init(this.settings.getAuthSettings(), env);
                    handler.build(builder);
                }
                client = builder.build();
                name = this.settings.getName();
                endpoint = new URL(this.settings.getEndpoint());

                state.setState(EConnectionState.Initialized);
                return this;
            } catch (Exception ex) {
                state.error(ex);
                state.error(ex);
                throw new ConnectionError(ex);
            }
        }
    }

    protected JerseyClientBuilder builder() throws Exception {
        JerseyClientBuilder builder = (JerseyClientBuilder) new JerseyClientBuilder()
                .connectTimeout(this.settings.getConnectionTimeout().normalized(), TimeUnit.MILLISECONDS)
                .readTimeout(this.settings.getReadTimeout().normalized(), TimeUnit.MILLISECONDS);
        if (this.settings.isUseSSL()) {
            builder.sslContext(SSLContext.getDefault());
        }
        builder.register(JacksonJaxbJsonProvider.class);

        return builder;
    }

    /**
     * @return
     * @throws ConnectionError
     */
    @Override
    public Connection connect() throws ConnectionError {
        state.check(EConnectionState.Initialized);
        state.setState(EConnectionState.Connected);
        return this;
    }

    public JerseyWebTarget connect(@NonNull String path) throws ConnectionError {
        state.check(EConnectionState.Connected);
        try {
            return client.target(endpoint.toURI()).path(path);
        } catch (Exception ex) {
            throw new ConnectionError(ex);
        }
    }

    public Invocation.Builder build(@NonNull JerseyWebTarget target,
                                    @NonNull String mediaType) throws ConnectionError {
        state.check(EConnectionState.Connected);
        if (handler != null) {
            return handler.build(target, mediaType);
        } else {
            return target.request(mediaType);
        }
    }

    /**
     * @return
     */
    @Override
    public Throwable error() {
        return state.getError();
    }

    /**
     * @return
     */
    @Override
    public EConnectionState connectionState() {
        return state.getState();
    }

    /**
     * @return
     */
    @Override
    public boolean isConnected() {
        return state.getState() == EConnectionState.Initialized;
    }

    @Override
    public String path() {
        return WebServiceConnectionConfig.__CONFIG_PATH;
    }

    @Override
    public EConnectionType type() {
        return settings.getType();
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {

    }


    @Getter
    @Accessors(fluent = true)
    public static class WebServiceConnectionConfig extends ConnectionConfig {
        private static final String __CONFIG_PATH = "rest";

        public WebServiceConnectionConfig(@NonNull HierarchicalConfiguration<ImmutableNode> config) {
            super(config,
                    __CONFIG_PATH,
                    WebServiceConnectionSettings.class,
                    WebServiceConnection.class);
        }
    }
}