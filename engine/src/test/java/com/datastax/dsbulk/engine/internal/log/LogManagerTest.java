/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.log;

import static com.datastax.dsbulk.engine.internal.log.statement.StatementFormatVerbosity.EXTENDED;
import static com.google.common.collect.Range.closed;
import static com.google.common.collect.Range.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.Configuration;
import com.datastax.driver.core.ProtocolOptions;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverInternalError;
import com.datastax.driver.core.exceptions.OperationTimedOutException;
import com.datastax.dsbulk.commons.tests.utils.FileUtils;
import com.datastax.dsbulk.commons.tests.utils.ReflectionUtils;
import com.datastax.dsbulk.connectors.api.Record;
import com.datastax.dsbulk.connectors.api.internal.DefaultErrorRecord;
import com.datastax.dsbulk.connectors.api.internal.DefaultRecord;
import com.datastax.dsbulk.engine.WorkflowType;
import com.datastax.dsbulk.engine.internal.log.statement.StatementFormatter;
import com.datastax.dsbulk.engine.internal.statement.BulkSimpleStatement;
import com.datastax.dsbulk.engine.internal.statement.UnmappableStatement;
import com.datastax.dsbulk.executor.api.exception.BulkExecutionException;
import com.datastax.dsbulk.executor.api.internal.result.DefaultReadResult;
import com.datastax.dsbulk.executor.api.internal.result.DefaultWriteResult;
import com.datastax.dsbulk.executor.api.result.ReadResult;
import com.datastax.dsbulk.executor.api.result.WriteResult;
import com.google.common.collect.Range;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class LogManagerTest {

  private final String source1 = "line1\n";
  private final String source2 = "line2\n";
  private final String source3 = "line3\n";

  private URI resource1;
  private URI resource2;
  private URI resource3;

  private URI location1;
  private URI location2;
  private URI location3;

  private Record record1;
  private Record record2;
  private Record record3;

  private Statement stmt1;
  private Statement stmt2;
  private Statement stmt3;

  private WriteResult writeResult1;
  private WriteResult writeResult2;
  private WriteResult writeResult3;
  private WriteResult batchWriteResult;

  private ReadResult readResult1;
  private ReadResult readResult2;
  private ReadResult readResult3;

  private Cluster cluster;

  private final StatementFormatter formatter =
      StatementFormatter.builder()
          .withMaxQueryStringLength(500)
          .withMaxBoundValueLength(50)
          .withMaxBoundValues(10)
          .withMaxInnerStatements(10)
          .build();

  @BeforeEach
  void setUp() throws Exception {
    cluster = mock(Cluster.class);
    Configuration configuration = mock(Configuration.class);
    ProtocolOptions protocolOptions = mock(ProtocolOptions.class);
    when(cluster.getConfiguration()).thenReturn(configuration);
    when(configuration.getProtocolOptions()).thenReturn(protocolOptions);
    when(protocolOptions.getProtocolVersion()).thenReturn(ProtocolVersion.V4);
    when(configuration.getCodecRegistry()).thenReturn(CodecRegistry.DEFAULT_INSTANCE);
    resource1 = new URI("file:///file1.csv");
    resource2 = new URI("file:///file2.csv");
    resource3 = new URI("file:///file3.csv");
    location1 = new URI("file:///file1.csv?line=1");
    location2 = new URI("file:///file2.csv?line=2");
    location3 = new URI("file:///file3.csv?line=3");
    record1 =
        new DefaultErrorRecord(
            source1, () -> resource1, 1, () -> location1, new RuntimeException("error 1"));
    record2 =
        new DefaultErrorRecord(
            source2, () -> resource2, 2, () -> location2, new RuntimeException("error 2"));
    record3 =
        new DefaultErrorRecord(
            source3, () -> resource3, 3, () -> location3, new RuntimeException("error 3"));
    stmt1 = new UnmappableStatement(record1, () -> location1, new RuntimeException("error 1"));
    stmt2 = new UnmappableStatement(record2, () -> location2, new RuntimeException("error 2"));
    stmt3 = new UnmappableStatement(record3, () -> location3, new RuntimeException("error 3"));
    writeResult1 =
        new DefaultWriteResult(
            new BulkExecutionException(
                new OperationTimedOutException(null, "error 1"),
                new BulkSimpleStatement<>(record1, "INSERT 1")));
    writeResult2 =
        new DefaultWriteResult(
            new BulkExecutionException(
                new OperationTimedOutException(null, "error 2"),
                new BulkSimpleStatement<>(record2, "INSERT 2")));
    writeResult3 =
        new DefaultWriteResult(
            new BulkExecutionException(
                new OperationTimedOutException(null, "error 3"),
                new BulkSimpleStatement<>(record3, "INSERT 3")));
    readResult1 =
        new DefaultReadResult(
            new BulkExecutionException(
                new OperationTimedOutException(null, "error 1"),
                new BulkSimpleStatement<>(record1, "SELECT 1")));
    readResult2 =
        new DefaultReadResult(
            new BulkExecutionException(
                new OperationTimedOutException(null, "error 2"),
                new BulkSimpleStatement<>(record2, "SELECT 2")));
    readResult3 =
        new DefaultReadResult(
            new BulkExecutionException(
                new OperationTimedOutException(null, "error 3"),
                new BulkSimpleStatement<>(record3, "SELECT 3")));
    BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
    batch.add(new BulkSimpleStatement<>(record1, "INSERT 1", "foo", 42));
    batch.add(new BulkSimpleStatement<>(record2, "INSERT 2", "bar", 43));
    batch.add(new BulkSimpleStatement<>(record3, "INSERT 3", "qix", 44));
    batchWriteResult =
        new DefaultWriteResult(
            new BulkExecutionException(new OperationTimedOutException(null, "error batch"), batch));
  }

  @Test
  void should_stop_when_max_record_mapping_errors_reached() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.LOAD, cluster, outputDir, 2, 0, formatter, EXTENDED);
    logManager.init();
    Flux<Statement> stmts = Flux.just(stmt1, stmt2, stmt3);
    try {
      stmts.transform(logManager.newUnmappableStatementsHandler()).blockLast();
      fail("Expecting TooManyErrorsException to be thrown");
    } catch (TooManyErrorsException e) {
      assertThat(e).hasMessage("Too many errors, the maximum allowed is 2.");
      assertThat(e.getMaxErrors()).isEqualTo(2);
    }
    logManager.close();
    Path bad = logManager.getExecutionDirectory().resolve("mapping.bad");
    Path errors = logManager.getExecutionDirectory().resolve("mapping-errors.log");
    Path positions = logManager.getExecutionDirectory().resolve("positions.txt");
    assertThat(bad.toFile()).exists();
    assertThat(errors.toFile()).exists();
    assertThat(positions.toFile()).exists();
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(bad, errors, positions);
    List<String> badLines = Files.readAllLines(bad, Charset.forName("UTF-8"));
    assertThat(badLines).hasSize(3);
    assertThat(badLines.get(0)).isEqualTo(source1.trim());
    assertThat(badLines.get(1)).isEqualTo(source2.trim());
    assertThat(badLines.get(2)).isEqualTo(source3.trim());
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .containsOnlyOnce("java.lang.RuntimeException: error 1")
        .containsOnlyOnce("Location: " + location2)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source2))
        .containsOnlyOnce("java.lang.RuntimeException: error 2")
        .containsOnlyOnce("Location: " + location3)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source3))
        .containsOnlyOnce("java.lang.RuntimeException: error 2");
  }

  @Test
  void should_stop_when_max_result_mapping_errors_reached() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.LOAD, cluster, outputDir, 2, 0, formatter, EXTENDED);
    logManager.init();
    Flux<Record> records = Flux.just(record1, record2, record3);
    try {
      records.transform(logManager.newFailedRecordsHandler()).blockLast();
      fail("Expecting TooManyErrorsException to be thrown");
    } catch (TooManyErrorsException e) {
      assertThat(e).hasMessage("Too many errors, the maximum allowed is 2.");
      assertThat(e.getMaxErrors()).isEqualTo(2);
    }
    logManager.close();
    Path bad = logManager.getExecutionDirectory().resolve("connector.bad");
    Path errors = logManager.getExecutionDirectory().resolve("connector-errors.log");
    Path positions = logManager.getExecutionDirectory().resolve("positions.txt");
    assertThat(bad.toFile()).exists();
    assertThat(errors.toFile()).exists();
    assertThat(positions.toFile()).exists();
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(bad, errors, positions);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .containsOnlyOnce("java.lang.RuntimeException: error 1")
        .containsOnlyOnce("Location: " + location2)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source2))
        .containsOnlyOnce("java.lang.RuntimeException: error 2");
  }

  @Test
  void should_stop_when_max_write_errors_reached() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.LOAD, cluster, outputDir, 2, 0, formatter, EXTENDED);
    logManager.init();
    Flux<WriteResult> stmts = Flux.just(writeResult1, writeResult2, writeResult3);
    try {
      stmts.transform(logManager.newFailedWritesHandler()).blockLast();
      fail("Expecting TooManyErrorsException to be thrown");
    } catch (TooManyErrorsException e) {
      assertThat(e).hasMessage("Too many errors, the maximum allowed is 2.");
      assertThat(e.getMaxErrors()).isEqualTo(2);
    }
    logManager.close();
    Path bad = logManager.getExecutionDirectory().resolve("load.bad");
    Path errors = logManager.getExecutionDirectory().resolve("load-errors.log");
    Path positions = logManager.getExecutionDirectory().resolve("positions.txt");
    assertThat(bad.toFile()).exists();
    assertThat(errors.toFile()).exists();
    assertThat(positions.toFile()).exists();
    List<String> badLines = Files.readAllLines(bad, Charset.forName("UTF-8"));
    assertThat(badLines).hasSize(3);
    assertThat(badLines.get(0)).isEqualTo(source1.trim());
    assertThat(badLines.get(1)).isEqualTo(source2.trim());
    assertThat(badLines.get(2)).isEqualTo(source3.trim());
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(bad, errors, positions);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .contains("INSERT 1")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: INSERT 1 (error 1)")
        .containsOnlyOnce("Location: " + location2)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source2))
        .contains("INSERT 2")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: INSERT 2 (error 2)")
        .containsOnlyOnce("Location: " + location3)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source3))
        .contains("INSERT 3")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: INSERT 3 (error 3)");
    List<String> positionLines = Files.readAllLines(positions, Charset.forName("UTF-8"));
    assertThat(positionLines)
        .contains("file:///file1.csv:1")
        .contains("file:///file2.csv:2")
        .contains("file:///file3.csv:3");
  }

  @Test
  void should_not_stop_before_sample_size_is_reached() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.LOAD, cluster, outputDir, 0, 2, formatter, EXTENDED);
    logManager.init();
    Flux<WriteResult> stmts = Flux.just(writeResult1, writeResult2, writeResult3);
    stmts.transform(logManager.newFailedWritesHandler()).blockLast();
    logManager.close();
    Path bad = logManager.getExecutionDirectory().resolve("load.bad");
    Path errors = logManager.getExecutionDirectory().resolve("load-errors.log");
    Path positions = logManager.getExecutionDirectory().resolve("positions.txt");
    assertThat(bad.toFile()).exists();
    assertThat(errors.toFile()).exists();
    assertThat(positions.toFile()).exists();
    List<String> badLines = Files.readAllLines(bad, Charset.forName("UTF-8"));
    assertThat(badLines).hasSize(3);
    assertThat(badLines.get(0)).isEqualTo(source1.trim());
    assertThat(badLines.get(1)).isEqualTo(source2.trim());
    assertThat(badLines.get(2)).isEqualTo(source3.trim());
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(bad, errors, positions);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .contains("INSERT 1")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: INSERT 1 (error 1)")
        .containsOnlyOnce("Location: " + location2)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source2))
        .contains("INSERT 2")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: INSERT 2 (error 2)")
        .containsOnlyOnce("Location: " + location3)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source3))
        .contains("INSERT 3")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: INSERT 3 (error 3)");
    List<String> positionLines = Files.readAllLines(positions, Charset.forName("UTF-8"));
    assertThat(positionLines)
        .contains("file:///file1.csv:1")
        .contains("file:///file2.csv:2")
        .contains("file:///file3.csv:3");
  }

  @Test
  void should_stop_when_max_write_errors_reached_and_statements_batched() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.LOAD, cluster, outputDir, 1, 0, formatter, EXTENDED);
    logManager.init();
    Flux<WriteResult> stmts = Flux.just(batchWriteResult);
    try {
      stmts.transform(logManager.newFailedWritesHandler()).blockLast();
      fail("Expecting TooManyErrorsException to be thrown");
    } catch (TooManyErrorsException e) {
      assertThat(e).hasMessage("Too many errors, the maximum allowed is 1.");
      assertThat(e.getMaxErrors()).isEqualTo(1);
    }
    logManager.close();
    Path bad = logManager.getExecutionDirectory().resolve("load.bad");
    Path errors = logManager.getExecutionDirectory().resolve("load-errors.log");
    Path positions = logManager.getExecutionDirectory().resolve("positions.txt");
    assertThat(bad.toFile()).exists();
    assertThat(errors.toFile()).exists();
    assertThat(positions.toFile()).exists();
    List<String> badLines = Files.readAllLines(bad, Charset.forName("UTF-8"));
    assertThat(badLines).hasSize(3);
    assertThat(badLines.get(0)).isEqualTo(source1.trim());
    assertThat(badLines.get(1)).isEqualTo(source2.trim());
    assertThat(badLines.get(2)).isEqualTo(source3.trim());
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(bad, errors, positions);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1.toString())
        .containsOnlyOnce("Location: " + location2.toString())
        .containsOnlyOnce("Location: " + location3.toString())
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source2))
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source3))
        .contains("INSERT 1")
        .contains("INSERT 2")
        .contains("INSERT 3")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed")
        .contains("error batch");
    List<String> positionLines = Files.readAllLines(positions, Charset.forName("UTF-8"));
    assertThat(positionLines)
        .contains("file:///file1.csv:1")
        .contains("file:///file2.csv:2")
        .contains("file:///file3.csv:3");
  }

  @Test
  void should_stop_when_max_read_errors_reached() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.UNLOAD, cluster, outputDir, 2, 0, formatter, EXTENDED);
    logManager.init();
    Flux<ReadResult> stmts = Flux.just(readResult1, readResult2, readResult3);
    try {
      stmts.transform(logManager.newFailedReadsHandler()).blockLast();
      fail("Expecting TooManyErrorsException to be thrown");
    } catch (TooManyErrorsException e) {
      assertThat(e).hasMessage("Too many errors, the maximum allowed is 2.");
      assertThat(e.getMaxErrors()).isEqualTo(2);
    }
    logManager.close();
    Path errors = logManager.getExecutionDirectory().resolve("unload-errors.log");
    assertThat(errors.toFile()).exists();
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(errors);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .contains("SELECT 1")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: SELECT 1 (error 1)")
        .containsOnlyOnce("Location: " + location2)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source2))
        .contains("SELECT 2")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: SELECT 2 (error 2)");
  }

  @Test
  void should_not_stop_when_sample_size_is_not_met() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.UNLOAD, cluster, outputDir, 0, 0.01f, formatter, EXTENDED);
    logManager.init();
    Flux<ReadResult> stmts = Flux.just(readResult1, readResult2, readResult3);
    stmts
        .transform(logManager.newTotalItemsCounter())
        .transform(logManager.newFailedReadsHandler())
        .blockLast();
    logManager.close();
    Path errors = logManager.getExecutionDirectory().resolve("unload-errors.log");
    assertThat(errors.toFile()).exists();
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(errors);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .contains("SELECT 1")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: SELECT 1 (error 1)")
        .containsOnlyOnce("Location: " + location2)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source2))
        .contains("SELECT 2")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: SELECT 2 (error 2)");
  }

  @Test
  void should_stop_when_sample_size_is_met_and_percentage_exceeded() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.UNLOAD, cluster, outputDir, 0, 0.01f, formatter, EXTENDED);
    logManager.init();
    Flux<ReadResult> stmts = Flux.just(readResult1);
    try {
      stmts
          .repeat(101)
          .transform(logManager.newTotalItemsCounter())
          .transform(logManager.newFailedReadsHandler())
          .blockLast();
      fail("Expecting TooManyErrorsException to be thrown");
    } catch (TooManyErrorsException e) {
      assertThat(e).hasMessage("Too many errors, the maximum percentage allowed is 1.0%.");
      assertThat(e.getMaxErrorRatio()).isEqualTo(0.01f);
    }
    logManager.close();
    Path errors = logManager.getExecutionDirectory().resolve("unload-errors.log");
    assertThat(errors.toFile()).exists();
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(errors);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    assertThat(lines.stream().filter(l -> l.contains("BulkExecutionException")).count())
        .isEqualTo(101);
  }

  @Test
  void should_stop_when_unrecoverable_error_writing() throws Exception {
    Path outputDir = Files.createTempDirectory("test4");
    LogManager logManager =
        new LogManager(WorkflowType.LOAD, cluster, outputDir, 1000, 0, formatter, EXTENDED);
    logManager.init();
    DefaultWriteResult result =
        new DefaultWriteResult(
            new BulkExecutionException(
                new DriverInternalError("error 1"),
                new BulkSimpleStatement<>(record1, "INSERT 1")));
    Flux<WriteResult> stmts = Flux.just(result);
    try {
      stmts.transform(logManager.newFailedWritesHandler()).blockLast();
      fail("Expecting DriverInternalError to be thrown");
    } catch (DriverInternalError e) {
      assertThat(e).hasMessage("error 1");
    }
    logManager.close();
    Path bad = logManager.getExecutionDirectory().resolve("load.bad");
    Path errors = logManager.getExecutionDirectory().resolve("load-errors.log");
    Path positions = logManager.getExecutionDirectory().resolve("positions.txt");
    assertThat(bad.toFile()).exists();
    assertThat(errors.toFile()).exists();
    assertThat(positions.toFile()).exists();
    List<String> badLines = Files.readAllLines(bad, Charset.forName("UTF-8"));
    assertThat(badLines).hasSize(1);
    assertThat(badLines.get(0)).isEqualTo(source1.trim());
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(bad, errors, positions);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .contains("INSERT 1")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: INSERT 1 (error 1)");
    List<String> positionLines = Files.readAllLines(positions, Charset.forName("UTF-8"));
    assertThat(positionLines).contains("file:///file1.csv:1");
  }

  @Test
  void should_stop_when_unrecoverable_error_reading() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.UNLOAD, cluster, outputDir, 2, 0, formatter, EXTENDED);
    logManager.init();
    DefaultReadResult result =
        new DefaultReadResult(
            new BulkExecutionException(
                new DriverInternalError("error 1"),
                new BulkSimpleStatement<>(record1, "SELECT 1")));
    Flux<ReadResult> stmts = Flux.just(result);
    try {
      stmts.transform(logManager.newFailedReadsHandler()).blockLast();
      fail("Expecting DriverInternalError to be thrown");
    } catch (DriverInternalError e) {
      assertThat(e).hasMessage("error 1");
    }
    logManager.close();
    Path errors = logManager.getExecutionDirectory().resolve("unload-errors.log");
    assertThat(errors.toFile()).exists();
    assertThat(FileUtils.listAllFilesInDirectory(logManager.getExecutionDirectory()))
        .containsOnly(errors);
    List<String> lines = Files.readAllLines(errors, Charset.forName("UTF-8"));
    String content = String.join("\n", lines);
    assertThat(content)
        .containsOnlyOnce("Location: " + location1)
        .containsOnlyOnce("Source  : " + LogUtils.formatSingleLine(source1))
        .contains("SELECT 1")
        .containsOnlyOnce(
            "com.datastax.dsbulk.executor.api.exception.BulkExecutionException: Statement execution failed: SELECT 1 (error 1)");
  }

  @Test
  void should_record_positions() throws Exception {
    Path outputDir = Files.createTempDirectory("test");
    LogManager logManager =
        new LogManager(WorkflowType.LOAD, cluster, outputDir, 1, 0, formatter, EXTENDED);
    logManager.init();
    assertRanges(logManager, new long[] {1, 2, 3, 4}, closed(1L, 4L));
    assertRanges(logManager, new long[] {1, 2, 3, 5}, closed(1L, 3L), singleton(5L));
    assertRanges(logManager, new long[] {5, 3, 2, 1}, closed(1L, 3L), singleton(5L));
    assertRanges(logManager, new long[] {1, 3, 5, 4, 2}, closed(1L, 5L));
    assertRanges(logManager, new long[] {2, 4, 5, 3, 1}, closed(1L, 5L));
    assertRanges(logManager, new long[] {4, 3, 2, 1}, closed(1L, 4L));
    assertRanges(logManager, new long[] {4, 3, 2, 1}, closed(1L, 4L));
    assertRanges(logManager, new long[] {3, 2}, closed(2L, 3L));
    assertRanges(logManager, new long[] {3, 5, 4, 2}, closed(2L, 5L));
    logManager.close();
  }

  @Test
  void should_add_position() {
    List<Range<Long>> positions = new ArrayList<>();
    positions = LogManager.addPosition(positions, 3);
    assertThat(positions).containsExactly(singleton(3L));
    positions = LogManager.addPosition(positions, 1);
    assertThat(positions).containsExactly(singleton(1L), singleton(3L));
    positions = LogManager.addPosition(positions, 2);
    assertThat(positions).containsExactly(closed(1L, 3L));
    positions = LogManager.addPosition(positions, 2);
    assertThat(positions).containsExactly(closed(1L, 3L));
    positions = LogManager.addPosition(positions, 6);
    assertThat(positions).containsExactly(closed(1L, 3L), singleton(6L));
    positions = LogManager.addPosition(positions, 5);
    assertThat(positions).containsExactly(closed(1L, 3L), closed(5L, 6L));
    positions = LogManager.addPosition(positions, 4);
    assertThat(positions).containsExactly(closed(1L, 6L));
  }

  @Test
  void should_merge_positions() {
    assertThat(LogManager.mergePositions(ranges(), ranges())).isEmpty();
    assertThat(LogManager.mergePositions(ranges(), ranges(closed(1L, 3L))))
        .isEqualTo(ranges(closed(1L, 3L)));
    assertThat(LogManager.mergePositions(ranges(closed(1L, 3L)), ranges()))
        .isEqualTo(ranges(closed(1L, 3L)));
    assertThat(LogManager.mergePositions(ranges(), ranges())).isEmpty();
    assertThat(LogManager.mergePositions(ranges(closed(1L, 3L)), ranges(closed(1L, 3L))))
        .isEqualTo(ranges(closed(1L, 3L)));
    assertThat(LogManager.mergePositions(ranges(closed(1L, 3L)), ranges(closed(2L, 4L))))
        .isEqualTo(ranges(closed(1L, 4L)));
    assertThat(LogManager.mergePositions(ranges(closed(1L, 3L)), ranges(closed(4L, 6L))))
        .isEqualTo(ranges(closed(1L, 6L)));
    assertThat(LogManager.mergePositions(ranges(closed(1L, 3L)), ranges(closed(5L, 7L))))
        .isEqualTo(ranges(closed(1L, 3L), closed(5L, 7L)));
    assertThat(LogManager.mergePositions(ranges(closed(2L, 4L)), ranges(closed(1L, 3L))))
        .isEqualTo(ranges(closed(1L, 4L)));
    assertThat(LogManager.mergePositions(ranges(closed(4L, 6L)), ranges(closed(1L, 3L))))
        .isEqualTo(ranges(closed(1L, 6L)));
    assertThat(LogManager.mergePositions(ranges(closed(5L, 7L)), ranges(closed(1L, 3L))))
        .isEqualTo(ranges(closed(1L, 3L), closed(5L, 7L)));
  }

  @SafeVarargs
  private static List<Range<Long>> ranges(Range<Long>... ranges) {
    return ranges == null ? Lists.emptyList() : Lists.newArrayList(ranges);
  }

  @SafeVarargs
  private static void assertRanges(LogManager logManager, long[] lines, Range<Long>... ranges)
      throws URISyntaxException {
    Flux.fromStream(LongStream.of(lines).boxed())
        .map(
            line -> {
              try {
                return result(line);
              } catch (URISyntaxException e) {
                throw new RuntimeException(e);
              }
            })
        .transform(logManager.newResultPositionTracker())
        .blockLast();
    @SuppressWarnings("unchecked")
    Map<URI, List<Range<Long>>> positions =
        (Map<URI, List<Range<Long>>>) ReflectionUtils.getInternalState(logManager, "positions");
    assertThat(positions)
        .hasEntrySatisfying(new URI("file1"), l -> assertThat(l).containsExactly(ranges));
    positions.clear();
  }

  private static WriteResult result(long position) throws URISyntaxException {
    URI resource = new URI("file1");
    URI location = new URI("file1?line=" + position);
    return new DefaultWriteResult(
        new BulkSimpleStatement<>(
            new DefaultRecord("irrelevant", () -> resource, position, () -> location), "INSERT 1"),
        null);
  }
}
