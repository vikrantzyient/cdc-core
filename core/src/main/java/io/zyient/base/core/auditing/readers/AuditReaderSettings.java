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

package io.zyient.base.core.auditing.readers;

import io.zyient.base.common.config.Config;
import io.zyient.base.common.config.Settings;
import io.zyient.base.core.auditing.AuditRecord;
import io.zyient.base.core.auditing.IAuditSerDe;
import io.zyient.base.core.auditing.JsonAuditRecord;
import io.zyient.base.core.auditing.JsonAuditSerDe;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditReaderSettings extends Settings {
    @Config(name = "name", required = false)
    private String name;
    @Config(name = "serializer", required = false, type = Class.class)
    private Class<? extends IAuditSerDe<?>> serializer = JsonAuditSerDe.class;
    @Config(name = "encryption.enabled", required = false, type = Boolean.class)
    private boolean encrypted = true;
    @Config(name = "encryption.key", required = false)
    private String encryptionKey;
    @Config(name = "recordType", required = false, type = Class.class)
    private Class<? extends AuditRecord<?>> recordType = JsonAuditRecord.class;
    @Config(name = "pageSize", required = false, type = Integer.class)
    private int pageSize = 128;
}
