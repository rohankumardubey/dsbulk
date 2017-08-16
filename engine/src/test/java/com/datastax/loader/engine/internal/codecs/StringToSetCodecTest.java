/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.engine.internal.codecs;

import static com.datastax.driver.core.TypeCodec.cdouble;
import static com.datastax.driver.core.TypeCodec.set;
import static com.datastax.driver.core.TypeCodec.varchar;
import static com.datastax.loader.engine.internal.Assertions.assertThat;

import com.datastax.driver.core.TypeCodec;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.junit.Test;

public class StringToSetCodecTest {

  private StringToDoubleCodec eltCodec1 =
      new StringToDoubleCodec(
          ThreadLocal.withInitial(
              () -> new DecimalFormat("#,###.##", DecimalFormatSymbols.getInstance(Locale.US))));

  private ExtendedCodecRegistry.StringToStringCodec eltCodec2 =
      new ExtendedCodecRegistry.StringToStringCodec(TypeCodec.varchar());

  private StringToSetCodec<Double> codec1 = new StringToSetCodec<>(set(cdouble()), eltCodec1, "|");
  private StringToSetCodec<String> codec2 = new StringToSetCodec<>(set(varchar()), eltCodec2, "|");

  @Test
  public void should_convert_from_valid_input() throws Exception {
    assertThat(codec1)
        .convertsFrom("1|2|3")
        .to(Sets.newHashSet(1d, 2d, 3d))
        .convertsFrom("1 | 2 | 3")
        .to(Sets.newHashSet(1d, 2d, 3d))
        .convertsFrom("1,234.56|78,900")
        .to(Sets.newHashSet(1234.56d, 78900d))
        .convertsFrom("|")
        .to(Sets.newHashSet(null, null))
        .convertsFrom(null)
        .to(null)
        .convertsFrom("")
        .to(null);
    assertThat(codec2)
        .convertsFrom("foo|bar")
        .to(Sets.newLinkedHashSet(Lists.newArrayList("foo", "bar")))
        .convertsFrom(" foo | bar ")
        .to(Sets.newLinkedHashSet(Lists.newArrayList("foo", "bar")))
        .convertsFrom("|")
        .to(Sets.newLinkedHashSet(Lists.newArrayList(null, null)))
        .convertsFrom(null)
        .to(null)
        .convertsFrom("")
        .to(null);
  }

  @Test
  public void should_convert_to_valid_input() throws Exception {
    assertThat(codec1)
        .convertsTo(Sets.newLinkedHashSet(Lists.newArrayList(1d, 2d, 3d)))
        .from("1|2|3")
        .convertsTo(Sets.newLinkedHashSet(Lists.newArrayList(1234.56d, 78900d)))
        .from("1,234.56|78,900")
        .convertsTo(Sets.newLinkedHashSet(Lists.newArrayList(1d, null)))
        .from("1|")
        .convertsTo(Sets.newLinkedHashSet(Lists.newArrayList(null, 0d)))
        .from("|0")
        .convertsTo(Sets.newLinkedHashSet(Lists.newArrayList((Double) null)))
        .from("")
        .convertsTo(null)
        .from(null);
    assertThat(codec2)
        .convertsTo(Sets.newLinkedHashSet(Lists.newArrayList("foo", "bar")))
        .from("foo|bar")
        .convertsTo(Sets.newLinkedHashSet(Lists.newArrayList((String) null)))
        .from("")
        .convertsTo(null)
        .from(null);
  }

  @Test
  public void should_not_convert_from_invalid_input() throws Exception {
    assertThat(codec1).cannotConvertFrom("1|not a valid double");
  }
}
