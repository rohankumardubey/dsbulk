/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.executor.reactor.tck;

import static com.datastax.dsbulk.executor.api.tck.ReadResultPublisherTestBase.FAILED_LISTENER;
import static org.mockito.Mockito.mock;

import com.datastax.driver.core.ContinuousPagingSession;
import com.datastax.dsbulk.executor.api.result.ReadResult;
import com.datastax.dsbulk.executor.api.tck.ContinuousReadResultPublisherTestBase;
import com.datastax.dsbulk.executor.reactor.ContinuousReactorBulkExecutor;
import org.reactivestreams.Publisher;

public class ContinuousReadResultPublisherTest extends ContinuousReadResultPublisherTestBase {

  @Override
  public Publisher<ReadResult> createPublisher(long elements) {
    ContinuousReactorBulkExecutor executor =
        new ContinuousReactorBulkExecutor(setUpSuccessfulSession(elements));
    return executor.readReactive("irrelevant");
  }

  @Override
  public Publisher<ReadResult> createFailedPublisher() {
    ContinuousReactorBulkExecutor executor =
        ContinuousReactorBulkExecutor.builder(mock(ContinuousPagingSession.class))
            .withExecutionListener(FAILED_LISTENER)
            .build();
    return executor.readReactive("irrelevant");
  }
}
