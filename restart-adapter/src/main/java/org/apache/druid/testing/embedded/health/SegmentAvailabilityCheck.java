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

import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Health check that verifies segments are available for querying.
 *
 * Checks that:
 * - All non-overshadowed segments are marked as available
 * - Segments are loaded on historical nodes
 */
public class SegmentAvailabilityCheck implements HealthCheck<EmbeddedDruidCluster>
{
  private static final Logger LOG = LoggerFactory.getLogger(SegmentAvailabilityCheck.class);

  @Override
  public HealthCheckResult checkHealth(EmbeddedDruidCluster cluster) throws Exception
  {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    try {
      // Discover datasources
      Set<String> datasources = discoverDatasources(cluster);

      if (datasources.isEmpty()) {
        LOG.debug("No datasources found, skipping segment availability check");
        result.addMetric("datasources_checked", 0);
        result.addMetric("total_segments", 0);
        result.addMetric("available_segments", 0);
        return result;
      }

      int totalSegments = 0;
      int availableSegments = 0;

      for (String datasource : datasources) {
        try {
          // Query for total non-overshadowed segments
          String totalSql = "SELECT COUNT(*) FROM sys.segments " +
                           "WHERE datasource = '" + escapeSql(datasource) + "' " +
                           "AND is_overshadowed = 0";
          String totalStr = cluster.runSql(totalSql).trim();

          // Skip header if present
          String[] totalLines = totalStr.split("\n");
          String totalValue = totalLines.length > 1 ? totalLines[1].trim() : totalStr;
          int total = Integer.parseInt(totalValue);

          // Query for available segments
          String availableSql = "SELECT COUNT(*) FROM sys.segments " +
                               "WHERE datasource = '" + escapeSql(datasource) + "' " +
                               "AND is_overshadowed = 0 AND is_available = 1";
          String availableStr = cluster.runSql(availableSql).trim();

          String[] availableLines = availableStr.split("\n");
          String availableValue = availableLines.length > 1 ? availableLines[1].trim() : availableStr;
          int available = Integer.parseInt(availableValue);

          totalSegments += total;
          availableSegments += available;

          result.addMetric(datasource + "_total_segments", total);
          result.addMetric(datasource + "_available_segments", available);

          if (available < total) {
            result.addFailure(
                "Not all segments available for " + datasource +
                ": " + available + "/" + total
            );
          } else if (total > 0) {
            LOG.debug("All segments available for datasource {}: {}", datasource, total);
          }
        }
        catch (Exception e) {
          LOG.warn("Failed to check segment availability for datasource {}: {}", datasource, e.getMessage());
          result.addFailure("Failed to check datasource " + datasource + ": " + e.getMessage());
        }
      }

      result.addMetric("datasources_checked", datasources.size());
      result.addMetric("total_segments", totalSegments);
      result.addMetric("available_segments", availableSegments);

      if (result.isPassed()) {
        LOG.debug("Segment availability check passed: {}/{} segments available across {} datasources",
                  availableSegments, totalSegments, datasources.size());
      }
    }
    catch (Exception e) {
      result.addFailure("Failed to check segment availability: " + e.getMessage());
      LOG.warn("Segment availability check failed", e);
    }

    return result;
  }

  @Override
  public String getName()
  {
    return "segment-availability";
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

      return datasources;
    }
    catch (Exception e) {
      LOG.warn("Failed to discover datasources: {}", e.getMessage());
      return Collections.emptySet();
    }
  }

  /**
   * Escapes single quotes in SQL strings.
   */
  private String escapeSql(String value)
  {
    return org.apache.druid.java.util.common.StringUtils.replace(value, "'", "''");
  }
}
