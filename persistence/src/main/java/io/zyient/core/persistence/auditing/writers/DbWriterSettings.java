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

package io.zyient.core.persistence.auditing.writers;

import io.zyient.base.common.config.Config;
import io.zyient.base.common.config.Settings;
import io.zyient.base.common.config.units.TimeUnitValue;
import io.zyient.base.common.config.units.TimeValueParser;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class DbWriterSettings extends Settings {
    @Config(name = "connection")
    private String connection;
    @Config(name = "commit.batchSize", required = false, type = Integer.class)
    private int commitBatchSize = 32;
    @Config(name = "commit.timeout", required = false, parser = TimeValueParser.class)
    private TimeUnitValue commitTimeout = new TimeUnitValue(15, TimeUnit.SECONDS);
}
