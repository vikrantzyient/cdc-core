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

package io.zyient.core.mapping.readers.impl.separated;

import com.google.common.base.Strings;
import io.zyient.base.common.utils.DefaultLogger;
import io.zyient.core.mapping.model.Column;
import io.zyient.core.mapping.model.InputContentInfo;
import io.zyient.core.mapping.readers.settings.SeparatedReaderSettings;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlattenedInputReaderTest {
    private static final String FILE_WITHOUT_HEADER = "src/test/resources/data/TransactionDetail_2023-07-03.txt";

    @Test
    void nextBatchWitCustomHeader() {
        try {
            Map<Integer, Column> headers = Map.of(
                    0, new Column("MASTERACCOUNT", 0),
                    1, new Column("ACCOUNTNUMBER", 1),
                    2, new Column("TRANSACTION", 2),
                    3, new Column("TRANSACTIONDESCRIPTION", 3),
                    4, new Column("POSTINGDATE", 4),
                    5, new Column("CUSIP", 5)
            );
            File file = new File(FILE_WITHOUT_HEADER);
            InputContentInfo ci = new InputContentInfo()
                    .path(file)
                    .sourceURI(file.toURI());
            SeparatedReaderSettings settings = new SeparatedReaderSettings();
            settings.setHasHeader(false);
            settings.setHeaders(headers);
            settings.setReadBatchSize(32);
            SeparatedInputReader reader = (SeparatedInputReader) new SeparatedInputReader()
                    .contentInfo(ci)
                    .settings(settings);
            try (SeparatedReadCursor cursor = (SeparatedReadCursor) reader.open()) {
                int count = 0;
                while (true) {
                    Map<String, Object> data = cursor.next();
                    if (data == null) {
                        break;
                    }
                    String v = (String) data.get(headers.get(4).getName());
                    assertFalse(Strings.isNullOrEmpty(v));
                    count++;
                }
                assertEquals(300024, count);
            }
        } catch (Exception ex) {
            DefaultLogger.stacktrace(ex);
            fail(ex);
        }
    }
}