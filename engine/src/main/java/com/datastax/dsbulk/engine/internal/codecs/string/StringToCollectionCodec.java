/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs.string;

import com.datastax.driver.core.exceptions.InvalidTypeException;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToCollectionCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public abstract class StringToCollectionCodec<E, C extends Collection<E>>
    extends StringConvertingCodec<C> {

  private final JsonNodeToCollectionCodec<E, C> jsonCodec;
  private final ObjectMapper objectMapper;

  StringToCollectionCodec(
      JsonNodeToCollectionCodec<E, C> jsonCodec,
      ObjectMapper objectMapper,
      List<String> nullStrings) {
    super(jsonCodec.getInternalCodec(), nullStrings);
    this.jsonCodec = jsonCodec;
    this.objectMapper = objectMapper;
  }

  @Override
  public C externalToInternal(String s) {
    if (isNullOrEmpty(s)) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(s);
      return jsonCodec.externalToInternal(node);
    } catch (IOException e) {
      throw new InvalidTypeException(String.format("Could not parse '%s' as Json", s), e);
    }
  }

  @Override
  public String internalToExternal(C collection) {
    if (collection == null) {
      return nullString();
    }
    try {
      JsonNode node = jsonCodec.internalToExternal(collection);
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new InvalidTypeException(String.format("Could not format '%s' to Json", collection), e);
    }
  }
}
