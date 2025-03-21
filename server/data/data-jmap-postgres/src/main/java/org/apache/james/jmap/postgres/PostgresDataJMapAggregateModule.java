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

package org.apache.james.jmap.postgres;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.jmap.postgres.change.PostgresEmailChangeModule;
import org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule;
import org.apache.james.jmap.postgres.filtering.PostgresFilteringProjectionModule;
import org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule;
import org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule;
import org.apache.james.jmap.postgres.projections.PostgresMessageFastViewProjectionModule;
import org.apache.james.jmap.postgres.pushsubscription.PostgresPushSubscriptionModule;
import org.apache.james.jmap.postgres.upload.PostgresUploadModule;

public interface PostgresDataJMapAggregateModule {
    PostgresModule MODULE = PostgresModule.aggregateModules(
        PostgresUploadModule.MODULE,
        PostgresMessageFastViewProjectionModule.MODULE,
        PostgresEmailChangeModule.MODULE,
        PostgresMailboxChangeModule.MODULE,
        PostgresPushSubscriptionModule.MODULE,
        PostgresFilteringProjectionModule.MODULE,
        PostgresCustomIdentityModule.MODULE,
        PostgresEmailQueryViewModule.MODULE);
}
