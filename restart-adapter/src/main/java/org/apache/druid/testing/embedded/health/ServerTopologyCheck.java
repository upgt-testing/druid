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

package org.apache.druid.testing.embedded.health;

import org.apache.druid.testing.embedded.DruidClusterAdapter;
import org.apache.druid.testing.embedded.EmbeddedBroker;
import org.apache.druid.testing.embedded.EmbeddedCoordinator;
import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedHistorical;
import org.apache.druid.testing.embedded.EmbeddedIndexer;
import org.apache.druid.testing.embedded.EmbeddedOverlord;
import org.apache.druid.testing.embedded.EmbeddedRouter;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Health check that verifies all servers in the cluster are running and
 * discoverable via the sys.servers table.
 *
 * Compares expected server counts (from cluster configuration) with
 * discovered server counts (from sys.servers query).
 */
public class ServerTopologyCheck implements HealthCheck<EmbeddedDruidCluster>
{
  private static final Logger LOG = LoggerFactory.getLogger(ServerTopologyCheck.class);

  @Override
  public HealthCheckResult checkHealth(EmbeddedDruidCluster cluster) throws Exception
  {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    try {
      // Get expected server counts
      Map<String, Integer> expectedCounts = getExpectedServerCounts(cluster);

      // Query sys.servers table for discovered counts
      Map<String, Integer> discoveredCounts = getDiscoveredServerCounts(cluster);

      // Compare expected vs discovered
      for (String serverType : expectedCounts.keySet()) {
        Integer expected = expectedCounts.get(serverType);
        Integer discovered = discoveredCounts.getOrDefault(serverType, 0);

        result.addMetric(serverType + "_expected", expected);
        result.addMetric(serverType + "_discovered", discovered);

        if (!expected.equals(discovered)) {
          result.addFailure(
              "Server count mismatch for " + serverType +
              ": expected=" + expected + ", discovered=" + discovered
          );
        }
      }

      if (result.isPassed()) {
        LOG.debug("Server topology check passed: all servers discovered");
      }
    }
    catch (Exception e) {
      result.addFailure("Failed to check server topology: " + e.getMessage());
      LOG.warn("Server topology check failed", e);
    }

    return result;
  }

  @Override
  public String getName()
  {
    return "server-topology";
  }

  /**
   * Gets expected server counts from the cluster configuration.
   */
  private Map<String, Integer> getExpectedServerCounts(EmbeddedDruidCluster cluster) throws Exception
  {
    DruidClusterAdapter adapter = new DruidClusterAdapter();
    Map<String, Integer> counts = new HashMap<>();

    counts.put("coordinator", adapter.getServersOfType(cluster, EmbeddedCoordinator.class).size());
    counts.put("overlord", adapter.getServersOfType(cluster, EmbeddedOverlord.class).size());
    counts.put("broker", adapter.getServersOfType(cluster, EmbeddedBroker.class).size());
    counts.put("historical", adapter.getServersOfType(cluster, EmbeddedHistorical.class).size());
    counts.put("indexer", adapter.getServersOfType(cluster, EmbeddedIndexer.class).size());
    counts.put("router", adapter.getServersOfType(cluster, EmbeddedRouter.class).size());

    return counts;
  }

  /**
   * Gets discovered server counts by querying sys.servers table.
   */
  private Map<String, Integer> getDiscoveredServerCounts(EmbeddedDruidCluster cluster)
  {
    Map<String, Integer> counts = new HashMap<>();

    try {
      String sql = "SELECT server_type, COUNT(*) as count FROM sys.servers GROUP BY server_type";
      String csvResult = cluster.runSql(sql);

      // Parse CSV result
      String[] lines = csvResult.split("\n");
      for (int i = 1; i < lines.length; i++) {  // Skip header
        String line = lines[i].trim();
        if (!line.isEmpty()) {
          String[] parts = line.split(",");
          if (parts.length >= 2) {
            String serverType = parts[0].trim().toLowerCase(Locale.ENGLISH);
            int count = Integer.parseInt(parts[1].trim());
            counts.put(serverType, count);
          }
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Failed to query sys.servers: {}", e.getMessage());
    }

    return counts;
  }
}
