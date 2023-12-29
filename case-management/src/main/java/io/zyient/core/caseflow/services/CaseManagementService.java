package io.zyient.core.caseflow.services;

import com.google.common.base.Preconditions;
import io.zyient.base.common.AbstractState;
import io.zyient.base.common.StateException;
import io.zyient.base.common.model.services.EConfigFileType;
import io.zyient.base.common.utils.DefaultLogger;
import io.zyient.base.core.BaseEnv;
import io.zyient.base.core.ServiceHandler;
import io.zyient.base.core.model.UserOrRole;
import io.zyient.core.caseflow.CaseManager;
import io.zyient.core.caseflow.errors.CaseActionException;
import io.zyient.core.caseflow.model.Case;
import io.zyient.core.caseflow.model.CaseDocument;
import io.zyient.core.caseflow.model.CaseId;
import io.zyient.core.caseflow.model.CaseState;
import io.zyient.core.persistence.AbstractDataStore;
import io.zyient.core.persistence.Cursor;
import io.zyient.core.persistence.DataStoreException;
import io.zyient.core.persistence.model.DocumentState;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Accessors(fluent = true)
public abstract class CaseManagementService<P extends Enum<P>, S extends CaseState<P>, E extends DocumentState<?>, T extends CaseDocument<E, T>, N extends Enum<N>> implements ServiceHandler<N> {
    private String configPath;
    private EConfigFileType configFileType;
    private String passKey;
    protected CaseManager<P, S, E, T> caseManager;
    protected BaseEnv<N> env;

    public CaseManagementService<P, S, E, T, N> withPassKey(@NonNull String passKey) {
        this.passKey = passKey;
        return this;
    }

    @Override
    public ServiceHandler<N> setConfigFile(@NonNull String configPath) {
        this.configPath = configPath;
        return this;
    }

    @Override
    public ServiceHandler<N> setConfigSource(@NonNull String configFileType) {
        this.configFileType = EConfigFileType.parse(configFileType);
        return this;
    }

    /**
     * @Override public ServiceHandler<EApEnvState> init() throws Exception {
     * if (Strings.isNullOrEmpty(configPath)) {
     * throw new Exception("Configuration file path not specified...");
     * }
     * if (configFileType == null) {
     * configFileType = EConfigFileType.File;
     * }
     * XMLConfiguration configuration = ConfigReader.read(configPath, configFileType);
     * env = (ApEnv) BaseEnv.create(ApEnv.class, configuration, passKey);
     * caseManager = env.caseManager();
     * if (caseManager == null) {
     * throw new Exception(String.format("Invalid configuration: Case manager not initialized. [path=%s]",
     * configPath));
     * }
     * return this;
     * }
     */


    @Override
    public ServiceHandler<N> stop() throws Exception {
        if (env != null) {
            env.close();
            BaseEnv.remove(env.name());
        }
        return this;
    }

    @Override
    public AbstractState<N> status() {
        Preconditions.checkNotNull(env);
        return env.state();
    }

    @Override
    public String name() {
        Preconditions.checkNotNull(env);
        return env.name();
    }

    @Override
    public void checkState() throws Exception {
        Preconditions.checkNotNull(env);
        if (!env.state().isAvailable()) {
            throw new StateException(String.format("Service not available. [state=%s]",
                    env.state().getState().name()));
        }
    }

    @SuppressWarnings("unchecked")
    public List<Case<P, S, E, T>> findAssignedTo(@NonNull UserOrRole assignedTo,
                                                 List<? extends Enum<?>> states,
                                                 @NonNull Class<? extends Case<P, S, E, T>> caseType,
                                                 int currentPage,
                                                 int batchSize,
                                                 boolean fetchDocuments) throws DataStoreException {
        try {
            List<Case<P, S, E, T>> cases = null;
            StringBuilder condition = new StringBuilder("assignedTo.name = :name");
            Map<String, Object> params = Map.of("name", assignedTo.getName());
            if (states != null && !states.isEmpty()) {
                params = new HashMap<>(params);
                condition = new StringBuilder(String.format("%s AND (", condition.toString()));
                int count = 0;
                for (Enum<?> state : states) {
                    if (count > 0) {
                        condition.append(" OR ");
                    }
                    String k = String.format("state_%d", count);
                    condition.append(String.format("caseState = :%s", k));
                    params.put(k, state.name());
                    count++;
                }
                condition.append(")");
            }
            AbstractDataStore.Q query = new AbstractDataStore.Q()
                    .where(condition.toString())
                    .addAll(params);
            try {
                cases = (List<Case<P, S, E, T>>) caseManager.search(query,
                        caseType,
                        currentPage,
                        batchSize,
                        fetchDocuments,
                        null);
            } catch (Exception ex) {
                throw new CaseActionException(ex);
            }
            return cases;
        } catch (Throwable t) {
            DefaultLogger.stacktrace(t);
            throw new DataStoreException(t);
        }
    }
}