/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.commons.tests.simulacron;

import com.datastax.dsbulk.commons.tests.RemoteClusterExtension;
import com.datastax.dsbulk.commons.tests.simulacron.factory.BoundClusterFactory;
import com.datastax.dsbulk.commons.tests.utils.NetworkUtils;
import com.datastax.oss.simulacron.server.AddressResolver;
import com.datastax.oss.simulacron.server.BoundCluster;
import com.datastax.oss.simulacron.server.BoundNode;
import com.datastax.oss.simulacron.server.Server;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A manager for {@link BoundCluster Simulacron} clusters that helps testing with JUnit 5 and
 * Cassandra.
 */
public class SimulacronExtension extends RemoteClusterExtension implements AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimulacronExtension.class);
  private static final String SIMULACRON = "SIMULACRON";
  private static final Server SERVER =
      Server.builder()
          .withAddressResolver(new AddressResolver.Inet4Resolver(NetworkUtils.findAvailablePort()))
          .build();

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    Class<?> type = parameterContext.getParameter().getType();
    return type.equals(BoundCluster.class)
        || super.supportsParameter(parameterContext, extensionContext);
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    Parameter parameter = parameterContext.getParameter();
    Class<?> type = parameter.getType();
    if (BoundCluster.class.equals(type)) {
      BoundCluster boundCluster = getOrCreateBoundCluster(extensionContext);
      LOGGER.debug(String.format("Returning %s for parameter %s", boundCluster, parameter));
      return boundCluster;
    } else {
      return super.resolveParameter(parameterContext, extensionContext);
    }
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    BoundCluster boundCluster = getOrCreateBoundCluster(context);
    boundCluster.clearLogs();
    boundCluster.clearPrimes(true);
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    super.afterAll(context);
    stopBoundCluster(context);
  }

  @Override
  protected int getBinaryPort(ExtensionContext context) {
    return getOrCreateBoundCluster(context)
        .dc(0)
        .getNodes()
        .stream()
        .map(BoundNode::port)
        .findFirst()
        .orElse(9042);
  }

  @Override
  protected List<InetAddress> getContactPoints(ExtensionContext context) {
    return getOrCreateBoundCluster(context)
        .dc(0)
        .getNodes()
        .stream()
        .map(BoundNode::inet)
        .collect(Collectors.toList());
  }

  private BoundCluster getOrCreateBoundCluster(ExtensionContext context) {
    return context
        .getStore(TEST_NAMESPACE)
        .getOrComputeIfAbsent(
            SIMULACRON,
            f -> {
              BoundClusterFactory factory =
                  BoundClusterFactory.createInstanceForClass(context.getRequiredTestClass());
              BoundCluster boundCluster = SERVER.register(factory.createClusterSpec());
              boundCluster.start();
              return boundCluster;
            },
            BoundCluster.class);
  }

  private void stopBoundCluster(ExtensionContext context) {
    BoundCluster boundCluster =
        context.getStore(TEST_NAMESPACE).remove(SIMULACRON, BoundCluster.class);
    if (boundCluster != null) {
      boundCluster.stop();
    }
  }
}
