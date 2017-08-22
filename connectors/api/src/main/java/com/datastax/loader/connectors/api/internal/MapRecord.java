/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.connectors.api.internal;

import com.datastax.loader.connectors.api.Record;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Streams;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/** */
public class MapRecord extends LinkedHashMap<String, Object> implements Record {

  private final Object source;
  private final Supplier<URI> location;

  public MapRecord(Object source, Supplier<URI> location, Object... values) {
    super();
    this.source = source;
    this.location = location;
    Streams.forEachPair(
        IntStream.range(0, values.length).boxed().map(Object::toString),
        Arrays.stream(values),
        this::put);
  }

  public MapRecord(Object source, Supplier<URI> location, String[] keys, Object[] values) {
    this(source, location, values);
    if (keys.length != values.length)
      throw new IllegalArgumentException("Keys and values have different sizes");
    Streams.forEachPair(Arrays.stream(keys), Arrays.stream(values), this::put);
  }

  @Override
  public Object getSource() {
    return source;
  }

  @Override
  public URI getLocation() {
    return location.get();
  }

  @Override
  public Set<String> fields() {
    return keySet();
  }

  @Override
  public Object getFieldValue(String field) {
    return get(field);
  }

  /**
   * Sets the value associated with the given field.
   *
   * @param field the field name.
   * @param value The value to set.
   */
  public void setFieldValue(String field, Object value) {
    put(field, value);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("source", source)
        .add("location", location)
        .add("entries", entrySet())
        .toString();
  }
}
