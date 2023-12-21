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

package io.zyient.core.mapping.model;


import io.zyient.base.common.config.Config;
import io.zyient.core.mapping.config.RegexGroupParser;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class RegexMappedElement extends MappedElement {
    @Config(name = "name")
    private String name;
    @Config(name = "regex", required = true)
    private String regex;
    @Config(name = "replaceWith", required = false)
    private String replace;
    @Config(name = "groups", required = false, parser = RegexGroupParser.class)
    private Map<Integer, List<Integer>> groups;
    @Config(name = "format", required = false)
    private String format;
}
