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

import org.apache.druid.timeline.DataSegment;
import org.apache.druid.timeline.SegmentId;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Captures and verifies Druid cluster state before and after restarts.
 *
 * Captures:
 * - Server topology (count of each server type)
 * - Leadership status (which servers are leaders)
 * - Datasources (list of all datasources)
 * - Segment counts per datasource
 * - Segment IDs for deep verification
 *
 * Verifies:
 * - Server counts preserved
 * - Leadership re-established
 * - Segment metadata preserved (no data loss)
 */
public class DruidStateCapture extends AbstractStateCapture<EmbeddedDruidCluster>
{
  private static final Logger LOG = LoggerFactory.getLogger(DruidStateCapture.class);

  @Override
  public ClusterState captureState(EmbeddedDruidCluster cluster) throws Exception
  {
    LOG.info("Capturing Druid cluster state");

    Map<String, Object> state = new HashMap<>();

    try {
      // Create adapter instance to access server listing methods
      DruidClusterAdapter adapter = new DruidClusterAdapter();

      // 1. Capture server topology
      Map<String, Integer> serverCounts = captureServerCounts(cluster, adapter);
      state.put("server_counts", serverCounts);

      // 2. Capture leadership status
      Map<String, String> leadership = captureLeadershipStatus(cluster, adapter);
      state.put("leadership", leadership);

      // 3. Discover datasources
      Set<String> datasources = discoverDatasources(cluster);
      state.put("datasources", datasources);

      // 4. Capture segment counts per datasource
      Map<String, Integer> segmentCounts = new HashMap<>();
      if (!datasources.isEmpty()) {
        List<EmbeddedOverlord> overlords = adapter.getServersOfType(cluster, EmbeddedOverlord.class);
        if (!overlords.isEmpty()) {
          EmbeddedOverlord overlord = findLeaderOverlord(overlords);
          if (overlord == null) {
            overlord = overlords.get(0);  // Fallback to first overlord
          }

          for (String datasource : datasources) {
            try {
              Set<DataSegment> segments = cluster.callApi().getVisibleUsedSegments(datasource, overlord);
              segmentCounts.put(datasource, segments.size());
            }
            catch (Exception e) {
              LOG.warn("Failed to get segment count for datasource {}: {}", datasource, e.getMessage());
              segmentCounts.put(datasource, 0);
            }
          }

          state.put("segment_counts", segmentCounts);

          // 5. Capture segment IDs (optional, for deep verification)
          Map<String, Set<String>> segmentIds = captureSegmentIds(cluster, datasources, overlord);
          state.put("segment_ids", segmentIds);
        }
      }

      LOG.info("Captured state: {} servers, {} datasources, {} total segments",
               serverCounts.values().stream().mapToInt(Integer::intValue).sum(),
               datasources.size(),
               segmentCounts.values().stream().mapToInt(Integer::intValue).sum());

    }
    catch (Exception e) {
      LOG.error("Failed to capture Druid state", e);
      throw e;
    }

    return new DefaultClusterState(state);
  }

  @Override
  protected void verifyCustomInvariants(
      EmbeddedDruidCluster cluster,
      ClusterState before,
      ClusterState after
  ) throws Exception
  {
    LOG.info("Verifying Druid-specific invariants");

    Map<String, Object> beforeMap = before.getStateMap();
    Map<String, Object> afterMap = after.getStateMap();

    // 1. Verify server topology preserved
    @SuppressWarnings("unchecked")
    Map<String, Integer> beforeCounts = (Map<String, Integer>) beforeMap.get("server_counts");
    @SuppressWarnings("unchecked")
    Map<String, Integer> afterCounts = (Map<String, Integer>) afterMap.get("server_counts");

    if (beforeCounts != null && afterCounts != null) {
      for (String serverType : beforeCounts.keySet()) {
        Integer beforeCount = beforeCounts.get(serverType);
        Integer afterCount = afterCounts.get(serverType);

        if (!beforeCount.equals(afterCount)) {
          throw new StateVerificationException(
              "Server count changed for " + serverType + ": " +
              beforeCount + " -> " + afterCount
          );
        }
      }
      LOG.info("Server topology verified: all server counts preserved");
    }

    // 2. Verify leadership re-established
    @SuppressWarnings("unchecked")
    Map<String, String> afterLeadership = (Map<String, String>) afterMap.get("leadership");

    if (afterLeadership != null) {
      long leaderCount = afterLeadership.values().stream()
                                        .filter(status -> "leader".equals(status))
                                        .count();

      if (leaderCount == 0) {
        throw new StateVerificationException("No leaders elected after restart");
      }
      LOG.info("Leadership verified: {} leaders elected", leaderCount);
    }

    // 3. Verify segment preservation
    @SuppressWarnings("unchecked")
    Map<String, Integer> beforeSegments = (Map<String, Integer>) beforeMap.get("segment_counts");
    @SuppressWarnings("unchecked")
    Map<String, Integer> afterSegments = (Map<String, Integer>) afterMap.get("segment_counts");

    if (beforeSegments != null && afterSegments != null) {
      for (String datasource : beforeSegments.keySet()) {
        Integer beforeCount = beforeSegments.get(datasource);
        Integer afterCount = afterSegments.get(datasource);

        if (afterCount == null) {
          throw new StateVerificationException(
              "Datasource disappeared after restart: " + datasource
          );
        }

        if (afterCount < beforeCount) {
          throw new StateVerificationException(
              "Segment count decreased for " + datasource + ": " +
              beforeCount + " -> " + afterCount
          );
        }

        if (afterCount > beforeCount) {
          LOG.debug("Segment count increased for {}: {} -> {} (expected for ongoing ingestion)",
                    datasource, beforeCount, afterCount);
        }
      }
      LOG.info("Segment metadata verified: {} datasources checked, no data loss",
               beforeSegments.size());
    }

    // 4. Verify segment IDs (deep check)
    @SuppressWarnings("unchecked")
    Map<String, Set<String>> beforeIds = (Map<String, Set<String>>) beforeMap.get("segment_ids");
    @SuppressWarnings("unchecked")
    Map<String, Set<String>> afterIds = (Map<String, Set<String>>) afterMap.get("segment_ids");

    if (beforeIds != null && afterIds != null) {
      for (String datasource : beforeIds.keySet()) {
        Set<String> beforeSet = beforeIds.get(datasource);
        Set<String> afterSet = afterIds.getOrDefault(datasource, Collections.emptySet());

        Set<String> missing = new HashSet<>(beforeSet);
        missing.removeAll(afterSet);

        if (!missing.isEmpty()) {
          throw new StateVerificationException(
              "Segments lost after restart for " + datasource + ": " + missing
          );
        }
      }
      LOG.info("Deep segment verification passed: all segment IDs preserved");
    }
  }

  // ========== Private Helper Methods ==========

  /**
   * Captures the count of each server type in the cluster.
   */
  private Map<String, Integer> captureServerCounts(
      EmbeddedDruidCluster cluster,
      DruidClusterAdapter adapter
  ) throws Exception
  {
    Map<String, Integer> counts = new HashMap<>();
    counts.put("coordinator", adapter.getServersOfType(cluster, EmbeddedCoordinator.class).size());
    counts.put("overlord", adapter.getServersOfType(cluster, EmbeddedOverlord.class).size());
    counts.put("indexer", adapter.getServersOfType(cluster, EmbeddedIndexer.class).size());
    counts.put("broker", adapter.getServersOfType(cluster, EmbeddedBroker.class).size());
    counts.put("historical", adapter.getServersOfType(cluster, EmbeddedHistorical.class).size());
    counts.put("router", adapter.getServersOfType(cluster, EmbeddedRouter.class).size());
    return counts;
  }

  /**
   * Captures which servers are currently leaders.
   */
  private Map<String, String> captureLeadershipStatus(
      EmbeddedDruidCluster cluster,
      DruidClusterAdapter adapter
  ) throws Exception
  {
    Map<String, String> leadership = new HashMap<>();

    // Check coordinator leadership
    List<EmbeddedCoordinator> coordinators = adapter.getServersOfType(cluster, EmbeddedCoordinator.class);
    for (int i = 0; i < coordinators.size(); i++) {
      try {
        boolean isLeader = coordinators.get(i).bindings().coordinatorLeaderSelector().isLeader();
        leadership.put("coordinator_" + i, isLeader ? "leader" : "follower");
      }
      catch (Exception e) {
        LOG.debug("Failed to check coordinator {} leadership: {}", i, e.getMessage());
        leadership.put("coordinator_" + i, "unknown");
      }
    }

    // Check overlord leadership
    List<EmbeddedOverlord> overlords = adapter.getServersOfType(cluster, EmbeddedOverlord.class);
    for (int i = 0; i < overlords.size(); i++) {
      try {
        boolean isLeader = overlords.get(i).bindings().overlordLeaderSelector().isLeader();
        leadership.put("overlord_" + i, isLeader ? "leader" : "follower");
      }
      catch (Exception e) {
        LOG.debug("Failed to check overlord {} leadership: {}", i, e.getMessage());
        leadership.put("overlord_" + i, "unknown");
      }
    }

    return leadership;
  }

  /**
   * Discovers all datasources in the cluster by querying sys.segments.
   */
  private Set<String> discoverDatasources(EmbeddedDruidCluster cluster)
  {
    try {
      String sql = "SELECT DISTINCT datasource FROM sys.segments";
      String csvResult = cluster.runSql(sql);

      Set<String> datasources = new HashSet<>();
      String[] lines = csvResult.split("\n");

      // Skip header line and parse datasource names
      for (int i = 1; i < lines.length; i++) {
        String datasource = lines[i].trim();
        if (!datasource.isEmpty()) {
          datasources.add(datasource);
        }
      }

      LOG.debug("Discovered {} datasources: {}", datasources.size(), datasources);
      return datasources;
    }
    catch (Exception e) {
      LOG.warn("Failed to discover datasources: {}", e.getMessage());
      return Collections.emptySet();
    }
  }

  /**
   * Captures segment IDs for all datasources for deep verification.
   */
  private Map<String, Set<String>> captureSegmentIds(
      EmbeddedDruidCluster cluster,
      Set<String> datasources,
      EmbeddedOverlord overlord
  )
  {
    Map<String, Set<String>> segmentIds = new HashMap<>();

    for (String datasource : datasources) {
      try {
        Set<DataSegment> segments = cluster.callApi().getVisibleUsedSegments(datasource, overlord);

        Set<String> ids = segments.stream()
                                  .map(DataSegment::getId)
                                  .map(SegmentId::toString)
                                  .collect(Collectors.toSet());

        segmentIds.put(datasource, ids);
      }
      catch (Exception e) {
        LOG.warn("Failed to capture segment IDs for datasource {}: {}", datasource, e.getMessage());
        segmentIds.put(datasource, Collections.emptySet());
      }
    }

    return segmentIds;
  }

  /**
   * Finds the leader overlord from a list of overlords.
   */
  private EmbeddedOverlord findLeaderOverlord(List<EmbeddedOverlord> overlords)
  {
    for (EmbeddedOverlord overlord : overlords) {
      try {
        if (overlord.bindings().overlordLeaderSelector().isLeader()) {
          return overlord;
        }
      }
      catch (Exception e) {
        LOG.debug("Failed to check overlord {} leadership: {}", overlord.getName(), e.getMessage());
      }
    }
    return null;
  }
}
