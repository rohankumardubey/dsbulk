/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs.number;

import com.datastax.driver.core.TypeCodec;
import com.datastax.dsbulk.engine.internal.codecs.ConvertingCodec;
import com.datastax.dsbulk.engine.internal.codecs.util.CodecUtils;

public class NumberToNumberCodec<EXTERNAL extends Number, INTERNAL extends Number>
    extends ConvertingCodec<EXTERNAL, INTERNAL> {

  public NumberToNumberCodec(Class<EXTERNAL> javaType, TypeCodec<INTERNAL> targetCodec) {
    super(targetCodec, javaType);
  }

  @SuppressWarnings("unchecked")
  @Override
  public EXTERNAL internalToExternal(INTERNAL value) {
    return CodecUtils.convertNumber(value, (Class<EXTERNAL>) getJavaType().getRawType());
  }

  @SuppressWarnings("unchecked")
  @Override
  public INTERNAL externalToInternal(EXTERNAL value) {
    return CodecUtils.convertNumber(
        value, (Class<INTERNAL>) internalCodec.getJavaType().getRawType());
  }
}
