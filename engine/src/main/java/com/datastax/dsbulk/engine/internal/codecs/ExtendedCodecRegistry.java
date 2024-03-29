/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.TupleType;
import com.datastax.driver.core.TupleValue;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;
import com.datastax.driver.core.exceptions.CodecNotFoundException;
import com.datastax.driver.dse.geometry.codecs.LineStringCodec;
import com.datastax.driver.dse.geometry.codecs.PointCodec;
import com.datastax.driver.dse.geometry.codecs.PolygonCodec;
import com.datastax.driver.extras.codecs.jdk8.InstantCodec;
import com.datastax.driver.extras.codecs.jdk8.LocalDateCodec;
import com.datastax.driver.extras.codecs.jdk8.LocalTimeCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToBigDecimalCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToBigIntegerCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToBlobCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToBooleanCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToByteCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToDoubleCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToDurationCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToFloatCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToInetAddressCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToInstantCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToIntegerCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToListCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToLocalDateCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToLocalTimeCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToLongCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToMapCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToSetCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToShortCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToStringCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToTupleCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToUDTCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToUUIDCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.JsonNodeToUnknownTypeCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.dse.JsonNodeToDateRangeCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.dse.JsonNodeToLineStringCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.dse.JsonNodeToPointCodec;
import com.datastax.dsbulk.engine.internal.codecs.json.dse.JsonNodeToPolygonCodec;
import com.datastax.dsbulk.engine.internal.codecs.number.BooleanToNumberCodec;
import com.datastax.dsbulk.engine.internal.codecs.number.NumberToBooleanCodec;
import com.datastax.dsbulk.engine.internal.codecs.number.NumberToInstantCodec;
import com.datastax.dsbulk.engine.internal.codecs.number.NumberToNumberCodec;
import com.datastax.dsbulk.engine.internal.codecs.number.NumberToUUIDCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToBigDecimalCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToBigIntegerCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToBlobCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToBooleanCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToByteCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToDoubleCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToDurationCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToFloatCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToInetAddressCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToInstantCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToIntegerCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToListCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToLocalDateCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToLocalTimeCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToLongCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToMapCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToSetCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToShortCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToStringCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToTupleCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToUDTCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToUUIDCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.StringToUnknownTypeCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.dse.StringToDateRangeCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.dse.StringToLineStringCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.dse.StringToPointCodec;
import com.datastax.dsbulk.engine.internal.codecs.string.dse.StringToPolygonCodec;
import com.datastax.dsbulk.engine.internal.codecs.temporal.DateToTemporalCodec;
import com.datastax.dsbulk.engine.internal.codecs.temporal.DateToUUIDCodec;
import com.datastax.dsbulk.engine.internal.codecs.temporal.TemporalToTemporalCodec;
import com.datastax.dsbulk.engine.internal.codecs.temporal.TemporalToUUIDCodec;
import com.datastax.dsbulk.engine.internal.codecs.util.OverflowStrategy;
import com.datastax.dsbulk.engine.internal.codecs.util.TemporalFormat;
import com.datastax.dsbulk.engine.internal.codecs.util.TimeUUIDGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import io.netty.util.concurrent.FastThreadLocal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If only CodecRegistry were extensible :(
 *
 * <p>This class helps solve the following problem: how to create codecs for combinations of Java
 * types + CQL types that the original CodecRegistry cannot handle?
 */
public class ExtendedCodecRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCodecRegistry.class);
  private static final TypeToken<String> STRING_TYPE_TOKEN = TypeToken.of(String.class);
  private static final TypeToken<JsonNode> JSON_NODE_TYPE_TOKEN = TypeToken.of(JsonNode.class);
  private static final String DATE_RANGE_CLASS_NAME =
      "org.apache.cassandra.db.marshal.DateRangeType";

  private final CodecRegistry codecRegistry;
  private final List<String> nullStrings;
  private final Map<String, Boolean> booleanInputWords;
  private final Map<Boolean, String> booleanOutputWords;
  private final List<BigDecimal> booleanNumbers;
  private final FastThreadLocal<NumberFormat> numberFormat;
  private final OverflowStrategy overflowStrategy;
  private final RoundingMode roundingMode;
  private final TemporalFormat localDateFormat;
  private final TemporalFormat localTimeFormat;
  private final TemporalFormat timestampFormat;
  private final ZoneId timeZone;
  private final TimeUnit timeUnit;
  private final ZonedDateTime epoch;
  private final ObjectMapper objectMapper;
  private final TimeUUIDGenerator generator;

  public ExtendedCodecRegistry(
      CodecRegistry codecRegistry,
      List<String> nullStrings,
      Map<String, Boolean> booleanInputWords,
      Map<Boolean, String> booleanOutputWords,
      List<BigDecimal> booleanNumbers,
      FastThreadLocal<NumberFormat> numberFormat,
      OverflowStrategy overflowStrategy,
      RoundingMode roundingMode,
      TemporalFormat localDateFormat,
      TemporalFormat localTimeFormat,
      TemporalFormat timestampFormat,
      ZoneId timeZone,
      TimeUnit timeUnit,
      ZonedDateTime epoch,
      TimeUUIDGenerator generator,
      ObjectMapper objectMapper) {
    this.codecRegistry = codecRegistry;
    this.nullStrings = nullStrings;
    this.booleanInputWords = booleanInputWords;
    this.booleanOutputWords = booleanOutputWords;
    this.booleanNumbers = booleanNumbers;
    this.numberFormat = numberFormat;
    this.overflowStrategy = overflowStrategy;
    this.roundingMode = roundingMode;
    this.localDateFormat = localDateFormat;
    this.localTimeFormat = localTimeFormat;
    this.timestampFormat = timestampFormat;
    this.timeZone = timeZone;
    this.timeUnit = timeUnit;
    this.epoch = epoch;
    this.generator = generator;
    this.objectMapper = objectMapper;
    // register Java Time API codecs
    codecRegistry.register(LocalDateCodec.instance, LocalTimeCodec.instance, InstantCodec.instance);
  }

  @SuppressWarnings("unchecked")
  public <T> TypeCodec<T> codecFor(
      @NotNull DataType cqlType, @NotNull TypeToken<? extends T> javaType) {
    // Implementation note: it's not required to cache codecs created on-the-fly by this method
    // as caching is meant to be handled by the caller, see
    // com.datastax.dsbulk.engine.internal.schema.DefaultMapping.codec()
    TypeCodec<T> codec;
    try {
      if (javaType.getRawType().equals(String.class)) {
        // Never return the driver's built-in StringCodec because it does not handle
        // null words. We need StringToStringCodec here.
        codec = (TypeCodec<T>) createStringConvertingCodec(cqlType, true);
      } else {
        codec = (TypeCodec<T>) codecRegistry.codecFor(cqlType, javaType);
      }
    } catch (CodecNotFoundException e) {
      codec = (TypeCodec<T>) maybeCreateConvertingCodec(cqlType, javaType);
      if (codec == null) {
        throw e;
      }
    }
    return codec;
  }

  @SuppressWarnings("unchecked")
  public <EXTERNAL, INTERNAL> ConvertingCodec<EXTERNAL, INTERNAL> convertingCodecFor(
      @NotNull DataType cqlType, @NotNull TypeToken<? extends EXTERNAL> javaType) {
    ConvertingCodec<EXTERNAL, INTERNAL> codec =
        (ConvertingCodec<EXTERNAL, INTERNAL>) maybeCreateConvertingCodec(cqlType, javaType);
    if (codec != null) {
      return codec;
    }
    throw new CodecNotFoundException(
        String.format(
            "ConvertingCodec not found for requested operation: [%s <-> %s]", cqlType, javaType),
        cqlType,
        javaType);
  }

  @Nullable
  private ConvertingCodec<?, ?> maybeCreateConvertingCodec(
      @NotNull DataType cqlType, @NotNull TypeToken<?> javaType) {
    if (String.class.equals(javaType.getRawType())) {
      return createStringConvertingCodec(cqlType, true);
    }
    if (JsonNode.class.equals(javaType.getRawType())) {
      return createJsonNodeConvertingCodec(cqlType, true);
    }
    if (Number.class.isAssignableFrom(javaType.getRawType()) && isNumeric(cqlType)) {
      @SuppressWarnings("unchecked")
      Class<Number> numberType = (Class<Number>) javaType.getRawType();
      @SuppressWarnings("unchecked")
      TypeCodec<Number> typeCodec = (TypeCodec<Number>) codecFor(cqlType);
      return new NumberToNumberCodec<>(numberType, typeCodec);
    }
    if (Number.class.isAssignableFrom(javaType.getRawType()) && cqlType == DataType.timestamp()) {
      @SuppressWarnings("unchecked")
      Class<Number> numberType = (Class<Number>) javaType.getRawType();
      return new NumberToInstantCodec<>(numberType, timeUnit, epoch);
    }
    if (Number.class.isAssignableFrom(javaType.getRawType()) && isUUID(cqlType)) {
      @SuppressWarnings("unchecked")
      TypeCodec<UUID> uuidCodec = (TypeCodec<UUID>) codecFor(cqlType);
      @SuppressWarnings("unchecked")
      Class<Number> numberType = (Class<Number>) javaType.getRawType();
      NumberToInstantCodec<Number> instantCodec =
          new NumberToInstantCodec<>(numberType, timeUnit, epoch);
      return new NumberToUUIDCodec<>(uuidCodec, instantCodec, generator);
    }
    if (Number.class.isAssignableFrom(javaType.getRawType()) && cqlType == DataType.cboolean()) {
      @SuppressWarnings("unchecked")
      Class<Number> numberType = (Class<Number>) javaType.getRawType();
      return new NumberToBooleanCodec<>(numberType, booleanNumbers);
    }
    if (Temporal.class.isAssignableFrom(javaType.getRawType()) && isTemporal(cqlType)) {
      @SuppressWarnings("unchecked")
      Class<Temporal> fromTemporalType = (Class<Temporal>) javaType.getRawType();
      if (cqlType == DataType.date()) {
        return new TemporalToTemporalCodec<>(
            fromTemporalType, LocalDateCodec.instance, timeZone, epoch);
      }
      if (cqlType == DataType.time()) {
        return new TemporalToTemporalCodec<>(
            fromTemporalType, LocalTimeCodec.instance, timeZone, epoch);
      }
      if (cqlType == DataType.timestamp()) {
        return new TemporalToTemporalCodec<>(
            fromTemporalType, InstantCodec.instance, timeZone, epoch);
      }
    }
    if (Temporal.class.isAssignableFrom(javaType.getRawType()) && isUUID(cqlType)) {
      @SuppressWarnings("unchecked")
      TypeCodec<UUID> uuidCodec = (TypeCodec<UUID>) codecFor(cqlType);
      @SuppressWarnings("unchecked")
      TemporalToTemporalCodec<TemporalAccessor, Instant> instantCodec =
          (TemporalToTemporalCodec<TemporalAccessor, Instant>)
              maybeCreateConvertingCodec(DataType.timestamp(), javaType);
      assert instantCodec != null;
      return new TemporalToUUIDCodec<>(uuidCodec, instantCodec, generator);
    }
    if (Date.class.isAssignableFrom(javaType.getRawType()) && isTemporal(cqlType)) {
      if (cqlType == DataType.date()) {
        return new DateToTemporalCodec<>(Date.class, LocalDateCodec.instance, timeZone);
      }
      if (cqlType == DataType.time()) {
        return new DateToTemporalCodec<>(Date.class, LocalTimeCodec.instance, timeZone);
      }
      if (cqlType == DataType.timestamp()) {
        return new DateToTemporalCodec<>(Date.class, InstantCodec.instance, timeZone);
      }
    }
    if (Date.class.isAssignableFrom(javaType.getRawType()) && isUUID(cqlType)) {
      @SuppressWarnings("unchecked")
      TypeCodec<UUID> uuidCodec = (TypeCodec<UUID>) codecFor(cqlType);
      @SuppressWarnings("unchecked")
      DateToTemporalCodec<Date, Instant> instantCodec =
          (DateToTemporalCodec<Date, Instant>)
              maybeCreateConvertingCodec(DataType.timestamp(), javaType);
      assert instantCodec != null;
      return new DateToUUIDCodec<>(uuidCodec, instantCodec, generator);
    }
    if (Boolean.class.isAssignableFrom(javaType.getRawType()) && isNumeric(cqlType)) {
      @SuppressWarnings("unchecked")
      TypeCodec<Number> typeCodec = (TypeCodec<Number>) codecFor(cqlType);
      return new BooleanToNumberCodec<>(typeCodec, booleanNumbers);
    }
    return null;
  }

  private ConvertingCodec<String, ?> createStringConvertingCodec(
      @NotNull DataType cqlType, boolean rootCodec) {
    // Don't apply null strings for non-root codecs
    List<String> nullStrings = rootCodec ? this.nullStrings : ImmutableList.of();
    DataType.Name name = cqlType.getName();
    switch (name) {
      case ASCII:
      case TEXT:
      case VARCHAR:
        @SuppressWarnings("unchecked")
        TypeCodec<String> typeCodec = (TypeCodec<String>) codecFor(cqlType);
        return new StringToStringCodec(typeCodec, nullStrings);
      case BOOLEAN:
        return new StringToBooleanCodec(booleanInputWords, booleanOutputWords, nullStrings);
      case TINYINT:
        return new StringToByteCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case SMALLINT:
        return new StringToShortCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case INT:
        return new StringToIntegerCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case BIGINT:
        return new StringToLongCodec(
            TypeCodec.bigint(),
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case COUNTER:
        return new StringToLongCodec(
            TypeCodec.counter(),
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case FLOAT:
        return new StringToFloatCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case DOUBLE:
        return new StringToDoubleCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case VARINT:
        return new StringToBigIntegerCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case DECIMAL:
        return new StringToBigDecimalCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case DATE:
        return new StringToLocalDateCodec(localDateFormat, timeZone, nullStrings);
      case TIME:
        return new StringToLocalTimeCodec(localTimeFormat, timeZone, nullStrings);
      case TIMESTAMP:
        return new StringToInstantCodec(
            timestampFormat, numberFormat, timeZone, timeUnit, epoch, nullStrings);
      case INET:
        return new StringToInetAddressCodec(nullStrings);
      case UUID:
        {
          @SuppressWarnings("unchecked")
          ConvertingCodec<String, Instant> instantCodec =
              (ConvertingCodec<String, Instant>)
                  createStringConvertingCodec(DataType.timestamp(), false);
          return new StringToUUIDCodec(TypeCodec.uuid(), instantCodec, generator, nullStrings);
        }
      case TIMEUUID:
        {
          @SuppressWarnings("unchecked")
          ConvertingCodec<String, Instant> instantCodec =
              (ConvertingCodec<String, Instant>)
                  createStringConvertingCodec(DataType.timestamp(), false);
          return new StringToUUIDCodec(TypeCodec.timeUUID(), instantCodec, generator, nullStrings);
        }
      case BLOB:
        return new StringToBlobCodec(nullStrings);
      case DURATION:
        return new StringToDurationCodec(nullStrings);
      case LIST:
        {
          @SuppressWarnings("unchecked")
          JsonNodeToListCodec<Object> jsonCodec =
              (JsonNodeToListCodec<Object>) createJsonNodeConvertingCodec(cqlType, false);
          return new StringToListCodec<>(jsonCodec, objectMapper, nullStrings);
        }
      case SET:
        {
          @SuppressWarnings("unchecked")
          JsonNodeToSetCodec<Object> jsonCodec =
              (JsonNodeToSetCodec<Object>) createJsonNodeConvertingCodec(cqlType, false);
          return new StringToSetCodec<>(jsonCodec, objectMapper, nullStrings);
        }
      case MAP:
        {
          @SuppressWarnings("unchecked")
          JsonNodeToMapCodec<Object, Object> jsonCodec =
              (JsonNodeToMapCodec<Object, Object>) createJsonNodeConvertingCodec(cqlType, false);
          return new StringToMapCodec<>(jsonCodec, objectMapper, nullStrings);
        }
      case TUPLE:
        {
          JsonNodeToTupleCodec jsonCodec =
              (JsonNodeToTupleCodec) createJsonNodeConvertingCodec(cqlType, false);
          return new StringToTupleCodec(jsonCodec, objectMapper, nullStrings);
        }
      case UDT:
        {
          JsonNodeToUDTCodec jsonCodec =
              (JsonNodeToUDTCodec) createJsonNodeConvertingCodec(cqlType, false);
          return new StringToUDTCodec(jsonCodec, objectMapper, nullStrings);
        }
      case CUSTOM:
        {
          DataType.CustomType customType = (DataType.CustomType) cqlType;
          switch (customType.getCustomTypeClassName()) {
            case PointCodec.CLASS_NAME:
              return new StringToPointCodec(nullStrings);
            case LineStringCodec.CLASS_NAME:
              return new StringToLineStringCodec(nullStrings);
            case PolygonCodec.CLASS_NAME:
              return new StringToPolygonCodec(nullStrings);
            case DATE_RANGE_CLASS_NAME:
              return new StringToDateRangeCodec(nullStrings);
          }
          // fall through
        }
      default:
        try {
          TypeCodec<?> innerCodec = codecFor(cqlType);
          LOGGER.warn(
              String.format(
                  "CQL type %s is not officially supported by this version of DSBulk; "
                      + "string literals will be parsed and formatted using registered codec %s",
                  cqlType, innerCodec.getClass().getSimpleName()));
          return new StringToUnknownTypeCodec<>(innerCodec, nullStrings);
        } catch (CodecNotFoundException e) {
          String msg =
              String.format(
                  "Codec not found for requested operation: [%s <-> %s]", cqlType, String.class);
          CodecNotFoundException e1 = new CodecNotFoundException(msg, cqlType, STRING_TYPE_TOKEN);
          e1.addSuppressed(e);
          throw e1;
        }
    }
  }

  private ConvertingCodec<JsonNode, ?> createJsonNodeConvertingCodec(
      @NotNull DataType cqlType, boolean rootCodec) {
    // Don't apply null strings for non-root codecs
    List<String> nullStrings = rootCodec ? this.nullStrings : ImmutableList.of();
    DataType.Name name = cqlType.getName();
    switch (name) {
      case ASCII:
      case TEXT:
      case VARCHAR:
        @SuppressWarnings("unchecked")
        TypeCodec<String> typeCodec = (TypeCodec<String>) codecFor(cqlType);
        return new JsonNodeToStringCodec(typeCodec, objectMapper, nullStrings);
      case BOOLEAN:
        return new JsonNodeToBooleanCodec(booleanInputWords, nullStrings);
      case TINYINT:
        return new JsonNodeToByteCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case SMALLINT:
        return new JsonNodeToShortCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case INT:
        return new JsonNodeToIntegerCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case BIGINT:
        return new JsonNodeToLongCodec(
            TypeCodec.bigint(),
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case COUNTER:
        return new JsonNodeToLongCodec(
            TypeCodec.counter(),
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case FLOAT:
        return new JsonNodeToFloatCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case DOUBLE:
        return new JsonNodeToDoubleCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case VARINT:
        return new JsonNodeToBigIntegerCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case DECIMAL:
        return new JsonNodeToBigDecimalCodec(
            numberFormat,
            overflowStrategy,
            roundingMode,
            timestampFormat,
            timeZone,
            timeUnit,
            epoch,
            booleanInputWords,
            booleanNumbers,
            nullStrings);
      case DATE:
        return new JsonNodeToLocalDateCodec(localDateFormat, nullStrings);
      case TIME:
        return new JsonNodeToLocalTimeCodec(localTimeFormat, nullStrings);
      case TIMESTAMP:
        return new JsonNodeToInstantCodec(
            timestampFormat, numberFormat, timeZone, timeUnit, epoch, nullStrings);
      case INET:
        return new JsonNodeToInetAddressCodec(nullStrings);
      case UUID:
        {
          @SuppressWarnings("unchecked")
          ConvertingCodec<String, Instant> instantCodec =
              (ConvertingCodec<String, Instant>)
                  createStringConvertingCodec(DataType.timestamp(), false);
          return new JsonNodeToUUIDCodec(TypeCodec.uuid(), instantCodec, generator, nullStrings);
        }
      case TIMEUUID:
        {
          @SuppressWarnings("unchecked")
          ConvertingCodec<String, Instant> instantCodec =
              (ConvertingCodec<String, Instant>)
                  createStringConvertingCodec(DataType.timestamp(), false);
          return new JsonNodeToUUIDCodec(
              TypeCodec.timeUUID(), instantCodec, generator, nullStrings);
        }
      case BLOB:
        return new JsonNodeToBlobCodec(nullStrings);
      case DURATION:
        return new JsonNodeToDurationCodec(nullStrings);
      case LIST:
        {
          DataType elementType = cqlType.getTypeArguments().get(0);
          @SuppressWarnings("unchecked")
          TypeCodec<List<Object>> collectionCodec = (TypeCodec<List<Object>>) codecFor(cqlType);
          @SuppressWarnings("unchecked")
          ConvertingCodec<JsonNode, Object> eltCodec =
              (ConvertingCodec<JsonNode, Object>) createJsonNodeConvertingCodec(elementType, false);
          return new JsonNodeToListCodec<>(collectionCodec, eltCodec, objectMapper, nullStrings);
        }
      case SET:
        {
          DataType elementType = cqlType.getTypeArguments().get(0);
          @SuppressWarnings("unchecked")
          TypeCodec<Set<Object>> collectionCodec = (TypeCodec<Set<Object>>) codecFor(cqlType);
          @SuppressWarnings("unchecked")
          ConvertingCodec<JsonNode, Object> eltCodec =
              (ConvertingCodec<JsonNode, Object>) createJsonNodeConvertingCodec(elementType, false);
          return new JsonNodeToSetCodec<>(collectionCodec, eltCodec, objectMapper, nullStrings);
        }
      case MAP:
        {
          DataType keyType = cqlType.getTypeArguments().get(0);
          DataType valueType = cqlType.getTypeArguments().get(1);
          @SuppressWarnings("unchecked")
          TypeCodec<Map<Object, Object>> mapCodec =
              (TypeCodec<Map<Object, Object>>) codecFor(cqlType);
          @SuppressWarnings("unchecked")
          ConvertingCodec<String, Object> keyCodec =
              (ConvertingCodec<String, Object>) createStringConvertingCodec(keyType, false);
          @SuppressWarnings("unchecked")
          ConvertingCodec<JsonNode, Object> valueCodec =
              (ConvertingCodec<JsonNode, Object>) createJsonNodeConvertingCodec(valueType, false);
          return new JsonNodeToMapCodec<>(
              mapCodec, keyCodec, valueCodec, objectMapper, nullStrings);
        }
      case TUPLE:
        {
          @SuppressWarnings("unchecked")
          TypeCodec<TupleValue> tupleCodec = (TypeCodec<TupleValue>) codecFor(cqlType);
          ImmutableList.Builder<ConvertingCodec<JsonNode, Object>> eltCodecs =
              new ImmutableList.Builder<>();
          for (DataType eltType : ((TupleType) cqlType).getComponentTypes()) {
            @SuppressWarnings("unchecked")
            ConvertingCodec<JsonNode, Object> eltCodec =
                (ConvertingCodec<JsonNode, Object>) createJsonNodeConvertingCodec(eltType, false);
            eltCodecs.add(eltCodec);
          }
          return new JsonNodeToTupleCodec(tupleCodec, eltCodecs.build(), objectMapper, nullStrings);
        }
      case UDT:
        {
          @SuppressWarnings("unchecked")
          TypeCodec<UDTValue> udtCodec = (TypeCodec<UDTValue>) codecFor(cqlType);
          ImmutableMap.Builder<String, ConvertingCodec<JsonNode, Object>> fieldCodecs =
              new ImmutableMap.Builder<>();
          for (UserType.Field field : ((UserType) cqlType)) {
            @SuppressWarnings("unchecked")
            ConvertingCodec<JsonNode, Object> fieldCodec =
                (ConvertingCodec<JsonNode, Object>)
                    createJsonNodeConvertingCodec(field.getType(), false);
            fieldCodecs.put(field.getName(), fieldCodec);
          }
          return new JsonNodeToUDTCodec(udtCodec, fieldCodecs.build(), objectMapper, nullStrings);
        }
      case CUSTOM:
        {
          DataType.CustomType customType = (DataType.CustomType) cqlType;
          switch (customType.getCustomTypeClassName()) {
            case PointCodec.CLASS_NAME:
              return new JsonNodeToPointCodec(objectMapper, nullStrings);
            case LineStringCodec.CLASS_NAME:
              return new JsonNodeToLineStringCodec(objectMapper, nullStrings);
            case PolygonCodec.CLASS_NAME:
              return new JsonNodeToPolygonCodec(objectMapper, nullStrings);
            case DATE_RANGE_CLASS_NAME:
              return new JsonNodeToDateRangeCodec(nullStrings);
          }
          // fall through
        }
      default:
        try {
          TypeCodec<?> innerCodec = codecFor(cqlType);
          LOGGER.warn(
              String.format(
                  "CQL type %s is not officially supported by this version of DSBulk; "
                      + "JSON literals will be parsed and formatted using registered codec %s",
                  cqlType, innerCodec.getClass().getSimpleName()));
          return new JsonNodeToUnknownTypeCodec<>(innerCodec, nullStrings);
        } catch (CodecNotFoundException e) {
          String msg =
              String.format(
                  "Codec not found for requested operation: [%s <-> %s]", cqlType, JsonNode.class);
          CodecNotFoundException e1 =
              new CodecNotFoundException(msg, cqlType, JSON_NODE_TYPE_TOKEN);
          e1.addSuppressed(e);
          throw e1;
        }
    }
  }

  // DAT-288: avoid returning legacy temporal codecs or collection codecs whose elements are legacy
  // temporal codecs.
  private @NotNull TypeCodec<?> codecFor(@NotNull DataType cqlType) {
    switch (cqlType.getName()) {
      case TIMESTAMP:
        return InstantCodec.instance;
      case DATE:
        return LocalDateCodec.instance;
      case TIME:
        return LocalTimeCodec.instance;
      case LIST:
        return TypeCodec.list(codecFor(cqlType.getTypeArguments().get(0)));
      case SET:
        return TypeCodec.set(codecFor(cqlType.getTypeArguments().get(0)));
      case MAP:
        return TypeCodec.map(
            codecFor(cqlType.getTypeArguments().get(0)),
            codecFor(cqlType.getTypeArguments().get(1)));
      default:
        return codecRegistry.codecFor(cqlType);
    }
  }

  private static boolean isNumeric(@NotNull DataType cqlType) {
    return cqlType == DataType.tinyint()
        || cqlType == DataType.smallint()
        || cqlType == DataType.cint()
        || cqlType == DataType.bigint()
        || cqlType == DataType.cfloat()
        || cqlType == DataType.cdouble()
        || cqlType == DataType.varint()
        || cqlType == DataType.decimal();
  }

  private static boolean isTemporal(@NotNull DataType cqlType) {
    return cqlType == DataType.date()
        || cqlType == DataType.time()
        || cqlType == DataType.timestamp();
  }

  private static boolean isUUID(@NotNull DataType cqlType) {
    return cqlType == DataType.uuid() || cqlType == DataType.timeuuid();
  }
}
