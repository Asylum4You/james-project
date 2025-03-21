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

package org.apache.james.imap.processor;

import static org.apache.james.imap.ImapFixture.TAG;
import static org.apache.james.imap.api.message.response.StatusResponse.Type.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.GetQuotaRootRequest;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.imap.message.response.QuotaRootResponse;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class GetQuotaRootProcessorTest {

    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("plop", Optional.empty());
    private static final Username PLOP = Username.of("plop");
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(PLOP, "INBOX");
    private static final Quota<QuotaCountLimit, QuotaCountUsage> MESSAGE_QUOTA =
        Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(24)).computedLimit(QuotaCountLimit.count(1589)).build();
    private static final Quota<QuotaSizeLimit, QuotaSizeUsage> STORAGE_QUOTA =
        Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(240)).computedLimit(QuotaSizeLimit.size(15890)).build();

    private GetQuotaRootProcessor testee;
    private FakeImapSession imapSession;
    private ImapProcessor.Responder mockedResponder;
    private QuotaManager mockedQuotaManager;
    private QuotaRootResolver mockedQuotaRootResolver;
    private MailboxManager mockedMailboxManager;
    private MailboxSession mailboxSession;

    @BeforeEach
    void setUp() throws Exception {
        mailboxSession = MailboxSessionUtil.create(PLOP);
        UnpooledStatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        imapSession = new FakeImapSession();
        mockedQuotaManager = mock(QuotaManager.class);
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
        mockedResponder = mock(ImapProcessor.Responder.class);
        mockedMailboxManager = mock(MailboxManager.class);
        when(mockedMailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> {
            Object[] args = invocation.getArguments();
            return (Mono) args[0];
        });
        MessageManager messageManager = mock(MessageManager.class);
        when(mockedMailboxManager.getMailboxReactive(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(Mono.just(messageManager));
        when(messageManager.getMailboxEntity()).thenReturn(mock(Mailbox.class));
        testee = new GetQuotaRootProcessor(mockedMailboxManager,
            statusResponseFactory, mockedQuotaRootResolver, mockedQuotaManager, new RecordingMetricFactory(), PathConverter.Factory.DEFAULT);
    }

    @Test
    void processorShouldWorkOnValidRights() throws Exception {
        GetQuotaRootRequest getQuotaRootRequest = new GetQuotaRootRequest(TAG, "INBOX");

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(mockedQuotaRootResolver.getQuotaRootReactive(MAILBOX_PATH)).thenReturn(Mono.just(QUOTA_ROOT));
        when(mockedQuotaRootResolver.retrieveAssociatedMailboxes(QUOTA_ROOT, mailboxSession)).thenReturn(Flux.just(mock(Mailbox.class)));
        when(mockedMailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Read), eq(mailboxSession))).thenReturn(true);
        when(mockedQuotaManager.getQuotasReactive(any(QuotaRoot.class)))
            .thenReturn(Mono.just(new QuotaManager.Quotas(MESSAGE_QUOTA, STORAGE_QUOTA)));

        final QuotaResponse storageQuotaResponse = new QuotaResponse("STORAGE", "plop", STORAGE_QUOTA);
        final QuotaResponse messageQuotaResponse = new QuotaResponse("MESSAGE", "plop", MESSAGE_QUOTA);
        final QuotaRootResponse quotaRootResponse = new QuotaRootResponse("INBOX", "plop");

        testee.doProcess(getQuotaRootRequest, mockedResponder, imapSession).block();

        verify(mockedMailboxManager).manageProcessing(any(), any());

        ArgumentCaptor<ImapResponseMessage> responseCaptor = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(mockedResponder, times(4)).respond(responseCaptor.capture());

        List<ImapResponseMessage> captorValues = responseCaptor.getAllValues();
        assertThat(captorValues).contains(quotaRootResponse, storageQuotaResponse, messageQuotaResponse);
        assertThat(captorValues).anySatisfy(response -> assertThat(response).isInstanceOfSatisfying(
            StatusResponse.class,
            st -> assertThat(st.getServerResponseType()).isEqualTo(OK)));
    }

    @Test
    void processorShouldWorkOnErrorThrown() throws Exception {
        GetQuotaRootRequest getQuotaRootRequest = new GetQuotaRootRequest(TAG, "INBOX");

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(mockedMailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Read), eq(mailboxSession))).thenThrow(new MailboxException());

        testee.doProcess(getQuotaRootRequest, mockedResponder, imapSession).block();

        verify(mockedMailboxManager).manageProcessing(any(), any());

        ArgumentCaptor<StatusResponse> responseCaptor = ArgumentCaptor.forClass(StatusResponse.class);
        verify(mockedResponder, only()).respond(responseCaptor.capture());

        assertThat(responseCaptor.getValue())
            .extracting(StatusResponse::getServerResponseType)
            .isEqualTo(StatusResponse.Type.NO);
    }

    @Test
    void processorShouldWorkOnNonValidRights() throws Exception {
        GetQuotaRootRequest getQuotaRootRequest = new GetQuotaRootRequest(TAG, "INBOX");

        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);
        when(mockedMailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.Read), eq(mailboxSession))).thenReturn(false);

        testee.doProcess(getQuotaRootRequest, mockedResponder, imapSession).block();

        verify(mockedMailboxManager).manageProcessing(any(), any());

        ArgumentCaptor<StatusResponse> responseCaptor = ArgumentCaptor.forClass(StatusResponse.class);
        verify(mockedResponder, only()).respond(responseCaptor.capture());

        assertThat(responseCaptor.getValue())
            .extracting(StatusResponse::getServerResponseType)
            .isEqualTo(StatusResponse.Type.NO);
    }
}
