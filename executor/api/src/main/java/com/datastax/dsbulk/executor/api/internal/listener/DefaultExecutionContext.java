/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.executor.api.internal.listener;

import com.datastax.dsbulk.executor.api.listener.ExecutionContext;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import org.jctools.maps.NonBlockingHashMap;

public class DefaultExecutionContext implements ExecutionContext {

  private volatile ConcurrentMap<Object, Object> attributes = null;

  private volatile long start = -1;
  private volatile long end = -1;

  @Override
  public void setAttribute(Object key, Object value) {
    getAttributes().put(key, value);
  }

  @Override
  public Optional<Object> getAttribute(Object key) {
    return Optional.ofNullable(getAttributes().get(key));
  }

  @Override
  public long elapsedTimeNanos() {
    return start == -1 || end == -1 ? -1 : end - start;
  }

  public void start() {
    this.start = System.nanoTime();
  }

  public void stop() {
    this.end = System.nanoTime();
  }

  private ConcurrentMap<Object, Object> getAttributes() {
    // Double check locking
    if (attributes == null) {
      synchronized (this) {
        if (attributes == null) {
          @SuppressWarnings("UnnecessaryLocalVariable")
          ConcurrentMap<Object, Object> attributes = new NonBlockingHashMap<>();
          this.attributes = attributes;
        }
      }
    }
    return attributes;
  }
}
