/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.settings;

import static com.datastax.dsbulk.engine.internal.utils.StringUtils.DELIMITER;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import com.datastax.driver.core.Cluster;
import com.datastax.dsbulk.commons.config.BulkConfigurationException;
import com.datastax.dsbulk.commons.config.LoaderConfig;
import com.datastax.dsbulk.commons.internal.config.ConfigUtils;
import com.datastax.dsbulk.engine.WorkflowType;
import com.datastax.dsbulk.engine.internal.log.JansiConsoleAppender;
import com.datastax.dsbulk.engine.internal.log.LogManager;
import com.datastax.dsbulk.engine.internal.log.statement.StatementFormatVerbosity;
import com.datastax.dsbulk.engine.internal.log.statement.StatementFormatter;
import com.datastax.dsbulk.engine.internal.utils.HelpUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class LogSettings {

  public static final String OPERATION_DIRECTORY_KEY = "com.datastax.dsbulk.OPERATION_DIRECTORY";
  public static final String PRODUCTION_KEY = "com.datastax.dsbulk.PRODUCTION";
  static final String MAIN_LOG_FILE_APPENDER = "MAIN_LOG_FILE_APPENDER";
  private static final String EFFECTIVE_SETTINGS_LOGGER =
      "com.datastax.dsbulk.engine.internal.settings.EFFECTIVE_SETTINGS";
  private static final String MAIN_LOG_FILE_NAME = "operation.log";
  private static final String STDOUT_APPENDER = "STDOUT";
  private static final String STDERR_APPENDER = "STDERR";
  private static final String DISABLE_ANSI = "jansi.strip";

  /** The options for stack trace printing. */
  public static final ImmutableList<String> STACK_TRACE_PRINTER_OPTIONS =
      ImmutableList.of(
          // number of stack elements to print
          "5",
          // packages to exclude from stack traces
          "reactor.core",
          "com.google",
          "io.netty",
          "java.util.concurrent");

  /**
   * The layout pattern to use for the main log file.
   *
   * <p>Example of formatted message:
   *
   * <pre>
   * 2018-03-09 14:41:02 ERROR Unload workflow engine execution UNLOAD_20180309-144101-093338 aborted: Invalid table
   * com.datastax.driver.core.exceptions.SyntaxError: Invalid table
   *     at com.datastax.driver.core.exceptions.SyntaxError.copy(SyntaxError.java:49)
   * 	   ...
   * </pre>
   */
  private static final String LAYOUT_PATTERN =
      "%date{yyyy-MM-dd HH:mm:ss,UTC} %-5level %msg%n%ex{"
          + Joiner.on(',').join(STACK_TRACE_PRINTER_OPTIONS)
          + "}";

  private static final Logger LOGGER = LoggerFactory.getLogger(LogSettings.class);

  // Path Constants
  private static final String STMT = "stmt";
  private static final String MAX_QUERY_STRING_LENGTH = STMT + DELIMITER + "maxQueryStringLength";
  private static final String MAX_BOUND_VALUE_LENGTH = STMT + DELIMITER + "maxBoundValueLength";
  private static final String MAX_BOUND_VALUES = STMT + DELIMITER + "maxBoundValues";
  private static final String MAX_INNER_STATEMENTS = STMT + DELIMITER + "maxInnerStatements";
  private static final String LEVEL = STMT + DELIMITER + "level";
  private static final String MAX_ERRORS = "maxErrors";
  private static final String ANSI_ENABLED = "ansiEnabled";

  private final LoaderConfig config;
  private final String executionId;

  private Path executionDirectory;
  private int maxQueryStringLength;
  private int maxBoundValueLength;
  private int maxBoundValues;
  private int maxInnerStatements;
  private StatementFormatVerbosity level;
  private int maxErrors;
  private float maxErrorsRatio;
  private FileAppender<ILoggingEvent> mainLogFileAppender;

  LogSettings(LoaderConfig config, String executionId) {
    this.config = config;
    this.executionId = executionId;
  }

  public void init(boolean writeToStandardOutput) throws IOException {
    try {
      executionDirectory = config.getPath("directory").resolve(executionId);
      checkExecutionDirectory();
      System.setProperty(OPERATION_DIRECTORY_KEY, executionDirectory.toFile().getAbsolutePath());
      maxQueryStringLength = config.getInt(MAX_QUERY_STRING_LENGTH);
      maxBoundValueLength = config.getInt(MAX_BOUND_VALUE_LENGTH);
      maxBoundValues = config.getInt(MAX_BOUND_VALUES);
      maxInnerStatements = config.getInt(MAX_INNER_STATEMENTS);
      level = config.getEnum(StatementFormatVerbosity.class, LEVEL);
      String maxErrorString = config.getString(MAX_ERRORS);
      if (isPercent(maxErrorString)) {
        maxErrorsRatio = Float.parseFloat(maxErrorString.replaceAll("\\s*%", "")) / 100f;
        validatePercentageRange(maxErrorsRatio);
        maxErrors = 0;
      } else {
        maxErrors = config.getInt(MAX_ERRORS);
        maxErrorsRatio = 0;
      }
      if (!config.getBoolean(ANSI_ENABLED)) {
        disableAnsi();
      }
      if (writeToStandardOutput) {
        redirectStandardOutputToStandardError();
      }
      if (isProduction()) {
        mainLogFileAppender =
            createMainLogFileAppender(executionDirectory.resolve(MAIN_LOG_FILE_NAME));
      }
      installJavaLoggingToSLF4JBridge();
    } catch (ConfigException e) {
      throw ConfigUtils.configExceptionToBulkConfigurationException(e, "log");
    }
  }

  public void logEffectiveSettings(Config global) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(HelpUtils.getVersionMessage() + " starting.");
      ch.qos.logback.classic.Logger root =
          (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
      LoggerContext lc = root.getLoggerContext();
      String production = lc.getProperty(PRODUCTION_KEY);
      if (production != null && production.equalsIgnoreCase("true")) {
        ch.qos.logback.classic.Logger effectiveSettingsLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EFFECTIVE_SETTINGS_LOGGER);
        effectiveSettingsLogger.setAdditive(false);
        effectiveSettingsLogger.addAppender(root.getAppender(MAIN_LOG_FILE_APPENDER));
        effectiveSettingsLogger.info("Effective settings:");
        Set<Map.Entry<String, ConfigValue>> entries =
            new TreeSet<>(Comparator.comparing(Map.Entry::getKey));
        entries.addAll(global.entrySet());
        for (Map.Entry<String, ConfigValue> entry : entries) {
          // Skip all settings that have a `metaSettings` path element.
          if (entry.getKey().contains(".metaSettings.")) {
            continue;
          }
          effectiveSettingsLogger.info(
              String.format(
                  "%s = %s",
                  entry.getKey(), entry.getValue().render(ConfigRenderOptions.concise())));
        }
      }
      LOGGER.info("Available CPU cores: {}.", Runtime.getRuntime().availableProcessors());
      LOGGER.info("Operation output directory: {}.", executionDirectory);
    }
  }

  public LogManager newLogManager(WorkflowType workflowType, Cluster cluster) {
    StatementFormatter formatter =
        StatementFormatter.builder()
            .withMaxQueryStringLength(maxQueryStringLength)
            .withMaxBoundValueLength(maxBoundValueLength)
            .withMaxBoundValues(maxBoundValues)
            .withMaxInnerStatements(maxInnerStatements)
            .build();
    return new LogManager(
        workflowType, cluster, executionDirectory, maxErrors, maxErrorsRatio, formatter, level);
  }

  public FileAppender<ILoggingEvent> getMainLogFileAppender() {
    return mainLogFileAppender;
  }

  private void checkExecutionDirectory() throws IOException {
    if (Files.exists(executionDirectory)) {
      if (Files.isDirectory(executionDirectory)) {
        if (Files.isWritable(executionDirectory)) {
          @SuppressWarnings("StreamResourceLeak")
          long count = Files.list(executionDirectory).count();
          if (count > 0) {
            throw new IllegalArgumentException(
                "Execution directory exists but is not empty: " + executionDirectory);
          }
        } else {
          throw new IllegalArgumentException(
              "Execution directory exists but is not writable: " + executionDirectory);
        }
      } else {
        throw new IllegalArgumentException(
            "Execution directory exists but is not a directory: " + executionDirectory);
      }
    } else {
      Files.createDirectories(executionDirectory);
    }
  }

  private static void disableAnsi() {
    System.setProperty(DISABLE_ANSI, "true");
    ch.qos.logback.classic.Logger root =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    JansiConsoleAppender<?> stdout = (JansiConsoleAppender<?>) root.getAppender(STDOUT_APPENDER);
    stdout.stop();
    stdout.start();
    JansiConsoleAppender<?> stderr = (JansiConsoleAppender<?>) root.getAppender(STDERR_APPENDER);
    stderr.stop();
    stderr.start();
  }

  private static void redirectStandardOutputToStandardError() {
    ch.qos.logback.classic.Logger root =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    LoggerContext lc = root.getLoggerContext();
    String production = lc.getProperty(PRODUCTION_KEY);
    if (production != null && production.equalsIgnoreCase("true")) {
      root.detachAppender(STDOUT_APPENDER);
      Appender<ILoggingEvent> stderr = root.getAppender(STDERR_APPENDER);
      stderr.clearAllFilters();
    }
    LOGGER.info("Standard output is reserved, log messages are redirected to standard error.");
  }

  private static boolean isProduction() {
    ch.qos.logback.classic.Logger root =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    LoggerContext lc = root.getLoggerContext();
    String production = lc.getProperty(PRODUCTION_KEY);
    return production != null && production.equalsIgnoreCase("true");
  }

  @VisibleForTesting
  public static FileAppender<ILoggingEvent> createMainLogFileAppender(Path mainLogFile) {
    ch.qos.logback.classic.Logger root =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    LoggerContext lc = root.getLoggerContext();
    PatternLayoutEncoder ple = new PatternLayoutEncoder();
    ple.setPattern(LAYOUT_PATTERN);
    ple.setContext(lc);
    ple.setCharset(StandardCharsets.UTF_8);
    ple.start();
    FileAppender<ILoggingEvent> mainLogFileAppender = new FileAppender<>();
    mainLogFileAppender.setName(MAIN_LOG_FILE_APPENDER);
    mainLogFileAppender.setFile(mainLogFile.toFile().getAbsolutePath());
    mainLogFileAppender.setEncoder(ple);
    mainLogFileAppender.setContext(lc);
    mainLogFileAppender.setAppend(false);
    mainLogFileAppender.start();
    root.addAppender(mainLogFileAppender);
    return mainLogFileAppender;
  }

  private static void installJavaLoggingToSLF4JBridge() {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private static boolean isPercent(String maxErrors) {
    return maxErrors.contains("%");
  }

  private static void validatePercentageRange(float maxErrorRatio) {
    if (maxErrorRatio <= 0 || maxErrorRatio >= 1) {
      throw new BulkConfigurationException(
          "maxErrors must either be a number, or percentage between 0 and 100 exclusive.");
    }
  }
}
