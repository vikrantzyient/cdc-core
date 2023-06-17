package ai.sapper.cdc.core.messaging.builders;

import ai.sapper.cdc.common.config.ConfigReader;
import ai.sapper.cdc.core.BaseEnv;
import ai.sapper.cdc.core.messaging.MessageReceiver;
import ai.sapper.cdc.core.messaging.MessagingError;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

@Getter
@Accessors(fluent = true)
public abstract class MessageReceiverBuilder<I, M> {
    private final Class<? extends MessageReceiverSettings> settingsType;
    private BaseEnv<?> env;
    protected HierarchicalConfiguration<ImmutableNode> config;

    protected MessageReceiverBuilder(@NonNull Class<? extends MessageReceiverSettings> settingsType) {
        this.settingsType = settingsType;
    }

    public MessageReceiverBuilder<I, M> withEnv(@NonNull BaseEnv<?> env) {
        this.env = env;
        return this;
    }

    public MessageReceiver<I, M> build(@NonNull HierarchicalConfiguration<ImmutableNode> config) throws MessagingError {
        Preconditions.checkNotNull(env);
        try {
            ConfigReader reader = new ConfigReader(config, settingsType);
            reader.read();
            this.config = reader.config();
            return build((MessageReceiverSettings) reader.settings());
        } catch (Exception ex) {
            throw new MessagingError(ex);
        }
    }

    public abstract MessageReceiver<I, M> build(@NonNull MessageReceiverSettings settings) throws Exception;
}
