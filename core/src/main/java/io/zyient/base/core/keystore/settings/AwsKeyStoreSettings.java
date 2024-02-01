/*
 * Copyright(C) (2024) Zyient Inc. (open.source at zyient dot io)
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

package io.zyient.base.core.keystore.settings;

import io.zyient.base.common.config.Config;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AwsKeyStoreSettings extends KeyStoreSettings {
    public static final String CONFIG_REGION = "region";
    public static final String CONFIG_NAME = "name";
    public static final String CONFIG_CACHE_TIMEOUT = "timeout";
    public static final String CONFIG_EXCLUDE_ENV = "excludeEnv";
    private static final long CACHE_TIMEOUT = 30 * 60 * 1000; // 30mins

    @Config(name = CONFIG_NAME)
    private String name;
    @Config(name = CONFIG_REGION)
    private String region;
    @Config(name = CONFIG_CACHE_TIMEOUT, required = false, type = Long.class)
    private long cacheTimeout = CACHE_TIMEOUT;
    @Config(name = CONFIG_EXCLUDE_ENV, required = false, type = Boolean.class)
    private Boolean excludeEnv;
}
