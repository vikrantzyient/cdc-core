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

package io.zyient.base.core.messaging.azure.builders;

import com.google.common.base.Preconditions;
import io.zyient.base.core.connections.azure.ServiceBusProducerConnection;
import io.zyient.base.core.connections.settings.EConnectionType;
import io.zyient.base.core.messaging.azure.AzureMessageProducer;
import io.zyient.base.core.messaging.builders.MessageSenderBuilder;
import io.zyient.base.core.messaging.builders.MessageSenderSettings;
import lombok.NonNull;

public class AzureMessageProducerBuilder<M> extends MessageSenderBuilder<String, M> {
    private final Class<? extends AzureMessageProducer<M>> type;

    protected AzureMessageProducerBuilder(@NonNull Class<? extends MessageSenderSettings> settingsType,
                                          @NonNull Class<? extends AzureMessageProducer<M>> type) {
        super(settingsType);
        this.type = type;
    }

    protected AzureMessageProducerBuilder(@NonNull Class<? extends AzureMessageProducer<M>> type) {
        super(MessageSenderSettings.class);
        this.type = type;
    }

    @Override
    public AzureMessageProducer<M> build(@NonNull MessageSenderSettings settings) throws Exception {
        Preconditions.checkNotNull(env());
        Preconditions.checkArgument(settings.getType() == EConnectionType.serviceBus);
        ServiceBusProducerConnection connection = env().connectionManager()
                .getConnection(settings.getConnection(), ServiceBusProducerConnection.class);
        if (connection == null) {
            throw new Exception(
                    String.format("Kafka Producer connection not found. [name=%s]", settings.getConnection()));
        }
        if (!connection.isConnected()) {
            connection.connect();
        }
        AzureMessageProducer<M> producer = type.getDeclaredConstructor().newInstance();
        return (AzureMessageProducer<M>) producer
                .withConnection(connection)
                .init();
    }
}