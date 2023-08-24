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

package ai.sapper.cdc.core.stores.impl.settings;

import ai.sapper.cdc.common.config.Config;
import ai.sapper.cdc.common.config.ConfigPath;
import ai.sapper.cdc.common.config.units.TimeUnitValue;
import ai.sapper.cdc.common.config.units.TimeValueParser;
import ai.sapper.cdc.core.stores.AbstractDataStoreSettings;
import ai.sapper.cdc.core.stores.EDataStoreType;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
        property = "@class")
@ConfigPath(path = "mongodb")
public class MongoDbSettings extends AbstractDataStoreSettings {
    @Config(name = "db")
    private String db;
    @Config(name = "sessionTimeout", required = false, parser = TimeValueParser.class)
    private TimeUnitValue sessionTimeout = new TimeUnitValue(30 * 60 * 1000, TimeUnit.MILLISECONDS);

    public MongoDbSettings() {
        setType(EDataStoreType.kvstore);
    }
}
