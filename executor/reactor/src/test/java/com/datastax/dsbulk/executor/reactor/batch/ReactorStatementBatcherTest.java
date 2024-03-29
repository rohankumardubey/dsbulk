/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.executor.reactor.batch;

import static com.datastax.dsbulk.executor.api.batch.StatementBatcher.BatchMode.REPLICA_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.Statement;
import com.datastax.dsbulk.executor.api.batch.StatementBatcherTest;
import io.reactivex.Flowable;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ReactorStatementBatcherTest extends StatementBatcherTest {

  @Test
  void should_batch_by_routing_key_reactive() throws Exception {
    assignRoutingKeys();
    ReactorStatementBatcher batcher = new ReactorStatementBatcher();
    Flux<Statement> statements =
        Flux.from(batcher.batchByGroupingKey(Flux.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.collectList().block())
        .usingFieldByFieldElementComparator()
        .contains(batch126, batch34, stmt5);
  }

  @Test
  void should_batch_by_routing_token_reactive() throws Exception {
    assignRoutingTokens();
    ReactorStatementBatcher batcher = new ReactorStatementBatcher();
    Flux<Statement> statements =
        Flux.from(batcher.batchByGroupingKey(Flux.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.collectList().block())
        .usingFieldByFieldElementComparator()
        .contains(batch1256, batch34);
  }

  @Test
  void should_batch_by_replica_set_and_routing_key_reactive() throws Exception {
    assignRoutingKeys();
    Metadata metadata = mock(Metadata.class);
    when(cluster.getMetadata()).thenReturn(metadata);
    when(metadata.getReplicas("ks", key1)).thenReturn(replicaSet1);
    when(metadata.getReplicas("ks", key2)).thenReturn(replicaSet2);
    when(metadata.getReplicas("ks", key3)).thenReturn(replicaSet1);
    ReactorStatementBatcher batcher = new ReactorStatementBatcher(cluster, REPLICA_SET);
    Flux<Statement> statements =
        Flux.from(batcher.batchByGroupingKey(Flux.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.collectList().block())
        .usingFieldByFieldElementComparator()
        .contains(batch1256, batch34);
  }

  @Test
  void should_batch_by_replica_set_and_routing_token_reactive() throws Exception {
    assignRoutingTokens();
    Metadata metadata = mock(Metadata.class);
    when(cluster.getMetadata()).thenReturn(metadata);
    when(metadata.getReplicas("ks", key1)).thenReturn(replicaSet1);
    when(metadata.getReplicas("ks", key2)).thenReturn(replicaSet2);
    when(metadata.getReplicas("ks", key3)).thenReturn(replicaSet1);
    ReactorStatementBatcher batcher = new ReactorStatementBatcher(cluster, REPLICA_SET);
    Flux<Statement> statements =
        Flux.from(batcher.batchByGroupingKey(Flux.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.collectList().block())
        .usingFieldByFieldElementComparator()
        .contains(batch1256, batch34);
  }

  @Test
  void should_batch_by_routing_key_when_replica_set_info_not_available_reactive() throws Exception {
    assignRoutingKeys();
    Metadata metadata = mock(Metadata.class);
    when(cluster.getMetadata()).thenReturn(metadata);
    when(metadata.getReplicas("ks", key1)).thenReturn(new HashSet<>());
    when(metadata.getReplicas("ks", key2)).thenReturn(new HashSet<>());
    when(metadata.getReplicas("ks", key3)).thenReturn(new HashSet<>());
    ReactorStatementBatcher batcher = new ReactorStatementBatcher(cluster, REPLICA_SET);
    Flux<Statement> statements =
        Flux.from(batcher.batchByGroupingKey(Flux.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.collectList().block())
        .usingFieldByFieldElementComparator()
        .contains(batch126, batch34, stmt5);
  }

  @Test
  void should_batch_by_routing_token_when_replica_set_info_not_available_reactive()
      throws Exception {
    assignRoutingTokens();
    Metadata metadata = mock(Metadata.class);
    when(cluster.getMetadata()).thenReturn(metadata);
    when(metadata.getReplicas("ks", key1)).thenReturn(new HashSet<>());
    when(metadata.getReplicas("ks", key2)).thenReturn(new HashSet<>());
    when(metadata.getReplicas("ks", key3)).thenReturn(new HashSet<>());
    ReactorStatementBatcher batcher = new ReactorStatementBatcher(cluster, REPLICA_SET);
    Flux<Statement> statements =
        Flux.from(batcher.batchByGroupingKey(Flux.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.collectList().block())
        .usingFieldByFieldElementComparator()
        .contains(batch1256, batch34);
  }

  @Test
  void should_batch_all_reactive() throws Exception {
    ReactorStatementBatcher batcher = new ReactorStatementBatcher();
    Flux<Statement> statements =
        Flux.from(batcher.batchAll(Flux.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(((BatchStatement) statements.blockFirst()).getStatements())
        .containsExactly(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6);
  }

  @Test
  void should_honor_max_batch_size_reactive() throws Exception {
    assignRoutingTokens();
    ReactorStatementBatcher batcher = new ReactorStatementBatcher(2);
    Flowable<Statement> statements =
        Flowable.fromPublisher(
            batcher.batchByGroupingKey(Flowable.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.toList().blockingGet())
        .usingFieldByFieldElementComparator()
        .contains(batch12, batch56, batch34);
    statements =
        Flowable.fromPublisher(
            batcher.batchAll(Flowable.just(stmt1, stmt2, stmt3, stmt4, stmt5, stmt6)));
    assertThat(statements.toList().blockingGet())
        .usingFieldByFieldElementComparator()
        .contains(batch12, batch56, batch34);
  }
}
