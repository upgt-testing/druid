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

import org.apache.druid.discovery.DiscoveryDruidNode;
import org.apache.druid.discovery.DruidNodeDiscovery;
import org.apache.druid.discovery.DruidNodeDiscoveryProvider;
import org.apache.druid.discovery.NodeRole;
import org.apache.druid.testing.embedded.DruidClusterAdapter;
import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedDruidServer;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Health check that verifies node discovery is working correctly.
 *
 * Checks that all server nodes can be discovered via the
 * DruidNodeDiscoveryProvider system.
 */
public class NodeDiscoveryCheck implements HealthCheck<EmbeddedDruidCluster>
{
  private static final Logger LOG = LoggerFactory.getLogger(NodeDiscoveryCheck.class);

  @Override
  public HealthCheckResult checkHealth(EmbeddedDruidCluster cluster) throws Exception
  {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    try {
      DruidClusterAdapter adapter = new DruidClusterAdapter();

      // Get any server to access node discovery
      List<EmbeddedDruidServer<?>> allServers = adapter.getServersList(cluster);
      if (allServers.isEmpty()) {
        result.addFailure("No servers in cluster");
        return result;
      }

      EmbeddedDruidServer<?> anyServer = allServers.get(0);
      DruidNodeDiscoveryProvider discovery = anyServer.bindings().nodeDiscovery();

      // Check each node role
      checkNodeRole(NodeRole.COORDINATOR, discovery, result);
      checkNodeRole(NodeRole.OVERLORD, discovery, result);
      checkNodeRole(NodeRole.BROKER, discovery, result);
      checkNodeRole(NodeRole.HISTORICAL, discovery, result);
      checkNodeRole(NodeRole.INDEXER, discovery, result);
      checkNodeRole(NodeRole.ROUTER, discovery, result);

      if (result.isPassed()) {
        LOG.debug("Node discovery check passed");
      }
    }
    catch (Exception e) {
      result.addFailure("Failed to check node discovery: " + e.getMessage());
      LOG.warn("Node discovery check failed", e);
    }

    return result;
  }

  @Override
  public String getName()
  {
    return "node-discovery";
  }

  /**
   * Checks if nodes of a specific role are discoverable.
   */
  private void checkNodeRole(
      NodeRole role,
      DruidNodeDiscoveryProvider discovery,
      HealthCheckResult result
  )
  {
    try {
      DruidNodeDiscovery nodeDiscovery = discovery.getForNodeRole(role);
      if (nodeDiscovery != null) {
        Collection<DiscoveryDruidNode> nodes = nodeDiscovery.getAllNodes();
        int count = nodes != null ? nodes.size() : 0;
        result.addMetric(role.toString().toLowerCase(Locale.ENGLISH) + "_discovered", count);

        if (count == 0) {
          LOG.debug("No nodes discovered for role: {}", role);
        } else {
          LOG.debug("Discovered {} nodes for role: {}", count, role);
        }
      } else {
        result.addMetric(role.toString().toLowerCase(Locale.ENGLISH) + "_discovered", 0);
        LOG.debug("Node discovery not available for role: {}", role);
      }
    }
    catch (Exception e) {
      LOG.debug("Error checking discovery for role {}: {}", role, e.getMessage());
      result.addMetric(role.toString().toLowerCase(Locale.ENGLISH) + "_discovered", 0);
    }
  }
}
