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

package ai.sapper.cdc.core.sources.settings;

import ai.sapper.cdc.common.config.Config;
import ai.sapper.cdc.common.config.Settings;
import ai.sapper.cdc.intake.model.EIntakeChannel;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
public class MailDataSourceSettings extends Settings {
    @Config(name = "name")
    private String name;
    @Config(name = "channel", type = EIntakeChannel.class)
    private EIntakeChannel channel;
    @Config(name = "partition", type = Short.class)
    private short partition;
    @Config(name = "mailbox")
    private String mailbox;
    @Config(name = "smtp", required = false)
    private String smtpConnectionName;
    @Config(name = "connection")
    private String connection;
}