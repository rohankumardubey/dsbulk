/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.ccm;

import static com.datastax.dsbulk.commons.tests.utils.FileUtils.deleteDirectory;
import static com.datastax.dsbulk.commons.tests.utils.StringUtils.escapeUserInput;
import static com.datastax.dsbulk.engine.ccm.CSVConnectorEndToEndCCMIT.assertComplexRows;
import static com.datastax.dsbulk.engine.ccm.CSVConnectorEndToEndCCMIT.checkNumbersWritten;
import static com.datastax.dsbulk.engine.ccm.CSVConnectorEndToEndCCMIT.checkTemporalsWritten;
import static com.datastax.dsbulk.engine.internal.codecs.util.OverflowStrategy.REJECT;
import static com.datastax.dsbulk.engine.internal.codecs.util.OverflowStrategy.TRUNCATE;
import static com.datastax.dsbulk.engine.tests.utils.EndToEndUtils.validateBadOps;
import static com.datastax.dsbulk.engine.tests.utils.EndToEndUtils.validateExceptionsLog;
import static com.datastax.dsbulk.engine.tests.utils.EndToEndUtils.validateOutputFiles;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.IP_BY_COUNTRY_MAPPING;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.JSON_RECORDS;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.JSON_RECORDS_SKIP;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.JSON_RECORDS_UNIQUE;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.JSON_RECORDS_WITH_SPACES;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.SELECT_FROM_IP_BY_COUNTRY;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.SELECT_FROM_IP_BY_COUNTRY_WITH_SPACES;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.createIpByCountryTable;
import static com.datastax.dsbulk.engine.tests.utils.JsonUtils.createWithSpacesTable;
import static java.math.RoundingMode.UNNECESSARY;
import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.driver.core.Session;
import com.datastax.dsbulk.commons.tests.ccm.CCMCluster;
import com.datastax.dsbulk.commons.tests.ccm.annotations.CCMConfig;
import com.datastax.dsbulk.commons.tests.utils.FileUtils;
import com.datastax.dsbulk.engine.DataStaxBulkLoader;
import com.datastax.dsbulk.engine.internal.codecs.util.OverflowStrategy;
import com.datastax.dsbulk.engine.internal.settings.LogSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@CCMConfig(numberOfNodes = 1)
@Tag("ccm")
class JsonConnectorEndToEndCCMIT extends EndToEndCCMITBase {

  private Path unloadDir;
  private Path logDir;

  JsonConnectorEndToEndCCMIT(CCMCluster ccm, Session session) {
    super(ccm, session);
  }

  @BeforeAll
  void createTables() {
    createIpByCountryTable(session);
    createWithSpacesTable(session);
  }

  @BeforeEach
  void setUpDirs() throws IOException {
    logDir = createTempDirectory("logs");
    unloadDir = createTempDirectory("unload");
  }

  @AfterEach
  void deleteDirs() {
    deleteDirectory(logDir);
    deleteDirectory(unloadDir);
  }

  /** Simple test case which attempts to load and unload data using ccm. */
  @Test
  void full_load_unload() throws Exception {

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.name");
    args.add("json");
    args.add("--connector.json.url");
    args.add(escapeUserInput(JSON_RECORDS_UNIQUE));
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    int status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateResultSetSize(24, SELECT_FROM_IP_BY_COUNTRY);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.name");
    args.add("json");
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateOutputFiles(24, unloadDir);
  }

  /** Simple test case which attempts to load and unload data using ccm and compression (LZ4). */
  @Test
  void full_load_unload_lz4() throws Exception {

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--driver.protocol.compression");
    args.add("LZ4");
    args.add("--connector.json.url");
    args.add(escapeUserInput(JSON_RECORDS_UNIQUE));
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    int status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateResultSetSize(24, SELECT_FROM_IP_BY_COUNTRY);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--driver.protocol.compression");
    args.add("LZ4");
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateOutputFiles(24, unloadDir);
  }

  /** Simple test case which attempts to load and unload data using ccm and compression (Snappy). */
  @Test
  void full_load_unload_snappy() throws Exception {

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--driver.protocol.compression");
    args.add("SNAPPY");
    args.add("--connector.json.url");
    args.add(escapeUserInput(JSON_RECORDS_UNIQUE));
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    int status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateResultSetSize(24, SELECT_FROM_IP_BY_COUNTRY);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--driver.protocol.compression");
    args.add("SNAPPY");
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateOutputFiles(24, unloadDir);
  }

  /**
   * Attempts to load and unload complex types (Collections, UDTs, etc).
   *
   * @jira_ticket DAT-288
   */
  @Test
  void full_load_unload_complex() throws Exception {

    session.execute("DROP TABLE IF EXISTS complex");
    session.execute("DROP TYPE IF EXISTS contacts");

    session.execute(
        "CREATE TYPE contacts ("
            + "f_tuple frozen<tuple<int, text, float, timestamp>>, "
            + "f_list frozen<list<timestamp>>"
            + ")");
    session.execute(
        "CREATE TABLE complex ("
            + "pk int PRIMARY KEY, "
            + "c_text text, "
            + "c_int int, "
            + "c_tuple frozen<tuple<int, text, float, timestamp>>, "
            + "c_map map<timestamp, varchar>,"
            + "c_list list<timestamp>,"
            + "c_set set<varchar>,"
            + "c_udt frozen<contacts>)");

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(getClass().getResource("/complex.json")));
    args.add("--connector.json.mode");
    args.add("SINGLE_DOCUMENT");
    args.add("--codec.nullStrings");
    args.add("N/A");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("complex");

    int status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();

    assertComplexRows(session);

    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.mode");
    args.add("SINGLE_DOCUMENT");
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--codec.nullStrings");
    args.add("N/A");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("complex");

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    // 2 documents + 2 lines for single document mode
    validateOutputFiles(4, unloadDir);

    args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.mode");
    args.add("SINGLE_DOCUMENT");
    args.add("--codec.nullStrings");
    args.add("N/A");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("complex");

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();

    assertComplexRows(session);
  }

  /** Attempts to load and unload a larger dataset which can be batched. */
  @Test
  void full_load_unload_large_batches() throws Exception {

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(JSON_RECORDS));
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    int status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateResultSetSize(500, SELECT_FROM_IP_BY_COUNTRY);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateOutputFiles(500, unloadDir);
  }

  /**
   * Attempt to load and unload data using ccm for a keyspace and table that is case-sensitive, and
   * with a column name containing spaces. The source data also has a header row containing spaces,
   * and the source data contains a multi-line value.
   */
  @Test
  void full_load_unload_with_spaces() throws Exception {

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("-url");
    args.add(escapeUserInput(JSON_RECORDS_WITH_SPACES));
    args.add("--schema.mapping");
    args.add("key=key,my source=my destination");
    args.add("-k");
    args.add("MYKS");
    args.add("-t");
    args.add("WITH_SPACES");

    int status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateResultSetSize(1, SELECT_FROM_IP_BY_COUNTRY_WITH_SPACES);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("-url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--schema.mapping");
    args.add("key=key,my source=my destination");
    args.add("-k");
    args.add("MYKS");
    args.add("-t");
    args.add("WITH_SPACES");

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateOutputFiles(1, unloadDir);
  }

  /** Attempts to load and unload data, some of which will be unsuccessful. */
  @Test
  void skip_test_load_unload() throws Exception {

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(JSON_RECORDS_SKIP));
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);
    args.add("--connector.json.skipRecords");
    args.add("3");
    args.add("--connector.json.maxRecords");
    args.add("24");
    args.add("--schema.allowMissingFields");
    args.add("true");

    int status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isEqualTo(DataStaxBulkLoader.STATUS_COMPLETED_WITH_ERRORS);
    validateResultSetSize(21, SELECT_FROM_IP_BY_COUNTRY);
    Path logPath = Paths.get(System.getProperty(LogSettings.OPERATION_DIRECTORY_KEY));
    validateBadOps(3, logPath);
    validateExceptionsLog(3, "Source  :", "mapping-errors.log", logPath);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("ip_by_country");
    args.add("--schema.mapping");
    args.add(IP_BY_COUNTRY_MAPPING);

    status = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(status).isZero();
    validateOutputFiles(21, unloadDir);
  }

  /** Test for DAT-224. */
  @Test
  void should_truncate_and_round() throws Exception {

    session.execute("DROP TABLE IF EXISTS numbers");
    session.execute(
        "CREATE TABLE IF NOT EXISTS numbers (key varchar PRIMARY KEY, vdouble double, vdecimal decimal)");

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(ClassLoader.getSystemResource("number.json").toExternalForm()));
    args.add("--connector.json.mode");
    args.add("SINGLE_DOCUMENT");
    args.add("--codec.overflowStrategy");
    args.add("TRUNCATE");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("numbers");
    args.add("--schema.mapping");
    args.add("*=*");

    int loadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(loadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    checkNumbersWritten(TRUNCATE, UNNECESSARY, session);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.mode");
    args.add("MULTI_DOCUMENT");
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--codec.roundingStrategy");
    args.add("FLOOR");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.query");
    args.add("SELECT key, vdouble, vdecimal FROM numbers");

    int unloadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(unloadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    checkNumbersRead(TRUNCATE, unloadDir);
    deleteDirectory(logDir);

    // check we can load from the unloaded dataset
    args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--codec.overflowStrategy");
    args.add("TRUNCATE");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("numbers");
    args.add("--schema.mapping");
    args.add("*=*");

    loadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(loadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    // no rounding possible in json
    checkNumbersWritten(TRUNCATE, UNNECESSARY, session);
  }

  /** Test for DAT-224. */
  @Test
  void should_not_truncate_nor_round() throws Exception {

    session.execute("DROP TABLE IF EXISTS numbers");
    session.execute(
        "CREATE TABLE IF NOT EXISTS numbers (key varchar PRIMARY KEY, vdouble double, vdecimal decimal)");

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(ClassLoader.getSystemResource("number.json").toExternalForm()));
    args.add("--connector.json.mode");
    args.add("SINGLE_DOCUMENT");
    args.add("--codec.overflowStrategy");
    args.add("REJECT");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("numbers");
    args.add("--schema.mapping");
    args.add("*=*");

    int loadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(loadStatus).isEqualTo(DataStaxBulkLoader.STATUS_COMPLETED_WITH_ERRORS);
    Path logPath = Paths.get(System.getProperty(LogSettings.OPERATION_DIRECTORY_KEY));
    validateExceptionsLog(
        1,
        "ArithmeticException: Cannot convert 0.12345678901234567890123456789 from BigDecimal to Double",
        "mapping-errors.log",
        logPath);
    checkNumbersWritten(REJECT, UNNECESSARY, session);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--connector.json.mode");
    args.add("MULTI_DOCUMENT");
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--codec.roundingStrategy");
    args.add("UNNECESSARY");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.query");
    args.add("SELECT key, vdouble, vdecimal FROM numbers");

    int unloadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(unloadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    checkNumbersRead(REJECT, unloadDir);
    deleteDirectory(logDir);

    // check we can load from the unloaded dataset
    args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--codec.overflowStrategy");
    args.add("REJECT");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("numbers");
    args.add("--schema.mapping");
    args.add("*=*");

    loadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(loadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    checkNumbersWritten(REJECT, UNNECESSARY, session);
  }

  /** Test for DAT-236. */
  @Test
  void temporal_roundtrip() throws IOException {

    session.execute("DROP TABLE IF EXISTS temporals");
    session.execute(
        "CREATE TABLE IF NOT EXISTS temporals (key int PRIMARY KEY, vdate date, vtime time, vtimestamp timestamp, vseconds timestamp)");

    List<String> args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(ClassLoader.getSystemResource("temporal.json").toExternalForm());
    args.add("--codec.locale");
    args.add("fr_FR");
    args.add("--codec.timeZone");
    args.add("Europe/Paris");
    args.add("--codec.date");
    args.add("cccc, d MMMM uuuu");
    args.add("--codec.time");
    args.add("HHmmssSSS");
    args.add("--codec.timestamp");
    args.add("ISO_ZONED_DATE_TIME");
    args.add("--codec.unit");
    args.add("SECONDS");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("temporals");
    args.add("--schema.mapping");
    args.add("*=*");

    int loadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(loadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    checkTemporalsWritten(session);
    deleteDirectory(logDir);

    args = new ArrayList<>();
    args.add("unload");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--codec.locale");
    args.add("fr_FR");
    args.add("--codec.timeZone");
    args.add("Europe/Paris");
    args.add("--codec.date");
    args.add("cccc, d MMMM uuuu");
    args.add("--codec.time");
    args.add("HHmmssSSS");
    args.add("--codec.timestamp");
    args.add("ISO_ZONED_DATE_TIME");
    args.add("--codec.unit");
    args.add("SECONDS");
    args.add("--connector.json.maxConcurrentFiles");
    args.add("1");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.query");
    args.add("SELECT key, vdate, vtime, vtimestamp, vseconds FROM temporals");

    int unloadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(unloadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    checkTemporalsRead(unloadDir);
    deleteDirectory(logDir);

    // check we can load from the unloaded dataset
    args = new ArrayList<>();
    args.add("load");
    args.add("--connector.name");
    args.add("json");
    args.add("--log.directory");
    args.add(escapeUserInput(logDir));
    args.add("--connector.json.url");
    args.add(escapeUserInput(unloadDir));
    args.add("--codec.locale");
    args.add("fr_FR");
    args.add("--codec.timeZone");
    args.add("Europe/Paris");
    args.add("--codec.date");
    args.add("cccc, d MMMM uuuu");
    args.add("--codec.time");
    args.add("HHmmssSSS");
    args.add("--codec.timestamp");
    args.add("ISO_ZONED_DATE_TIME");
    args.add("--codec.unit");
    args.add("SECONDS");
    args.add("--schema.keyspace");
    args.add(session.getLoggedKeyspace());
    args.add("--schema.table");
    args.add("temporals");
    args.add("--schema.mapping");
    args.add("*=*");

    loadStatus = new DataStaxBulkLoader(addContactPointAndPort(args)).run();
    assertThat(loadStatus).isEqualTo(DataStaxBulkLoader.STATUS_OK);
    checkTemporalsWritten(session);
  }

  private static void checkNumbersRead(OverflowStrategy overflowStrategy, Path unloadDir)
      throws IOException {
    Map<String, String> doubles = new HashMap<>();
    Map<String, String> bigdecimals = new HashMap<>();
    List<String> lines =
        FileUtils.readAllLinesInDirectoryAsStream(unloadDir).collect(Collectors.toList());
    Pattern pattern = Pattern.compile("\\{\"key\":\"(.+?)\",\"vdouble\":(.+?),\"vdecimal\":(.+?)}");
    for (String line : lines) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        doubles.put(matcher.group(1), matcher.group(2));
        bigdecimals.put(matcher.group(1), matcher.group(3));
      }
    }
    // no rounding possible in Json, the nodes are numeric
    checkDoubles(doubles, overflowStrategy);
    checkBigDecimals(bigdecimals, overflowStrategy);
  }

  @SuppressWarnings("FloatingPointLiteralPrecision")
  private static void checkDoubles(Map<String, String> numbers, OverflowStrategy overflowStrategy) {
    assertThat(numbers.get("scientific_notation")).isEqualTo("1.0E7");
    assertThat(Double.valueOf(numbers.get("scientific_notation"))).isEqualTo(10_000_000d);
    assertThat(numbers.get("regular_notation")).isEqualTo("1.0E7");
    assertThat(Double.valueOf(numbers.get("regular_notation"))).isEqualTo(10_000_000d);
    assertThat(numbers.get("hex_notation")).isEqualTo("1.7976931348623157E308");
    assertThat(Double.valueOf(numbers.get("hex_notation"))).isEqualTo(Double.MAX_VALUE);
    assertThat(numbers.get("irrational")).isEqualTo("0.1");
    assertThat(numbers.get("Double.NaN")).isEqualTo("\"NaN\"");
    assertThat(numbers.get("Double.POSITIVE_INFINITY")).isEqualTo("\"Infinity\"");
    assertThat(numbers.get("Double.NEGATIVE_INFINITY")).isEqualTo("\"-Infinity\"");
    assertThat(numbers.get("Double.MAX_VALUE")).isEqualTo("1.7976931348623157E308");
    assertThat(Double.valueOf(numbers.get("Double.MAX_VALUE"))).isEqualTo(Double.MAX_VALUE);
    assertThat(numbers.get("Double.MIN_VALUE")).isEqualTo("4.9E-324");
    assertThat(Double.valueOf(numbers.get("Double.MIN_VALUE"))).isEqualTo(Double.MIN_VALUE);
    assertThat(numbers.get("Double.MIN_NORMAL")).isEqualTo("2.2250738585072014E-308");
    assertThat(Double.valueOf(numbers.get("Double.MIN_NORMAL"))).isEqualTo(Double.MIN_NORMAL);
    assertThat(numbers.get("Float.MAX_VALUE")).isEqualTo("3.4028235E38");
    assertThat(Float.valueOf(numbers.get("Float.MAX_VALUE"))).isEqualTo(Float.MAX_VALUE);
    assertThat(numbers.get("Float.MIN_VALUE")).isEqualTo("1.4E-45");
    if (overflowStrategy == TRUNCATE) {
      assertThat(numbers.get("too_many_digits")).isEqualTo("0.12345678901234568");
    }
  }

  @SuppressWarnings("FloatingPointLiteralPrecision")
  private static void checkBigDecimals(
      Map<String, String> numbers, OverflowStrategy overflowStrategy) {
    assertThat(numbers.get("scientific_notation")).isEqualTo("1.0E+7");
    assertThat(Double.valueOf(numbers.get("scientific_notation"))).isEqualTo(10_000_000d);
    assertThat(numbers.get("regular_notation")).isEqualTo("10000000");
    assertThat(Double.valueOf(numbers.get("regular_notation"))).isEqualTo(10_000_000d);
    assertThat(numbers.get("hex_notation")).isEqualTo("1.7976931348623157E+308");
    assertThat(Double.valueOf(numbers.get("hex_notation"))).isEqualTo(Double.MAX_VALUE);
    assertThat(numbers.get("irrational")).isEqualTo("0.1");
    assertThat(numbers.get("Double.MAX_VALUE")).isEqualTo("1.7976931348623157E+308");
    assertThat(Double.valueOf(numbers.get("Double.MAX_VALUE"))).isEqualTo(Double.MAX_VALUE);
    assertThat(numbers.get("Double.MIN_VALUE")).isEqualTo("4.9E-324");
    assertThat(Double.valueOf(numbers.get("Double.MIN_VALUE"))).isEqualTo(Double.MIN_VALUE);
    assertThat(numbers.get("Double.MIN_NORMAL")).isEqualTo("2.2250738585072014E-308");
    assertThat(Double.valueOf(numbers.get("Double.MIN_NORMAL"))).isEqualTo(Double.MIN_NORMAL);
    assertThat(numbers.get("Float.MAX_VALUE")).isEqualTo("340282350000000000000000000000000000000");
    assertThat(Float.valueOf(numbers.get("Float.MAX_VALUE"))).isEqualTo(Float.MAX_VALUE);
    assertThat(numbers.get("Float.MIN_VALUE")).isEqualTo("1.4E-45");
    if (overflowStrategy == TRUNCATE) {
      assertThat(numbers.get("too_many_digits")).isEqualTo("0.12345678901234567890123456789");
    }
  }

  private static void checkTemporalsRead(Path unloadDir) throws IOException {
    String line =
        FileUtils.readAllLinesInDirectoryAsStream(unloadDir).collect(Collectors.toList()).get(0);
    Pattern pattern =
        Pattern.compile(
            "\\{\"key\":(.+?),\"vdate\":\"(.+?)\",\"vtime\":\"(.+?)\",\"vtimestamp\":\"(.+?)\",\"vseconds\":\"(.+?)\"}");
    Matcher matcher = pattern.matcher(line);
    assertThat(matcher.find()).isTrue();
    assertThat(matcher.group(2)).isEqualTo("vendredi, 9 mars 2018");
    assertThat(matcher.group(3)).isEqualTo("171232584");
    assertThat(matcher.group(4)).isEqualTo("2018-03-09T17:12:32.584+01:00[Europe/Paris]");
    assertThat(matcher.group(5)).isEqualTo("2018-03-09T17:12:32+01:00[Europe/Paris]");
  }
}
