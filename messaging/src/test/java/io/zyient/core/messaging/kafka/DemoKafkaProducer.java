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

package io.zyient.core.messaging.kafka;

import io.zyient.base.common.messaging.MessagingError;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;

public class DemoKafkaProducer extends BaseKafkaProducer<String> {
    @Override
    protected byte[] serialize(@NonNull String message) throws MessagingError {
        return message.getBytes(StandardCharsets.UTF_8);
    }
}