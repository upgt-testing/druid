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
import org.apache.druid.testing.embedded.EmbeddedCoordinator;
import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedOverlord;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Health check that verifies coordinator and overlord leaders are elected.
 *
 * Checks that:
 * - If coordinators exist, at least one is elected as leader
 * - If overlords exist, at least one is elected as leader
 */
public class LeadershipCheck implements HealthCheck<EmbeddedDruidCluster>
{
  private static final Logger LOG = LoggerFactory.getLogger(LeadershipCheck.class);

  @Override
  public HealthCheckResult checkHealth(EmbeddedDruidCluster cluster) throws Exception
  {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    try {
      DruidClusterAdapter adapter = new DruidClusterAdapter();

      // Check coordinator leadership
      boolean coordinatorLeaderFound = false;
      List<EmbeddedCoordinator> coordinators = adapter.getServersOfType(cluster, EmbeddedCoordinator.class);

      for (EmbeddedCoordinator coord : coordinators) {
        try {
          if (coord.bindings().coordinatorLeaderSelector().isLeader()) {
            coordinatorLeaderFound = true;
            LOG.debug("Coordinator leader found: {}", coord.getName());
            break;
          }
        }
        catch (Exception e) {
          LOG.debug("Error checking coordinator {} leadership: {}", coord.getName(), e.getMessage());
        }
      }

      if (!coordinators.isEmpty() && !coordinatorLeaderFound) {
        result.addFailure("No coordinator leader elected (checked " + coordinators.size() + " coordinators)");
      }
      result.addMetric("coordinator_count", coordinators.size());
      result.addMetric("coordinator_leader_elected", coordinatorLeaderFound);

      // Check overlord leadership
      boolean overlordLeaderFound = false;
      List<EmbeddedOverlord> overlords = adapter.getServersOfType(cluster, EmbeddedOverlord.class);

      for (EmbeddedOverlord overlord : overlords) {
        try {
          if (overlord.bindings().overlordLeaderSelector().isLeader()) {
            overlordLeaderFound = true;
            LOG.debug("Overlord leader found: {}", overlord.getName());
            break;
          }
        }
        catch (Exception e) {
          LOG.debug("Error checking overlord {} leadership: {}", overlord.getName(), e.getMessage());
        }
      }

      if (!overlords.isEmpty() && !overlordLeaderFound) {
        result.addFailure("No overlord leader elected (checked " + overlords.size() + " overlords)");
      }
      result.addMetric("overlord_count", overlords.size());
      result.addMetric("overlord_leader_elected", overlordLeaderFound);

      if (result.isPassed()) {
        LOG.debug("Leadership check passed: leaders elected");
      }
    }
    catch (Exception e) {
      result.addFailure("Failed to check leadership: " + e.getMessage());
      LOG.warn("Leadership check failed", e);
    }

    return result;
  }

  @Override
  public String getName()
  {
    return "leadership";
  }
}
