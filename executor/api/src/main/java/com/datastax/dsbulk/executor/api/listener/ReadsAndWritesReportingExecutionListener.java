/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.executor.api.listener;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ExecutionListener} that reports useful metrics about ongoing bulk read and write
 * operations. It relies on a delegate {@link MetricsCollectingExecutionListener} as its source of
 * metrics.
 */
public class ReadsAndWritesReportingExecutionListener
    extends AbstractMetricsReportingExecutionListener implements ExecutionListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReadsAndWritesReportingExecutionListener.class);

  private static final MetricFilter METRIC_FILTER =
      (name, metric) -> name.startsWith("executor/reads-writes/");

  private static final String REPORTER_NAME = "bulk-execution-reads-writes-reporter";

  /**
   * Creates a new builder for this class.
   *
   * @return a new builder.
   */
  public static AbstractMetricsReportingExecutionListenerBuilder<
          ReadsAndWritesReportingExecutionListener>
      builder() {
    return new AbstractMetricsReportingExecutionListenerBuilder<
        ReadsAndWritesReportingExecutionListener>() {
      @Override
      public ReadsAndWritesReportingExecutionListener build() {
        Logger l = logger == null ? LOGGER : logger;
        if (scheduler == null) {
          return new ReadsAndWritesReportingExecutionListener(
              delegate, rateUnit, durationUnit, expectedTotal, l);
        } else {
          return new ReadsAndWritesReportingExecutionListener(
              delegate, rateUnit, durationUnit, expectedTotal, l, scheduler);
        }
      }
    };
  }

  private final long expectedTotal;
  private final String countMessage;
  private final String throughputMessage;
  private final String latencyMessage;
  private final Timer timer;
  private final Counter failed;
  private final Counter successful;
  private final Counter inFlight;
  private final Meter sent;
  private final Meter received;
  private final Logger logger;

  /**
   * Creates a default instance of {@link ReadsAndWritesReportingExecutionListener}.
   *
   * <p>The instance will express rates in operations per second, and durations in milliseconds.
   */
  public ReadsAndWritesReportingExecutionListener() {
    this(new MetricsCollectingExecutionListener(), SECONDS, MILLISECONDS, -1, LOGGER);
  }

  /**
   * Creates an instance of {@link ReadsAndWritesReportingExecutionListener} using the given
   * {@linkplain MetricsCollectingExecutionListener delegate}.
   *
   * <p>The instance will express rates in operations per second, and durations in milliseconds.
   *
   * @param delegate the {@link ReadsAndWritesReportingExecutionListener} to use as metrics source.
   */
  public ReadsAndWritesReportingExecutionListener(MetricsCollectingExecutionListener delegate) {
    this(delegate, SECONDS, MILLISECONDS, -1, LOGGER);
  }

  private ReadsAndWritesReportingExecutionListener(
      MetricsCollectingExecutionListener delegate,
      TimeUnit rateUnit,
      TimeUnit durationUnit,
      long expectedTotal,
      Logger logger) {
    super(delegate, REPORTER_NAME, METRIC_FILTER, rateUnit, durationUnit);
    this.expectedTotal = expectedTotal;
    this.logger = logger;
    countMessage = createCountMessageTemplate(expectedTotal);
    throughputMessage = createThroughputMessageTemplate();
    latencyMessage = createLatencyMessageTemplate();
    timer = delegate.getTotalReadsWritesTimer();
    successful = delegate.getSuccessfulReadsWritesCounter();
    failed = delegate.getFailedReadsWritesCounter();
    inFlight = delegate.getInFlightRequestsCounter();
    sent = delegate.getBytesSentMeter();
    received = delegate.getBytesReceivedMeter();
  }

  private ReadsAndWritesReportingExecutionListener(
      MetricsCollectingExecutionListener delegate,
      TimeUnit rateUnit,
      TimeUnit durationUnit,
      long expectedTotal,
      Logger logger,
      ScheduledExecutorService scheduler) {
    super(delegate, REPORTER_NAME, METRIC_FILTER, rateUnit, durationUnit, scheduler);
    this.expectedTotal = expectedTotal;
    this.logger = logger;
    countMessage = createCountMessageTemplate(expectedTotal);
    throughputMessage = createThroughputMessageTemplate();
    latencyMessage = createLatencyMessageTemplate();
    timer = delegate.getTotalReadsWritesTimer();
    successful = delegate.getSuccessfulReadsWritesCounter();
    failed = delegate.getFailedReadsWritesCounter();
    inFlight = delegate.getInFlightRequestsCounter();
    sent = delegate.getBytesSentMeter();
    received = delegate.getBytesReceivedMeter();
  }

  @Override
  public void report(
      SortedMap<String, Gauge> gauges,
      SortedMap<String, Counter> counters,
      SortedMap<String, Histogram> histograms,
      SortedMap<String, Meter> meters,
      SortedMap<String, Timer> timers) {
    Snapshot snapshot = timer.getSnapshot();
    long total = timer.getCount();
    String durationUnit = getDurationUnit();
    String rateUnit = getRateUnit();
    if (expectedTotal < 0) {
      logger.info(
          String.format(
              countMessage, total, successful.getCount(), failed.getCount(), inFlight.getCount()));
    } else {
      float achieved = (float) total / (float) expectedTotal * 100f;
      logger.info(
          String.format(
              countMessage,
              total,
              successful.getCount(),
              failed.getCount(),
              inFlight.getCount(),
              achieved));
    }
    double throughput = timer.getMeanRate();
    double sizeSent = sent.getMeanRate();
    double sizeReceived = received.getMeanRate();
    logger.info(
        String.format(
            throughputMessage,
            convertRate(throughput),
            rateUnit,
            convertRate(sizeSent / BYTES_PER_MB),
            rateUnit,
            convertRate(sizeReceived / BYTES_PER_MB),
            rateUnit,
            throughput == 0 ? 0 : (sizeSent / BYTES_PER_KB) / throughput,
            throughput == 0 ? 0 : (sizeReceived / BYTES_PER_KB) / throughput));
    logger.info(
        String.format(
            latencyMessage,
            convertDuration(snapshot.getMean()),
            convertDuration(snapshot.get75thPercentile()),
            convertDuration(snapshot.get99thPercentile()),
            convertDuration(snapshot.get999thPercentile()),
            durationUnit));
  }

  private static String createCountMessageTemplate(long expectedTotal) {
    if (expectedTotal < 0) {
      return "Reads/Writes: "
          + "total: %,d, "
          + "successful: %,d, "
          + "failed: %,d, "
          + "in-flight: %,d";
    } else {
      int numDigits = String.format("%,d", expectedTotal).length();
      return "Reads/Writes: "
          + "total: %,"
          + numDigits
          + "d, "
          + "successful: %,"
          + numDigits
          + "d, "
          + "failed: %,d, "
          + "in-flight: %,d, "
          + "progression: %,.0f%%";
    }
  }

  private static String createThroughputMessageTemplate() {
    return "Throughput: "
        + "%,.0f reads-writes/%s, "
        + "%,.2f mb/%s sent, "
        + "%,.2f mb/%s received ("
        + "%,.2f kb/write, "
        + "%,.2f kb/read)";
  }

  private static String createLatencyMessageTemplate() {
    return "Latencies: mean %,.2f, 75p %,.2f, 99p %,.2f, 999p %,.2f %s";
  }
}
