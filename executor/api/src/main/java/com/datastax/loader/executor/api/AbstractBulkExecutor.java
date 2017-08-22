/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.executor.api;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.datastax.driver.core.Session;
import com.datastax.loader.executor.api.listener.ExecutionListener;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Base class for implementations of {@link BulkExecutor}. */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public abstract class AbstractBulkExecutor implements BulkExecutor, AutoCloseable {

  /** The default number of maximum in-flight requests. */
  static final int DEFAULT_MAX_INFLIGHT_REQUESTS = 1_000;

  /** The default maximum number of concurrent requests per second. */
  static final int DEFAULT_MAX_REQUESTS_PER_SECOND = 100_000;

  final Session session;

  final boolean failFast;

  final Optional<Semaphore> requestPermits;

  final Optional<RateLimiter> rateLimiter;

  final Optional<ExecutionListener> listener;

  final Executor executor;

  AbstractBulkExecutor(Session session) {
    this(session, true, DEFAULT_MAX_INFLIGHT_REQUESTS, DEFAULT_MAX_REQUESTS_PER_SECOND, null, null);
  }

  AbstractBulkExecutor(
      Session session,
      boolean failFast,
      int maxInFlightRequests,
      int maxRequestsPerSecond,
      ExecutionListener listener,
      Executor executor) {
    this.session = session;
    this.failFast = failFast;
    this.requestPermits =
        maxInFlightRequests < 0
            ? Optional.empty()
            : Optional.of(new Semaphore(maxInFlightRequests));
    this.rateLimiter =
        maxRequestsPerSecond < 0
            ? Optional.empty()
            : Optional.of(RateLimiter.create(maxRequestsPerSecond));
    this.listener = Optional.ofNullable(listener);
    if (executor == null) {
      executor =
          new ThreadPoolExecutor(
              0,
              Runtime.getRuntime().availableProcessors() * 4,
              60,
              SECONDS,
              new SynchronousQueue<>(),
              new ThreadFactoryBuilder().setDaemon(true).setNameFormat("bulk-executor-%d").build(),
              new ThreadPoolExecutor.CallerRunsPolicy());
    }
    this.executor = executor;
  }

  @Override
  public void close() {
    if (executor instanceof ThreadPoolExecutor) {
      ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
      tpe.shutdown();
      try {
        tpe.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // swallow; best effort
      }
      tpe.shutdownNow();
    }
  }
}