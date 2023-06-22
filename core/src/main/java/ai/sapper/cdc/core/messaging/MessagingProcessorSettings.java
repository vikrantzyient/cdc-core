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

package ai.sapper.cdc.core.messaging;

import ai.sapper.cdc.common.config.Config;
import ai.sapper.cdc.core.messaging.builders.MessageReceiverBuilder;
import ai.sapper.cdc.core.messaging.builders.MessageReceiverSettings;
import ai.sapper.cdc.core.messaging.builders.MessageSenderBuilder;
import ai.sapper.cdc.core.messaging.builders.MessageSenderSettings;
import ai.sapper.cdc.core.processing.ProcessorSettings;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * <pre>
 *     <processor>
 *         <name>[Processor Name]</name>
 *         <type>[Processor Class]</type>
 *         <queue>
 *              <builder>
 *                  <type>[Message Builder class]</type>
 *                  <settingsType>[Message Builder Settings class]</settingsType>
 *                  <queue>[Queue Configuration]</queue>
 *              </builder>
 *         </queue>
 *         <errors>
 *             <builder>
 *                <type>[Message Builder class]</type>
 *                <settingsType>[Message Builder Settings class]</settingsType>
 *                <errors>[Error Queue Configuration]</errors>
 *             </builder>
 *             <readBatchTimeout>[Batch read timeout, optional]</readBatchTimeout>
 *         </errors>
 *     </processor>
 * </pre>
 */
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
public class MessagingProcessorSettings extends ProcessorSettings {
    public static class Constants {
        public static final String __CONFIG_PATH_RECEIVER = "queue";
        public static final String __CONFIG_PATH_ERRORS = "errors";

        public static final String CONFIG_BUILDER_TYPE = "queue.builder.type";
        public static final String CONFIG_MESSAGING_SETTINGS_TYPE = "queue.builder.settingsType";
        public static final String CONFIG_ERRORS_BUILDER_TYPE = "errors.builder.type";
        public static final String CONFIG_ERRORS_MESSAGING_SETTINGS_TYPE = "errors.builder.settingsType";
        public static final String CONFIG_BATCH_RECEIVE_TIMEOUT = "readBatchTimeout";
    }

    @Config(name = Constants.CONFIG_BUILDER_TYPE, type = Class.class)
    private Class<? extends MessageReceiverBuilder<?, ?>> builderType;
    @Config(name = Constants.CONFIG_MESSAGING_SETTINGS_TYPE, type = Class.class)
    private Class<? extends MessageReceiverSettings> builderSettingsType;
    @Config(name = Constants.CONFIG_BATCH_RECEIVE_TIMEOUT, required = false, type = Long.class)
    private long receiveBatchTimeout = 1000;
    @Config(name = Constants.CONFIG_ERRORS_BUILDER_TYPE, required = false, type = Class.class)
    private Class<? extends MessageSenderBuilder<?, ?>> errorsBuilderType;
    @Config(name = Constants.CONFIG_ERRORS_MESSAGING_SETTINGS_TYPE, required = false, type = Class.class)
    private Class<? extends MessageSenderSettings> errorsBuilderSettingsType;
}
