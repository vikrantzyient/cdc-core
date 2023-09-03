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

package ai.sapper.cdc.common.config;

import com.google.common.base.Strings;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;

public class StringListParser implements ConfigValueParser<List<String>> {
    @Override
    public List<String> parse(@NonNull String value) throws Exception {
        if (!Strings.isNullOrEmpty(value)) {
            String[] parts = value.split(",");
            return Arrays.stream(parts).toList();
        }
        return null;
    }

    @Override
    public String serialize(@NonNull List<String> value) throws Exception {
        StringBuilder builder = new StringBuilder();
        for(String v : value) {
            if (!builder.isEmpty()) {
                builder.append(",");
            }
            builder.append(v);
        }
        return builder.toString();
    }
}
