/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs.json;

import static com.datastax.dsbulk.engine.internal.settings.CodecSettings.JSON_NODE_FACTORY;

import com.datastax.driver.core.TypeCodec;
import com.datastax.dsbulk.engine.internal.codecs.util.CodecUtils;
import com.datastax.dsbulk.engine.internal.codecs.util.OverflowStrategy;
import com.datastax.dsbulk.engine.internal.codecs.util.TemporalFormat;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.util.concurrent.FastThreadLocal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JsonNodeToBigDecimalCodec extends JsonNodeToNumberCodec<BigDecimal> {

  public JsonNodeToBigDecimalCodec(
      FastThreadLocal<NumberFormat> numberFormat,
      OverflowStrategy overflowStrategy,
      RoundingMode roundingMode,
      TemporalFormat temporalFormat,
      ZoneId timeZone,
      TimeUnit timeUnit,
      ZonedDateTime epoch,
      Map<String, Boolean> booleanStrings,
      List<BigDecimal> booleanNumbers,
      List<String> nullStrings) {
    super(
        TypeCodec.decimal(),
        numberFormat,
        overflowStrategy,
        roundingMode,
        temporalFormat,
        timeZone,
        timeUnit,
        epoch,
        booleanStrings,
        booleanNumbers,
        nullStrings);
  }

  @Override
  public BigDecimal externalToInternal(JsonNode node) {
    if (isNullOrEmpty(node)) {
      return null;
    }
    if (node.isBigDecimal()) {
      return node.decimalValue();
    }
    Number number;
    if (node.isNumber()) {
      number = node.numberValue();
    } else {
      number = parseNumber(node);
    }
    if (number == null) {
      return null;
    }
    return CodecUtils.toBigDecimal(number);
  }

  @Override
  public JsonNode internalToExternal(BigDecimal value) {
    return value == null ? null : JSON_NODE_FACTORY.numberNode(value);
  }
}
