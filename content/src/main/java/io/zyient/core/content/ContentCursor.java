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

package io.zyient.core.content;

import io.zyient.base.common.model.entity.IKey;
import io.zyient.core.filesystem.FileSystem;
import io.zyient.core.filesystem.Reader;
import io.zyient.core.filesystem.model.FileInode;
import io.zyient.core.filesystem.model.PathInfo;
import io.zyient.core.persistence.DataStoreException;
import io.zyient.core.persistence.model.Document;
import io.zyient.core.persistence.model.DocumentState;
import lombok.NonNull;

import java.io.File;
import java.util.Map;

public interface ContentCursor<E extends DocumentState<?>, K extends IKey, D extends Document<E, K, D>> {

    static <E extends DocumentState<?>, K extends IKey, D extends Document<E, K, D>> Document<E, K, D> fetch(@NonNull Document<E, K, D> doc,
                                                                                                             @NonNull FileSystem fileSystem) throws DataStoreException {
        try {
            Map<String, String> map = doc.pathConfig();
            PathInfo pi = fileSystem.parsePathInfo(map);
            FileInode fi = (FileInode) fileSystem.getInode(pi);
            if (fi == null) {
                throw new DataStoreException(String.format("Document not found. [uri=%s]", doc.getUri()));
            }
            try (Reader reader = fileSystem.reader(pi)) {
                File path = reader.copy();
                doc.setPath(path);
            }
            return doc;
        } catch (Exception ex) {
            throw new DataStoreException(ex);
        }
    }


    boolean download();

    ContentCursor<E, K, D> download(boolean download);

    Document<E, K, D> fetch(@NonNull Document<E, K, D> doc) throws DataStoreException;
}
