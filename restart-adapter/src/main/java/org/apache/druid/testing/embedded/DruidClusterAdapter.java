/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.testing.embedded;

import org.apache.druid.testing.embedded.health.LeadershipCheck;
import org.apache.druid.testing.embedded.health.NodeDiscoveryCheck;
import org.apache.druid.testing.embedded.health.SegmentAvailabilityCheck;
import org.apache.druid.testing.embedded.health.ServerTopologyCheck;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.CompositeHealthCheck;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Adapter for Apache Druid's EmbeddedDruidCluster to enable restart testing
 * using the RestartTestingFramework.
 *
 * This adapter supports restarting individual Druid servers (Coordinator, Overlord,
 * Broker, Historical, Indexer, Router) with different restart modes:
 * - GRACEFUL: Clean shutdown followed by restart
 * - CRASH: Forced termination without lifecycle cleanup
 * - DELAYED_CRASH: Crash with delay before restart
 */
public class DruidClusterAdapter implements ClusterAdapter<EmbeddedDruidCluster>
{
  private static final Logger LOG = LoggerFactory.getLogger(DruidClusterAdapter.class);

  private final DruidStateCapture stateCapture;
  private final CompositeHealthCheck<EmbeddedDruidCluster> healthCheck;

  public DruidClusterAdapter()
  {
    this.stateCapture = new DruidStateCapture();
    this.healthCheck = new CompositeHealthCheck<>("druid-health");

    // Add all health checks
    // this.healthCheck.addCheck(new ServerTopologyCheck());
    // this.healthCheck.addCheck(new LeadershipCheck());
    // this.healthCheck.addCheck(new NodeDiscoveryCheck());
    // this.healthCheck.addCheck(new SegmentAvailabilityCheck());
  }

  @Override
  public Class<EmbeddedDruidCluster> getClusterType()
  {
    return EmbeddedDruidCluster.class;
  }

  @Override
  public void restartNode(
      EmbeddedDruidCluster cluster,
      String nodeRole,
      int nodeIndex,
      RestartMode mode
  ) throws Exception
  {
    @SuppressWarnings("unchecked")
    Class<? extends EmbeddedDruidServer<?>> serverType = getServerTypeForRole(nodeRole);
    @SuppressWarnings("unchecked")
    EmbeddedDruidServer<?> server = getServerByIndex(cluster, (Class) serverType, nodeIndex);

    LOG.info("Restarting {} {} (index {}) with mode {}",
             nodeRole, server.getName(), nodeIndex, mode);

    switch (mode) {
      case GRACEFUL:
        restartServerGraceful(server);
        break;
      case CRASH:
        restartServerCrash(server, false);
        break;
      case DELAYED_CRASH:
        restartServerCrash(server, true);
        break;
      default:
        throw new IllegalArgumentException("Unknown restart mode: " + mode);
    }

    LOG.info("Successfully restarted {} {}", nodeRole, server.getName());
  }

  @Override
  public void restartAllNodes(
      EmbeddedDruidCluster cluster,
      String nodeRole,
      RestartMode mode
  ) throws Exception
  {
    @SuppressWarnings("unchecked")
    Class<? extends EmbeddedDruidServer<?>> serverType = getServerTypeForRole(nodeRole);
    @SuppressWarnings("unchecked")
    List<? extends EmbeddedDruidServer<?>> servers = getServersOfType(cluster, (Class) serverType);

    LOG.info("Restarting all {} {}s with mode {}", servers.size(), nodeRole, mode);

    for (int i = 0; i < servers.size(); i++) {
      restartNode(cluster, nodeRole, i, mode);
    }

    LOG.info("Successfully restarted all {} {}s", servers.size(), nodeRole);
  }

  @Override
  public void waitActive(EmbeddedDruidCluster cluster) throws Exception
  {
    LOG.info("Waiting for Druid cluster to become active");

    // Wait for leadership election
    waitForLeadership(cluster);

    // Run health checks
    HealthCheckResult result = healthCheck.checkHealth(cluster);
    if (!result.isPassed()) {
      LOG.warn("Health checks failed: {}", result.getFailures());
      throw new Exception("Cluster health check failed: " + result.getFailures());
    }

    LOG.info("Druid cluster is active and healthy");
  }

  @Override
  public StateCapture<EmbeddedDruidCluster> getStateCapture()
  {
    return stateCapture;
  }

  @Override
  public HealthCheck<EmbeddedDruidCluster> getHealthCheck()
  {
    return healthCheck;
  }

  @Override
  public int getNodeCount(EmbeddedDruidCluster cluster, String nodeRole) throws Exception
  {
    @SuppressWarnings("unchecked")
    Class<? extends EmbeddedDruidServer<?>> serverType = getServerTypeForRole(nodeRole);
    @SuppressWarnings("unchecked")
    int count = getServersOfType(cluster, (Class) serverType).size();
    return count;
  }

  // ========== Private Helper Methods ==========

  /**
   * Performs a graceful restart using stop() -> start() pattern.
   * This is the same approach used in HighAvailabilityTest.
   */
  private void restartServerGraceful(EmbeddedDruidServer<?> server) throws Exception
  {
    LOG.debug("Graceful restart: stopping server {}", server.getName());
    server.stop();

    Thread.sleep(100);  // Brief pause to ensure cleanup

    LOG.debug("Graceful restart: starting server {}", server.getName());
    server.start();
  }

  /**
   * Performs a crash restart by forcefully killing the executor service
   * without calling the lifecycle stop() method. This simulates an abrupt
   * process termination.
   *
   * @param delayed if true, adds a delay before restarting to simulate
   *                partial state propagation windows
   */
  private void restartServerCrash(EmbeddedDruidServer<?> server, boolean delayed) throws Exception
  {
    LOG.debug("CRASH mode restart: forcefully terminating server {}", server.getName());

    try {
      // Access lifecycle via reflection
      Field lifecycleField = EmbeddedDruidServer.class.getDeclaredField("lifecycle");
      lifecycleField.setAccessible(true);

      @SuppressWarnings("unchecked")
      AtomicReference<EmbeddedServerLifecycle> lifecycleRef =
          (AtomicReference<EmbeddedServerLifecycle>) lifecycleField.get(server);

      EmbeddedServerLifecycle serverLifecycle = lifecycleRef.get();
      if (serverLifecycle == null) {
        throw new IllegalStateException("Server not started: " + server.getName());
      }

      // Access executor service and force shutdown without calling lifecycle.stop()
      Field executorField = EmbeddedServerLifecycle.class.getDeclaredField("executorService");
      executorField.setAccessible(true);
      ExecutorService executor = (ExecutorService) executorField.get(serverLifecycle);

      if (executor != null) {
        // Force shutdown without calling lifecycle.stop() - this is the key difference
        executor.shutdownNow();

        // Directly clear the lifecycle reference (mimicking what stop() does but without cleanup)
        lifecycleRef.set(null);
      }

      // Simulate crash delay
      if (delayed) {
        Thread.sleep(500);  // Allow partial state propagation
      } else {
        Thread.sleep(200);
      }

      LOG.debug("CRASH mode restart: restarting server {}", server.getName());
      server.start();
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new Exception("Failed to perform crash restart via reflection: " + e.getMessage(), e);
    }
  }

  /**
   * Gets all servers of a specific type from the cluster using reflection
   * to access the package-protected servers list.
   */
  public <S extends EmbeddedDruidServer<S>> List<S> getServersOfType(
      EmbeddedDruidCluster cluster,
      Class<S> serverType
  ) throws Exception
  {
    List<EmbeddedDruidServer<?>> allServers = getServersList(cluster);
    List<S> filtered = new ArrayList<>();

    for (EmbeddedDruidServer<?> server : allServers) {
      if (serverType.isInstance(server)) {
        filtered.add(serverType.cast(server));
      }
    }

    return filtered;
  }

  /**
   * Gets a server by its index within servers of a specific type.
   */
  private <S extends EmbeddedDruidServer<S>> S getServerByIndex(
      EmbeddedDruidCluster cluster,
      Class<S> serverType,
      int index
  ) throws Exception
  {
    List<S> servers = getServersOfType(cluster, serverType);

    if (index < 0 || index >= servers.size()) {
      throw new IllegalArgumentException(
          "Invalid index " + index + " for " + serverType.getSimpleName() +
          " (count=" + servers.size() + ")"
      );
    }

    return servers.get(index);
  }

  /**
   * Accesses the package-protected servers list from EmbeddedDruidCluster
   * using reflection.
   */
  public List<EmbeddedDruidServer<?>> getServersList(EmbeddedDruidCluster cluster) throws Exception
  {
    try {
      Field serversField = EmbeddedDruidCluster.class.getDeclaredField("servers");
      serversField.setAccessible(true);

      @SuppressWarnings("unchecked")
      List<EmbeddedDruidServer<?>> servers =
          (List<EmbeddedDruidServer<?>>) serversField.get(cluster);

      return servers;
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new Exception("Failed to access cluster servers via reflection: " + e.getMessage(), e);
    }
  }

  /**
   * Maps node role names to Druid server types.
   */
  private Class<? extends EmbeddedDruidServer<?>> getServerTypeForRole(String nodeRole)
  {
    switch (nodeRole.toLowerCase(Locale.ENGLISH)) {
      case "coordinator":
        return EmbeddedCoordinator.class;
      case "overlord":
        return EmbeddedOverlord.class;
      case "indexer":
        return EmbeddedIndexer.class;
      case "broker":
        return EmbeddedBroker.class;
      case "historical":
        return EmbeddedHistorical.class;
      case "router":
        return EmbeddedRouter.class;
      default:
        throw new IllegalArgumentException(
            "Unknown node role: " + nodeRole +
            ". Supported: coordinator, overlord, indexer, broker, historical, router"
        );
    }
  }

  /**
   * Waits for leadership election to complete for Coordinator and Overlord.
   */
  private void waitForLeadership(EmbeddedDruidCluster cluster) throws Exception
  {
    // Wait for coordinator leader (if any coordinators exist)
    List<EmbeddedCoordinator> coordinators = getServersOfType(cluster, EmbeddedCoordinator.class);
    if (!coordinators.isEmpty()) {
      waitForElection(
          coordinators,
          coord -> coord.bindings().coordinatorLeaderSelector().isLeader(),
          "Coordinator"
      );
    }

    // Wait for overlord leader (if any overlords exist)
    List<EmbeddedOverlord> overlords = getServersOfType(cluster, EmbeddedOverlord.class);
    if (!overlords.isEmpty()) {
      waitForElection(
          overlords,
          overlord -> overlord.bindings().overlordLeaderSelector().isLeader(),
          "Overlord"
      );
    }
  }

  /**
   * Waits for a leader to be elected among a list of servers.
   */
  private <S extends EmbeddedDruidServer<S>> void waitForElection(
      List<S> servers,
      Function<S, Boolean> leaderCheck,
      String serverType
  ) throws Exception
  {
    int maxAttempts = 30;
    int attempt = 0;

    while (attempt < maxAttempts) {
      for (S server : servers) {
        try {
          if (leaderCheck.apply(server)) {
            LOG.debug("{} leader elected: {}", serverType, server.getName());
            return;
          }
        }
        catch (Exception e) {
          LOG.debug("Error checking leadership for {}: {}", server.getName(), e.getMessage());
        }
      }

      Thread.sleep(100);
      attempt++;
    }

    throw new Exception(
        "Timeout waiting for " + serverType + " leader election after " +
        (maxAttempts * 100) + "ms"
    );
  }
}
