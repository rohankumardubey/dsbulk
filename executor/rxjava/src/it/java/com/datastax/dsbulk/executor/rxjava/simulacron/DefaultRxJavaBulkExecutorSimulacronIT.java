/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.executor.rxjava.simulacron;

import com.datastax.driver.core.SerializedSession;
import com.datastax.driver.core.Session;
import com.datastax.dsbulk.executor.api.simulacron.BulkExecutorSimulacronITBase;
import com.datastax.dsbulk.executor.rxjava.DefaultRxJavaBulkExecutor;
import com.datastax.oss.simulacron.server.BoundCluster;

class DefaultRxJavaBulkExecutorSimulacronIT extends BulkExecutorSimulacronITBase {

  DefaultRxJavaBulkExecutorSimulacronIT(BoundCluster simulacron, Session session) {
    super(
        simulacron,
        session,
        DefaultRxJavaBulkExecutor.builder(new SerializedSession(session)).build(),
        DefaultRxJavaBulkExecutor.builder(new SerializedSession(session)).failSafe().build());
  }
}
