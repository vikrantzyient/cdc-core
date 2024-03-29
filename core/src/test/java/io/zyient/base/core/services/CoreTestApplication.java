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

package io.zyient.base.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.zyient.base.common.GlobalConstants;
import io.zyient.base.core.BaseEnv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@SpringBootApplication
@ComponentScan("io.zyient.base.core.services")
@Configuration
public class CoreTestApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(CoreTestApplication.class, args);
    }

    @Bean
    public MeterRegistry getMeterRegistry() {
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        BaseEnv.registry(registry);
        return registry;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return GlobalConstants.getJsonMapper();
    }
}
