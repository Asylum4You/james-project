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

package org.apache.james.mailbox.quota.cassandra.listeners;

import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreExtension;
import org.apache.james.mailbox.quota.mailing.events.QuotaEventDTOModules;
import org.apache.james.mailbox.quota.mailing.listeners.QuotaThresholdMailingIntegrationTest;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraQuotaMailingListenersIntegrationTest implements QuotaThresholdMailingIntegrationTest {
    @RegisterExtension
    static CassandraEventStoreExtension eventStoreExtension =
        new CassandraEventStoreExtension(JsonEventSerializer.forModules(QuotaEventDTOModules.QUOTA_THRESHOLD_CHANGE).withoutNestedType());
}
