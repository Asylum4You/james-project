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

package org.apache.james.jmap.cassandra.change;

import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.ASC;
import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.ACCOUNT_ID;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.CREATED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.DATE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.DESTROYED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.IS_COUNT_CHANGE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.IS_DELEGATED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.STATE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.TABLE_NAME;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.UPDATED;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeDataDefinition;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.jmap.api.change.MailboxChange;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MailboxId;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.internal.core.type.codec.BooleanCodec;
import com.datastax.oss.driver.internal.querybuilder.DefaultLiteral;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxChangeRepositoryDAO {
    private static final TypeCodec<Set<UUID>> SET_OF_UUIDS_CODEC = CodecRegistry.DEFAULT.codecFor(DataTypes.frozenSetOf(DataTypes.UUID), GenericType.setOf(UUID.class));
    private static final CqlIdentifier TTL_FOR_ROW = CqlIdentifier.fromCql("ttl");

    private final CassandraAsyncExecutor executor;
    private final UserDefinedType zonedDateTimeUserType;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectAllStatement;
    private final PreparedStatement selectFromStatement;
    private final PreparedStatement selectLatestStatement;
    private final PreparedStatement selectLatestNotDelegatedStatement;
    private final int timeToLive;

    @Inject
    public MailboxChangeRepositoryDAO(CqlSession session, CassandraTypesProvider cassandraTypesProvider,
                                      CassandraChangesConfiguration cassandraChangesConfiguration) {
        executor = new CassandraAsyncExecutor(session);
        zonedDateTimeUserType = cassandraTypesProvider.getDefinedUserType(CassandraZonedDateTimeDataDefinition.ZONED_DATE_TIME);

        insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(ACCOUNT_ID, bindMarker(ACCOUNT_ID))
            .value(STATE, bindMarker(STATE))
            .value(DATE, bindMarker(DATE))
            .value(IS_DELEGATED, bindMarker(IS_DELEGATED))
            .value(IS_COUNT_CHANGE, bindMarker(IS_COUNT_CHANGE))
            .value(CREATED, bindMarker(CREATED))
            .value(UPDATED, bindMarker(UPDATED))
            .value(DESTROYED, bindMarker(DESTROYED))
            .usingTtl(bindMarker(TTL_FOR_ROW))
            .build());

        selectAllStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .orderBy(STATE, ASC)
            .build());

        selectFromStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .whereColumn(STATE).isGreaterThanOrEqualTo(bindMarker(STATE))
            .orderBy(STATE, ASC)
            .build());

        selectLatestStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(STATE)
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .orderBy(STATE, DESC)
            .limit(1)
            .build());

        selectLatestNotDelegatedStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(STATE)
            .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
            .whereColumn(IS_DELEGATED).isEqualTo(new DefaultLiteral<>(false, new BooleanCodec()))
            .orderBy(STATE, DESC)
            .limit(1)
            .allowFiltering()
            .build());

        timeToLive = Math.toIntExact(cassandraChangesConfiguration.getMailboxChangeTtl().getSeconds());
    }

    Mono<Void> insert(MailboxChange change) {
        return executor.executeVoid(insertStatement.bind()
            .setString(ACCOUNT_ID, change.getAccountId().getIdentifier())
            .setUuid(STATE, change.getState().getValue())
            .setBoolean(IS_COUNT_CHANGE, change.isCountChange())
            .setBoolean(IS_DELEGATED, change.isShared())
            .set(CREATED, toUuidSet(change.getCreated()), SET_OF_UUIDS_CODEC)
            .set(UPDATED, toUuidSet(change.getUpdated()), SET_OF_UUIDS_CODEC)
            .set(DESTROYED, toUuidSet(change.getDestroyed()), SET_OF_UUIDS_CODEC)
            .setUdtValue(DATE, CassandraZonedDateTimeDataDefinition.toUDT(zonedDateTimeUserType, change.getDate()))
            .setInt(TTL_FOR_ROW, timeToLive));
    }

    private ImmutableSet<UUID> toUuidSet(List<MailboxId> idSet) {
        return idSet.stream()
            .filter(CassandraId.class::isInstance)
            .map(CassandraId.class::cast)
            .map(CassandraId::asUuid)
            .collect(ImmutableSet.toImmutableSet());
    }

    Flux<MailboxChange> getAllChanges(AccountId accountId) {
        return executor.executeRows(selectAllStatement.bind()
            .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT))
            .map(row -> readRow(row, accountId));
    }

    Flux<MailboxChange> getChangesSince(AccountId accountId, State state) {
        return executor.executeRows(selectFromStatement.bind()
                .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT)
                .setUuid(STATE, state.getValue()))
            .map(row -> readRow(row, accountId));
    }

    Mono<State> latestState(AccountId accountId) {
        return executor.executeSingleRow(selectLatestStatement.bind()
            .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT))
            .map(row -> State.of(row.getUuid(0)));
    }

    Mono<State> latestStateNotDelegated(AccountId accountId) {
        return executor.executeSingleRow(selectLatestNotDelegatedStatement.bind()
            .set(ACCOUNT_ID, accountId.getIdentifier(), TypeCodecs.TEXT))
            .map(row -> State.of(row.getUuid(0)));
    }

    private MailboxChange readRow(Row row, AccountId accountId) {
        return MailboxChange.builder()
            .accountId(accountId)
            .state(State.of(row.getUuid(STATE)))
            .date(CassandraZonedDateTimeDataDefinition.fromUDT(row.getUdtValue(DATE)))
            .isCountChange(row.getBoolean(IS_COUNT_CHANGE))
            .shared(row.getBoolean(IS_DELEGATED))
            .created(toIdSet(row.get(CREATED, SET_OF_UUIDS_CODEC)))
            .updated(toIdSet(row.get(UPDATED, SET_OF_UUIDS_CODEC)))
            .destroyed(toIdSet(row.get(DESTROYED, SET_OF_UUIDS_CODEC)))
            .build();
    }

    private ImmutableList<MailboxId> toIdSet(Set<UUID> uuidSet) {
        return uuidSet.stream()
            .map(CassandraId::of)
            .collect(ImmutableList.toImmutableList());
    }
}
