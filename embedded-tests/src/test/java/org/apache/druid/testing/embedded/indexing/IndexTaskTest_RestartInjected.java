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

package org.apache.druid.testing.embedded.indexing;

import org.apache.druid.indexing.common.task.IndexTask;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.guava.Comparators;
import org.apache.druid.testing.embedded.EmbeddedBroker;
import org.apache.druid.testing.embedded.EmbeddedClusterApis;
import org.apache.druid.testing.embedded.EmbeddedCoordinator;
import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedHistorical;
import org.apache.druid.testing.embedded.EmbeddedIndexer;
import org.apache.druid.testing.embedded.EmbeddedOverlord;
import org.apache.druid.testing.embedded.EmbeddedRouter;
import org.apache.druid.testing.embedded.junit5.EmbeddedClusterTestBase;
import org.apache.druid.timeline.DataSegment;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Embedded tests for batch {@link IndexTask} using inline datasources.
 */
public class IndexTaskTest_RestartInjected extends EmbeddedClusterTestBase
{
  protected final EmbeddedBroker broker = new EmbeddedBroker();
  protected final EmbeddedIndexer indexer = new EmbeddedIndexer().addProperty("druid.worker.capacity", "25");
  protected final EmbeddedOverlord overlord = new EmbeddedOverlord();
  protected final EmbeddedHistorical historical = new EmbeddedHistorical();
  protected final EmbeddedCoordinator coordinator = new EmbeddedCoordinator();

  @Override
  public EmbeddedDruidCluster createCluster()
  {
    return EmbeddedDruidCluster.withEmbeddedDerbyAndZookeeper()
                               .useLatchableEmitter()
                               .addServer(coordinator)
                               .addServer(overlord)
                               .addServer(indexer)
                               .addServer(historical)
                               .addServer(broker)
                               .addServer(new EmbeddedRouter());
  }

  @Test
  @Timeout(20)
  public void test_runIndexTask_forInlineDatasource()
  {
    final String taskId = EmbeddedClusterApis.newTaskId(dataSource);
    final IndexTask task = createIndexTaskForInlineData(
        taskId,
        Resources.InlineData.CSV_10_DAYS
    );

    cluster.callApi().runTask(task, overlord);

    RestartFramework.at("after_task_submit")
        .on(cluster)
        .restart("overlord")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Verify that the task created 10 DAY-granularity segments
    final List<DataSegment> segments = new ArrayList<>(
        overlord.bindings().segmentsMetadataStorage().retrieveAllUsedSegments(dataSource, null)
    );

    RestartFramework.at("after_segment_metadata_retrieval")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    segments.sort(
        (o1, o2) -> Comparators.intervalsByStartThenEnd()
                               .compare(o1.getInterval(), o2.getInterval())
    );

    Assertions.assertEquals(10, segments.size());
    DateTime start = DateTimes.of("2025-06-01");
    for (DataSegment segment : segments) {
      Assertions.assertEquals(dataSource, segment.getDataSource());
      Assertions.assertEquals(new Interval(start, Period.days(1)), segment.getInterval());
      start = start.plusDays(1);
    }

    cluster.callApi().waitForAllSegmentsToBeAvailable(dataSource, coordinator, broker);

    RestartFramework.at("after_segments_available")
        .on(cluster)
        .restart("historical")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    Assertions.assertEquals(
        Resources.InlineData.CSV_10_DAYS,
        cluster.runSql("SELECT * FROM %s", dataSource)
    );

    RestartFramework.at("after_query")
        .on(cluster)
        .restart("broker")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    Assertions.assertEquals("10", cluster.runSql("SELECT COUNT(*) FROM %s", dataSource));
  }

  @Test
  @Timeout(20)
  public void test_run10Tasks_concurrently()
  {
    runTasksConcurrently(10);
  }

  @Test
  @Timeout(20)
  public void test_run25Tasks_oneByOne()
  {
    for (int i = 0; i < 25; ++i) {
      runTasksConcurrently(1);
    }
  }

  @Test
  @Timeout(20)
  public void test_run25Tasks_concurrently()
  {
    runTasksConcurrently(25);
  }

  @Test
  @Timeout(20)
  public void test_run100Tasks_concurrently()
  {
    runTasksConcurrently(100);
  }

  private IndexTask createIndexTaskForInlineData(String taskId, String inlineDataCsv)
  {
    return MoreResources.Task.BASIC_INDEX
        .get()
        .inlineInputSourceWithData(inlineDataCsv)
        .dataSource(dataSource)
        .withId(taskId);
  }

  /**
   * Runs the given number of concurrent batch {@link IndexTask} for {@link #dataSource}.
   * Each task ingests a single segment containing 1 row of data.
   */
  private void runTasksConcurrently(int count)
  {
    final DateTime jan1 = DateTimes.of("2025-01-01");

    final List<String> taskIds = IntStream.range(0, count).mapToObj(
        i -> EmbeddedClusterApis.newTaskId(dataSource)
    ).collect(Collectors.toList());

    int index = 0;
    for (String taskId : taskIds) {
      index++;
      final IndexTask task = createIndexTaskForInlineData(
          taskId,
          StringUtils.format(
              "%s,%s,%d",
              jan1.plusDays(index), "item " + index, index
          )
      );
      cluster.callApi().onLeaderOverlord(o -> o.runTask(taskId, task));
    }

    RestartFramework.at("after_concurrent_tasks_submit")
        .on(cluster)
        .restart("overlord")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    for (String taskId : taskIds) {
      cluster.callApi().waitForTaskToSucceed(taskId, overlord);
    }

    RestartFramework.at("after_concurrent_tasks_complete")
        .on(cluster)
        .restart("indexer")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
  }
}
