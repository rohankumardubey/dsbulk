/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.executor.api.ccm;

import com.datastax.loader.executor.api.ContinuousReactorBulkExecutor;
import com.datastax.loader.tests.categories.LongTests;
import com.datastax.loader.tests.ccm.annotations.CCMTest;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

@CCMTest
@Category(LongTests.class)
public class ContinuousReactorBulkExecutorIT extends AbstractContinuousBulkExecutorIT {

  @BeforeClass
  public static void createBulkExecutors() {
    failFastExecutor = ContinuousReactorBulkExecutor.builder(session).build();
    failSafeExecutor = ContinuousReactorBulkExecutor.builder(session).failSafe().build();
  }
}
