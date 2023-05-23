package ai.sapper.cdc.core.messaging.builders;

import ai.sapper.cdc.common.config.ConfigReader;
import ai.sapper.cdc.core.BaseEnv;
import ai.sapper.cdc.core.messaging.MessageReceiver;
import ai.sapper.cdc.core.messaging.MessageSender;
import ai.sapper.cdc.core.messaging.MessagingError;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

@Getter
@Setter
@Accessors(fluent = true)
public abstract class MessageReceiverBuilder<I, M> {
    private final Class<? extends MessageReceiverSettings> settingsType;
    private final BaseEnv<?> env;
    private HierarchicalConfiguration<ImmutableNode> config;

    protected MessageReceiverBuilder(@NonNull BaseEnv<?> env,
                                     @NonNull Class<? extends MessageReceiverSettings> settingsType) {
        this.env = env;
        this.settingsType = settingsType;
    }

    public MessageReceiver<I, M> build() throws MessagingError {
        Preconditions.checkNotNull(config);
        try {
            ConfigReader reader = new ConfigReader(config, settingsType);
            reader.read();
            return build((MessageReceiverSettings) reader.settings());
        } catch (Exception ex) {
            throw new MessagingError(ex);
        }
    }

    public abstract MessageReceiver<I, M> build(@NonNull MessageReceiverSettings settings) throws Exception;
}
