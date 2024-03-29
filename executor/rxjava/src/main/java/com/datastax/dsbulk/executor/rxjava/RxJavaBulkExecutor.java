/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.executor.rxjava;

import com.datastax.dsbulk.executor.api.BulkExecutor;
import com.datastax.dsbulk.executor.rxjava.reader.RxJavaBulkReader;
import com.datastax.dsbulk.executor.rxjava.writer.RxJavaBulkWriter;

/**
 * An execution unit for {@link RxJavaBulkWriter bulk writes} and {@link RxJavaBulkReader bulk
 * reads} that operates in reactive mode using <a
 * href="https://github.com/ReactiveX/RxJava/wiki">RxJava</a>.
 */
public interface RxJavaBulkExecutor extends RxJavaBulkWriter, RxJavaBulkReader, BulkExecutor {}
