/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.schema;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.exceptions.CodecNotFoundException;
import com.google.common.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a bidirectional, one-to-one relationship between record fields and CQL columns.
 *
 * <p>In write workflows, CQL columns correspond to bound variables in the write statement. In read
 * workflows, CQL columns correspond to row variables in a read result.
 */
public interface Mapping {

  /**
   * Maps the given field to a bound statement variable. Used in write workflows.
   *
   * <p>Note that the returned name is never quoted, even if it requires quoting to conform with the
   * syntax of CQL identifiers; it is the caller's responsibility to check if quoting is required or
   * not.
   *
   * @param field the field name.
   * @return the bound statement variable name the given field maps to, or {@code null} if the field
   *     does not map to any known bound statement variable.
   */
  @Nullable
  String fieldToVariable(@NotNull String field);

  /**
   * Maps the given row variable to a field. Used in read workflows.
   *
   * <p>Note that the given variable name must be supplied unquoted, even if it requires quoting to
   * comply with the syntax of CQL identifiers.
   *
   * @param variable the row variable name; never {@code null}.
   * @return the field name the given variable maps to, or {@code null} if the variable does not map
   *     to any known field.
   */
  @Nullable
  String variableToField(@NotNull String variable);

  /**
   * Returns the codec to use for the given bound statement or row variable.
   *
   * <p>Note that the given variable name must be supplied unquoted, even if it requires quoting to
   * comply with the syntax of CQL identifiers.
   *
   * @param <T> the codec's Java type.
   * @param variable the bound statement or row variable name; never {@code null}.
   * @param cqlType the CQL type; never {@code null}.
   * @param javaType the Java type; never {@code null}.
   * @return the codec to use; never {@code null}.
   * @throws CodecNotFoundException if a suitable codec cannot be found.
   */
  @NotNull
  <T> TypeCodec<T> codec(
      @NotNull String variable, @NotNull DataType cqlType, @NotNull TypeToken<? extends T> javaType)
      throws CodecNotFoundException;
}
