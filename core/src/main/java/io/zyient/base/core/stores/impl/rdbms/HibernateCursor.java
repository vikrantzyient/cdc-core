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

package io.zyient.base.core.stores.impl.rdbms;

import io.zyient.base.common.model.entity.IEntity;
import io.zyient.base.common.model.entity.IKey;
import io.zyient.base.core.stores.Cursor;
import io.zyient.base.core.stores.DataStoreException;
import lombok.NonNull;
import org.hibernate.ScrollableResults;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HibernateCursor<K extends IKey, E extends IEntity<K>> extends Cursor<K, E> {
    private final ScrollableResults<E> results;

    protected HibernateCursor(@NonNull ScrollableResults<E> results) {
        this.results = results;
    }

    @Override
    protected List<E> next(int page) throws DataStoreException {
        int index = page * pageSize() + 1;
        if (!results.position(index)) {
            return null;
        }
        List<E> batch = new ArrayList<>();
        int count = pageSize();
        while (count > 0) {
            E entity = results.get();
            batch.add(entity);
            if (!results.next()) {
                break;
            }
            count--;
        }
        return batch;
    }

    @Override
    public void close() throws IOException {
        results.close();
    }
}