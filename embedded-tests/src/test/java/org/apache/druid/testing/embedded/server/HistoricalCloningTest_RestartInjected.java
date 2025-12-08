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

package org.apache.druid.testing.embedded.server;

import org.apache.druid.common.utils.IdUtils;
import org.apache.druid.indexing.common.task.IndexTask;
import org.apache.druid.indexing.common.task.TaskBuilder;
import org.apache.druid.query.DruidMetrics;
import org.apache.druid.server.coordinator.CoordinatorDynamicConfig;
import org.apache.druid.server.coordinator.rules.ForeverLoadRule;
import org.apache.druid.testing.embedded.EmbeddedBroker;
import org.apache.druid.testing.embedded.EmbeddedCoordinator;
import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedHistorical;
import org.apache.druid.testing.embedded.EmbeddedIndexer;
import org.apache.druid.testing.embedded.EmbeddedOverlord;
import org.apache.druid.testing.embedded.EmbeddedRouter;
import org.apache.druid.testing.embedded.indexing.Resources;
import org.apache.druid.testing.embedded.junit5.EmbeddedClusterTestBase;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class HistoricalCloningTest_RestartInjected extends EmbeddedClusterTestBase
{
  private final EmbeddedHistorical historical1 = new EmbeddedHistorical();
  private final EmbeddedHistorical historical2 = new EmbeddedHistorical()
      .addProperty("druid.plaintextPort", "7083");
  private final EmbeddedCoordinator coordinator1 = new EmbeddedCoordinator();
  private final EmbeddedCoordinator coordinator2 = new EmbeddedCoordinator()
      .addProperty("druid.plaintextPort", "7081");
  private final EmbeddedOverlord overlord = new EmbeddedOverlord();

  @Override
  protected EmbeddedDruidCluster createCluster()
  {
    return EmbeddedDruidCluster.withEmbeddedDerbyAndZookeeper()
                               .useLatchableEmitter()
                               .addServer(overlord)
                               .addServer(coordinator1)
                               .addServer(coordinator2)
                               .addServer(new EmbeddedIndexer())
                               .addServer(historical1)
                               .addServer(new EmbeddedBroker())
                               .addServer(new EmbeddedRouter());
  }

  @Test
  public void test_cloneHistoricals_inTurboMode_duringCoordinatorLeaderSwitch() throws Exception
  {
    cluster.callApi().onLeaderCoordinator(
        c -> c.updateRulesForDatasource(
            dataSource,
            List.of(new ForeverLoadRule(Map.of("_default_tier", 1), null))
        )
    );
    RestartFramework.at("after_rule_update")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    cluster.callApi().onLeaderCoordinator(
        c -> c.updateCoordinatorDynamicConfig(
            CoordinatorDynamicConfig
                .builder()
                .withCloneServers(Map.of("localhost:7083", "localhost:8083"))
                .withTurboLoadingNodes(Set.of("localhost:7083"))
                .build()
        )
    );
    RestartFramework.at("after_coordinator_config_update")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    runIngestion();
    RestartFramework.at("after_ingestion")
        .on(cluster)
        .restart("overlord")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Wait for segments to be loaded on historical1
    coordinator1.latchableEmitter().waitForEventAggregate(
        event -> event.hasMetricName("segment/loadQueue/success")
                      .hasDimension(DruidMetrics.DATASOURCE, dataSource),
        agg -> agg.hasSumAtLeast(10)
    );
    RestartFramework.at("after_segment_load_success")
        .on(cluster)
        .restart("historical")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    coordinator1.latchableEmitter().waitForEventAggregate(
        event -> event.hasMetricName("segment/loadQueue/success")
                      .hasDimension("server", historical1.bindings().selfNode().getHostAndPort())
                      .hasDimension("description", "LOAD: NORMAL"),
        agg -> agg.hasSumAtLeast(10)
    );
    RestartFramework.at("after_normal_load_complete")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Switch coordinator leader to force syncer to reset
    coordinator1.stop();

    // Wait for a few coordinator runs so that the server views are refreshed
    coordinator2.latchableEmitter().waitForEventAggregate(
        event -> event.hasMetricName("coordinator/time")
                      .hasDimension("dutyGroup", "HistoricalManagementDuties"),
        agg -> agg.hasCountAtLeast(2)
    );
    RestartFramework.at("after_coordinator_runs")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Add historical2 to the cluster
    cluster.addServer(historical2);
    historical2.start();
    RestartFramework.at("after_historical2_start")
        .on(cluster)
        .restart("broker")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Wait for the clones to be loaded
    coordinator2.latchableEmitter().waitForEventAggregate(
        event -> event.hasMetricName("segment/clone/assigned/count")
                      .hasDimension("server", historical2.bindings().selfNode().getHostAndPort()),
        agg -> agg.hasSumAtLeast(10)
    );
    RestartFramework.at("after_clone_assigned")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    coordinator2.latchableEmitter().waitForEventAggregate(
        event -> event.hasMetricName("segment/loadQueue/success")
                      .hasDimension("server", historical2.bindings().selfNode().getHostAndPort())
                      .hasDimension("description", "LOAD: TURBO"),
        agg -> agg.hasSumAtLeast(10)
    );
    RestartFramework.at("after_turbo_load_complete")
        .on(cluster)
        .restart("historical")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
  }

  private void runIngestion()
  {
    final String taskId = IdUtils.getRandomId();
    final IndexTask task = createIndexTaskForInlineData(taskId);

    cluster.callApi().runTask(task, overlord);
  }

  private IndexTask createIndexTaskForInlineData(String taskId)
  {
    return TaskBuilder.ofTypeIndex()
                      .dataSource(dataSource)
                      .isoTimestampColumn("time")
                      .csvInputFormatWithColumns("time", "item", "value")
                      .inlineInputSourceWithData(Resources.InlineData.CSV_10_DAYS)
                      .segmentGranularity("DAY")
                      .dimensions()
                      .withId(taskId);
  }
}
