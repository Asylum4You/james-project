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
package org.apache.james.modules.mailbox;

import static org.apache.james.modules.Names.MAILBOXMANAGER_NAME;

import java.io.FileNotFoundException;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.adapter.mailbox.ACLUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.MailboxUserDeletionTaskStep;
import org.apache.james.adapter.mailbox.MailboxUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.QuotaUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.UserRepositoryAuthenticator;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.events.EventListener;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.cassandra.change.CassandraChangesConfiguration;
import org.apache.james.jmap.cassandra.change.CassandraEmailChangeRepository;
import org.apache.james.jmap.cassandra.change.CassandraMailboxChangeRepository;
import org.apache.james.jmap.cassandra.change.CassandraStateFactory;
import org.apache.james.jmap.cassandra.change.EmailChangeRepositoryDAO;
import org.apache.james.jmap.cassandra.change.MailboxChangeRepositoryDAO;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.AttachmentIdFactory;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.MailboxCounterCorrector;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.StringBackedAttachmentIdFactory;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.ACLMapper;
import org.apache.james.mailbox.cassandra.mail.AttachmentBlobReferenceSource;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV3DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.mail.MessageBlobReferenceSource;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLModule;
import org.apache.james.mailbox.cassandra.mail.task.CassandraMailboxCounterCorrector;
import org.apache.james.mailbox.cassandra.modules.CassandraAclDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraUidDataDefinition;
import org.apache.james.mailbox.indexer.MessageIdReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.event.MailboxSubscriptionListener;
import org.apache.james.mailbox.store.mail.AttachmentIdAssignationStrategy;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.MessageParserImpl;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.utils.MailboxManagerDefinition;
import org.apache.james.utils.PropertiesProvider;
import org.apache.mailbox.tools.indexer.MessageIdReIndexerImpl;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class CassandraMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DefaultEventModule());
        install(new CassandraQuotaModule());
        install(new CassandraDeadLetterModule());
        install(new CassandraThreadIdGuessingModule());

        bind(CassandraApplicableFlagDAO.class).in(Scopes.SINGLETON);
        bind(CassandraAttachmentDAOV2.class).in(Scopes.SINGLETON);
        bind(CassandraDeletedMessageDAO.class).in(Scopes.SINGLETON);
        bind(CassandraFirstUnseenDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxCounterDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxDAO.class).in(Scopes.SINGLETON);
        bind(CassandraACLDAOV2.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxPathV3DAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxRecentsDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMessageDAOV3.class).in(Scopes.SINGLETON);
        bind(CassandraMessageIdDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMessageIdToImapUidDAO.class).in(Scopes.SINGLETON);
        bind(CassandraUserMailboxRightsDAO.class).in(Scopes.SINGLETON);

        bind(CassandraMailboxManager.class).in(Scopes.SINGLETON);
        bind(StoreSubscriptionManager.class).in(Scopes.SINGLETON);
        bind(StoreAttachmentManager.class).in(Scopes.SINGLETON);
        bind(StoreMessageIdManager.class).in(Scopes.SINGLETON);
        bind(StoreRightManager.class).in(Scopes.SINGLETON);
        bind(SessionProviderImpl.class).in(Scopes.SINGLETON);

        bind(CassandraMailboxMapper.class).in(Scopes.SINGLETON);

        bind(CassandraId.Factory.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(CassandraMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(CassandraThreadIdGuessingAlgorithm.class).in(Scopes.SINGLETON);
        bind(CassandraModSeqProvider.class).in(Scopes.SINGLETON);
        bind(CassandraUidProvider.class).in(Scopes.SINGLETON);
        bind(NoMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(EmailChangeRepositoryDAO.class).in(Scopes.SINGLETON);
        bind(MailboxChangeRepositoryDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxCounterCorrector.class).in(Scopes.SINGLETON);

        bind(ReIndexerImpl.class).in(Scopes.SINGLETON);
        bind(MessageIdReIndexerImpl.class).in(Scopes.SINGLETON);

        bind(MessageMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(AttachmentIdFactory.class).to(StringBackedAttachmentIdFactory.class);
        bind(AttachmentMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(SubscriptionMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);

        bind(ACLMapper.class).to(CassandraACLMapper.class);
        bind(ModSeqProvider.class).to(CassandraModSeqProvider.class);
        bind(UidProvider.class).to(CassandraUidProvider.class);
        bind(SubscriptionManager.class).to(StoreSubscriptionManager.class);
        bind(MailboxPathLocker.class).to(NoMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(MailboxManager.class).to(CassandraMailboxManager.class);
        bind(StoreMailboxManager.class).to(CassandraMailboxManager.class);
        bind(MailboxChangeRepository.class).to(CassandraMailboxChangeRepository.class);
        bind(EmailChangeRepository.class).to(CassandraEmailChangeRepository.class);
        bind(State.Factory.class).to(CassandraStateFactory.class);
        bind(MailboxId.Factory.class).to(CassandraId.Factory.class);
        bind(State.Factory.class).to(CassandraStateFactory.class);
        bind(MessageId.Factory.class).to(CassandraMessageId.Factory.class);
        bind(ThreadIdGuessingAlgorithm.class).to(CassandraThreadIdGuessingAlgorithm.class);
        bind(MessageIdManager.class).to(StoreMessageIdManager.class);
        bind(AttachmentManager.class).to(StoreAttachmentManager.class);
        bind(RightManager.class).to(StoreRightManager.class);
        bind(SessionProvider.class).to(SessionProviderImpl.class);
        bind(AttachmentContentLoader.class).to(AttachmentManager.class);
        bind(MailboxCounterCorrector.class).to(CassandraMailboxCounterCorrector.class);
        bind(MessageParser.class).toInstance(new MessageParserImpl());
        bind(AttachmentIdAssignationStrategy.class).to(AttachmentIdAssignationStrategy.Default.class);

        bind(Limit.class).annotatedWith(Names.named(CassandraEmailChangeRepository.LIMIT_NAME)).toInstance(Limit.of(256));
        bind(Limit.class).annotatedWith(Names.named(CassandraMailboxChangeRepository.LIMIT_NAME)).toInstance(Limit.of(256));

        bind(ReIndexer.class).to(ReIndexerImpl.class);
        bind(MessageIdReIndexer.class).to(MessageIdReIndexerImpl.class);

        Multibinder<CassandraDataDefinition> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraDataDefinition.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAclDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAttachmentDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMailboxDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMessageDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMailboxCounterDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAnnotationDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraApplicableFlagsDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraDeletedMessageDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraFirstUnseenDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMailboxRecentsDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraModSeqDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraSubscriptionDataDefinition.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraUidDataDefinition.MODULE);

        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(CassandraMailboxManagerDefinition.class);

        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class)
            .addBinding().to(MailboxAnnotationListener.class);
        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class)
            .addBinding().to(MailboxSubscriptionListener.class);
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding().to(DeleteMessageListener.class);
        Multibinder.newSetBinder(binder(), DeleteMessageListener.DeletionCallback.class);

        bind(MailboxManager.class).annotatedWith(Names.named(MAILBOXMANAGER_NAME)).to(MailboxManager.class);

        Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {})
            .addBinding().toInstance(ACLModule.ACL_UPDATE);

        Multibinder.newSetBinder(binder(), BlobReferenceSource.class)
            .addBinding().to(AttachmentBlobReferenceSource.class);
        Multibinder.newSetBinder(binder(), BlobReferenceSource.class)
            .addBinding().to(MessageBlobReferenceSource.class);

        Multibinder<UsernameChangeTaskStep> usernameChangeTaskStepMultibinder = Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(MailboxUsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(ACLUsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(QuotaUsernameChangeTaskStep.class);

        Multibinder<DeleteUserDataTaskStep> deleteUserDataTaskStepMultibinder = Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class);
        deleteUserDataTaskStepMultibinder.addBinding().to(MailboxUserDeletionTaskStep.class);
    }

    @Provides
    @Singleton
    CassandraACLMapper aclMapper(CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                                 CassandraACLDAOV2 cassandraACLDAOV2,
                                 CqlSession session,
                                 CassandraConfiguration cassandraConfiguration) {
        return new CassandraACLMapper(
            new CassandraACLMapper.StoreV2(userMailboxRightsDAO, cassandraACLDAOV2,
                new CassandraEventStore(new EventStoreDao(session, JsonEventSerializer.forModules(ACLModule.ACL_UPDATE).withoutNestedType()))),
            cassandraConfiguration);
    }
    
    @Singleton
    private static class CassandraMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private CassandraMailboxManagerDefinition(CassandraMailboxManager manager) {
            super("cassandra-mailboxmanager", manager);
        }
    }

    @Provides
    @Singleton
    CassandraChangesConfiguration providesCassandraChangesConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("cassandra");
            return CassandraChangesConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            return CassandraChangesConfiguration.DEFAULT;
        }
    }
}
