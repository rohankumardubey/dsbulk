/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs.number;

import com.datastax.driver.extras.codecs.jdk8.InstantCodec;
import com.datastax.dsbulk.engine.internal.codecs.ConvertingCodec;
import com.datastax.dsbulk.engine.internal.codecs.util.CodecUtils;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public class NumberToInstantCodec<EXTERNAL extends Number>
    extends ConvertingCodec<EXTERNAL, Instant> {

  private final TimeUnit timeUnit;
  private final ZonedDateTime epoch;

  public NumberToInstantCodec(Class<EXTERNAL> javaType, TimeUnit timeUnit, ZonedDateTime epoch) {
    super(InstantCodec.instance, javaType);
    this.timeUnit = timeUnit;
    this.epoch = epoch;
  }

  @Override
  public EXTERNAL internalToExternal(Instant value) {
    if (value == null) {
      return null;
    }
    long timestamp = CodecUtils.instantToNumber(value, timeUnit, epoch.toInstant());
    @SuppressWarnings("unchecked")
    EXTERNAL n = CodecUtils.convertNumber(timestamp, (Class<EXTERNAL>) getJavaType().getRawType());
    return n;
  }

  @Override
  public Instant externalToInternal(EXTERNAL value) {
    if (value == null) {
      return null;
    }
    return CodecUtils.numberToInstant(value, timeUnit, epoch.toInstant());
  }
}
