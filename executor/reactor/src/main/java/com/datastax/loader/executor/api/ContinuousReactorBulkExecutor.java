/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.executor.api;

import com.datastax.driver.core.ContinuousPagingOptions;
import com.datastax.driver.core.ContinuousPagingSession;
import com.datastax.driver.core.Statement;
import com.datastax.loader.executor.api.internal.ContinuousReadResultPublisher;
import com.datastax.loader.executor.api.listener.ExecutionListener;
import com.datastax.loader.executor.api.result.ReadResult;
import java.util.Objects;
import java.util.concurrent.Executor;
import reactor.core.publisher.Flux;

/**
 * An implementation of {@link BulkExecutor} using <a href="https://projectreactor.io">Reactor</a>,
 * that executes all reads using continuous paging. This executor can achieve significant
 * performance improvements for reads, provided that the read statements to be executed can be
 * properly routed to a replica.
 */
public class ContinuousReactorBulkExecutor extends DefaultReactorBulkExecutor
    implements ReactorBulkExecutor {

  /**
   * Creates a new builder for {@link ContinuousReactorBulkExecutor} instances using the given
   * {@link ContinuousPagingSession}.
   *
   * @param session the {@link ContinuousPagingSession} to use.
   * @return a new builder.
   */
  public static ContinuousReactorBulkExecutorBuilder builder(ContinuousPagingSession session) {
    return new ContinuousReactorBulkExecutorBuilder(session);
  }

  private final ContinuousPagingSession session;
  private final ContinuousPagingOptions options;

  /**
   * Creates a new instance using the given {@link ContinuousPagingSession} and using defaults for
   * all parameters.
   *
   * <p>If you need to customize your executor, use the {@link #builder(ContinuousPagingSession)
   * builder} method instead.
   *
   * @param session the {@link ContinuousPagingSession} to use.
   */
  public ContinuousReactorBulkExecutor(ContinuousPagingSession session) {
    this(session, ContinuousPagingOptions.builder().build());
  }

  /**
   * Creates a new instance using the given {@link ContinuousPagingSession}, the given {@link
   * ContinuousPagingOptions}, and using defaults for all other parameters.
   *
   * <p>If you need to customize your executor, use the {@link #builder(ContinuousPagingSession)
   * builder} method instead.
   *
   * @param session the {@link ContinuousPagingSession} to use.
   * @param options the {@link ContinuousPagingOptions} to use.
   */
  public ContinuousReactorBulkExecutor(
      ContinuousPagingSession session, ContinuousPagingOptions options) {
    super(session);
    this.session = session;
    this.options = options;
  }

  ContinuousReactorBulkExecutor(
      ContinuousPagingSession session,
      ContinuousPagingOptions options,
      boolean failFast,
      int maxInFlightRequests,
      int maxRequestsPerSecond,
      ExecutionListener listener,
      Executor executor) {
    super(session, failFast, maxInFlightRequests, maxRequestsPerSecond, listener, executor);
    this.session = session;
    this.options = options;
  }

  @Override
  public Flux<ReadResult> readReactive(Statement statement) {
    Objects.requireNonNull(statement);
    return Flux.from(
        new ContinuousReadResultPublisher(
            statement,
            session,
            options,
            executor,
            listener,
            rateLimiter,
            requestPermits,
            failFast));
  }
}