/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.memory.upload;

import java.time.Clock;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadRepositoryContract;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;

public class InMemoryUploadRepositoryTest implements UploadRepositoryContract {

    private BlobStoreDAO blobStoreDAO;
    private UploadRepository testee;
    private UpdatableTickingClock clock;

    @BeforeEach
    void setUp() {
        clock = new UpdatableTickingClock(Clock.systemUTC().instant());
        blobStoreDAO = new MemoryBlobStoreDAO();
        testee = new InMemoryUploadRepository(new PlainBlobId.Factory(), blobStoreDAO, clock);
    }

    @Override
    public UploadRepository testee() {
        return testee;
    }

    @Override
    public UpdatableTickingClock clock() {
        return clock;
    }

    @Override
    public BlobStoreDAO blobStoreDAO() {
        return blobStoreDAO;
    }
}
