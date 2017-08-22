/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.engine.internal.settings;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.TokenRange;
import com.datastax.loader.commons.config.LoaderConfig;
import com.datastax.loader.connectors.api.RecordMetadata;
import com.datastax.loader.engine.WorkflowType;
import com.datastax.loader.engine.internal.codecs.ExtendedCodecRegistry;
import com.datastax.loader.engine.internal.schema.DefaultMapping;
import com.datastax.loader.engine.internal.schema.DefaultReadResultMapper;
import com.datastax.loader.engine.internal.schema.DefaultRecordMapper;
import com.datastax.loader.engine.internal.schema.MergedRecordMetadata;
import com.datastax.loader.engine.internal.schema.ReadResultMapper;
import com.datastax.loader.engine.internal.schema.RecordMapper;
import com.datastax.loader.executor.api.statement.TableScanner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** */
public class SchemaSettings {

  private final LoaderConfig config;

  private KeyspaceMetadata keyspace;
  private TableMetadata table;
  private String keyspaceName;
  private String tableName;
  private PreparedStatement preparedStatement;

  SchemaSettings(LoaderConfig config) {
    this.config = config;
  }

  public RecordMapper createRecordMapper(
      Session session, RecordMetadata recordMetadata, ExtendedCodecRegistry codecRegistry) {
    ImmutableBiMap<String, String> fieldsToVariables = createFieldsToVariablesMap(session);
    PreparedStatement statement = prepareStatement(session, fieldsToVariables, WorkflowType.WRITE);
    DefaultMapping mapping = new DefaultMapping(fieldsToVariables, codecRegistry);
    return new DefaultRecordMapper(
        statement,
        mapping,
        mergeRecordMetadata(recordMetadata),
        ImmutableSet.copyOf(config.getStringList("nullWords")),
        config.getBoolean("nullToUnset"));
  }

  public ReadResultMapper createReadResultMapper(
      Session session, RecordMetadata recordMetadata, ExtendedCodecRegistry codecRegistry) {
    ImmutableBiMap<String, String> fieldsToVariables = createFieldsToVariablesMap(session);
    preparedStatement = prepareStatement(session, fieldsToVariables, WorkflowType.READ);
    DefaultMapping mapping = new DefaultMapping(fieldsToVariables, codecRegistry);
    return new DefaultReadResultMapper(
        mapping, mergeRecordMetadata(recordMetadata), config.getFirstString("nullWords"));
  }

  public List<Statement> createReadStatements(Cluster cluster) {
    ColumnDefinitions variables = preparedStatement.getVariables();
    if (variables.size() == 0) {
      return Collections.singletonList(preparedStatement.bind());
    }
    assert variables.size() == 2
            && variables.getIndexOf("start") != -1
            && variables.getIndexOf("end") != -1
        : "The provided statement contains unrecognized bound variables; only 'start' and 'end' can be used";
    Set<TokenRange> ring = cluster.getMetadata().getTokenRanges();
    return TableScanner.scan(
        ring,
        range ->
            preparedStatement
                .bind()
                .setToken("start", range.getStart())
                .setToken("end", range.getEnd()));
  }

  private ImmutableBiMap<String, String> createFieldsToVariablesMap(Session session) {
    ImmutableBiMap.Builder<String, String> fieldsToVariablesBuilder = null;
    if (config.hasPath("mapping") && !config.getConfig("mapping").isEmpty()) {
      fieldsToVariablesBuilder = new ImmutableBiMap.Builder<>();
      LoaderConfig mapping = config.getConfig("mapping");
      for (String path : mapping.root().keySet()) {
        fieldsToVariablesBuilder.put(path, mapping.getString(path));
      }
    }
    if (config.hasPath("keyspace")) {
      Preconditions.checkState(config.hasPath("table"), "Keyspace and table must be specified");
      keyspaceName = Metadata.quoteIfNecessary(config.getString("keyspace"));
      tableName = Metadata.quoteIfNecessary(config.getString("table"));
      keyspace = session.getCluster().getMetadata().getKeyspace(keyspaceName);
      Preconditions.checkNotNull(keyspace, "Keyspace does not exist: " + keyspaceName);
      table = keyspace.getTable(tableName);
      Preconditions.checkNotNull(
          table, String.format("Table does not exist: %s.%s", keyspaceName, tableName));
    }
    if (!config.hasPath("mapping") || config.getConfig("mapping").isEmpty()) {
      Preconditions.checkState(
          keyspace != null && table != null, "Keyspace and table must be specified");
      fieldsToVariablesBuilder = inferFieldsToVariablesMap();
    }
    Preconditions.checkNotNull(
        fieldsToVariablesBuilder,
        "Mapping was absent and could not be inferred, please provide an explicit mapping");
    return fieldsToVariablesBuilder.build();
  }

  private RecordMetadata mergeRecordMetadata(RecordMetadata fallback) {
    if (config.hasPath("recordMetadata") && !config.getConfig("recordMetadata").isEmpty()) {
      ImmutableMap.Builder<String, TypeToken<?>> fieldsToTypes = new ImmutableMap.Builder<>();
      LoaderConfig recordMetadata = config.getConfig("recordMetadata");
      for (String path : recordMetadata.root().keySet()) {
        fieldsToTypes.put(path, TypeToken.of(recordMetadata.getClass(path)));
      }
      return new MergedRecordMetadata(fieldsToTypes.build(), fallback);
    }
    return fallback;
  }

  private PreparedStatement prepareStatement(
      Session session,
      ImmutableBiMap<String, String> fieldsToVariables,
      WorkflowType workflowType) {
    String query;
    if (config.hasPath("statement")) {
      query = config.getString("statement");
    } else {
      Preconditions.checkState(
          keyspace != null && table != null, "Keyspace and table must be specified");
      query =
          workflowType == WorkflowType.WRITE
              ? inferWriteQuery(fieldsToVariables)
              : inferReadQuery(fieldsToVariables);
    }
    return session.prepare(query);
  }

  private ImmutableBiMap.Builder<String, String> inferFieldsToVariablesMap() {
    ImmutableBiMap.Builder<String, String> fieldsToVariables = new ImmutableBiMap.Builder<>();
    for (int i = 0; i < table.getColumns().size(); i++) {
      ColumnMetadata col = table.getColumns().get(i);
      String name = Metadata.quoteIfNecessary(col.getName());
      fieldsToVariables.put(col.getName(), name);
    }
    return fieldsToVariables;
  }

  private String inferWriteQuery(ImmutableBiMap<String, String> fieldsToVariables) {
    StringBuilder sb = new StringBuilder("INSERT INTO ");
    sb.append(keyspaceName).append('.').append(tableName).append('(');
    appendColumnNames(fieldsToVariables, sb);
    sb.append(") VALUES (");
    Set<String> cols = new LinkedHashSet<>(fieldsToVariables.values());
    Iterator<String> it = cols.iterator();
    while (it.hasNext()) {
      String col = it.next();
      sb.append(':');
      sb.append(col);
      if (it.hasNext()) sb.append(',');
    }
    sb.append(')');
    return sb.toString();
  }

  private String inferReadQuery(ImmutableBiMap<String, String> fieldsToVariables) {
    StringBuilder sb = new StringBuilder("SELECT ");
    appendColumnNames(fieldsToVariables, sb);
    sb.append(" FROM ").append(keyspaceName).append('.').append(tableName).append(" WHERE ");
    appendTokenFunction(sb);
    sb.append(" > :start AND ");
    appendTokenFunction(sb);
    sb.append(" <= :end");
    return sb.toString();
  }

  private void appendColumnNames(
      ImmutableBiMap<String, String> fieldsToVariables, StringBuilder sb) {
    // de-dup in case the mapping has both indexed and mapped entries
    // for the same bound variable
    Set<String> cols = new LinkedHashSet<>(fieldsToVariables.values());
    Iterator<String> it = cols.iterator();
    while (it.hasNext()) {
      // this assumes that the variable name found in the mapping
      // corresponds to a CQL column having the exact same name.
      String col = it.next();
      sb.append(Metadata.quoteIfNecessary(col));
      if (it.hasNext()) {
        sb.append(',');
      }
    }
  }

  private void appendTokenFunction(StringBuilder sb) {
    sb.append("token(");
    Iterator<ColumnMetadata> pks = table.getPartitionKey().iterator();
    while (pks.hasNext()) {
      ColumnMetadata pk = pks.next();
      sb.append(Metadata.quoteIfNecessary(pk.getName()));
      if (pks.hasNext()) {
        sb.append(',');
      }
    }
    sb.append(")");
  }
}
