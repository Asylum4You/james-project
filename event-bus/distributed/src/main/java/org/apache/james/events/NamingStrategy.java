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

package org.apache.james.events;

import reactor.rabbitmq.QueueSpecification;

public interface NamingStrategy {
    EventBusName JMAP_EVENT_BUS_NAME = new EventBusName("jmapEvent");
    EventBusName MAILBOX_EVENT_BUS_NAME = new EventBusName("mailboxEvent");
    EventBusName CONTENT_DELETION_EVENT_BUS_NAME = new EventBusName("contentDeletionEvent");
    NamingStrategy JMAP_NAMING_STRATEGY = new DefaultNamingStrategy(JMAP_EVENT_BUS_NAME);
    NamingStrategy MAILBOX_EVENT_NAMING_STRATEGY = new DefaultNamingStrategy(MAILBOX_EVENT_BUS_NAME);
    NamingStrategy CONTENT_DELETION_NAMING_STRATEGY = new DefaultNamingStrategy(CONTENT_DELETION_EVENT_BUS_NAME);

    RegistrationQueueName queueName(EventBusId eventBusId);

    QueueSpecification deadLetterQueue();

    String exchange();

    String deadLetterExchange();

    GroupConsumerRetry.RetryExchangeName retryExchange(Group group);

    GroupRegistration.WorkQueueName workQueue(Group group);

    EventBusName getEventBusName();
}
