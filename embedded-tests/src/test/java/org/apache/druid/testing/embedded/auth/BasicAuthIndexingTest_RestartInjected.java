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

package org.apache.druid.testing.embedded.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.druid.rpc.RequestBuilder;
import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedRouter;
import org.apache.druid.testing.embedded.indexing.IndexTaskTest_RestartInjected;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import java.util.List;

public class BasicAuthIndexingTest_RestartInjected extends IndexTaskTest_RestartInjected
{
  @Override
  public EmbeddedDruidCluster createCluster()
  {
    return EmbeddedDruidCluster
        .withEmbeddedDerbyAndZookeeper()
        .addResource(new EmbeddedBasicAuthResource())
        .useLatchableEmitter()
        .addServer(coordinator)
        .addServer(overlord)
        .addServer(indexer)
        .addServer(historical)
        .addServer(broker)
        .addServer(new EmbeddedRouter())
        .addCommonProperty("druid.indexer.autoscale.doAutoscale", "true");
  }

  @Test
  public void test_getScalingStats_redirectFromCoordinatorToOverlord()
  {
    RestartFramework.at("before_scaling_stats_request")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    final List<Object> response = cluster.callApi().serviceClient().onLeaderCoordinator(
        mapper -> new RequestBuilder(HttpMethod.GET, "/druid/indexer/v1/scaling"),
        new TypeReference<>() {}
    );

    RestartFramework.at("after_scaling_stats_request")
        .on(cluster)
        .restart("overlord")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    Assertions.assertNotNull(response);
    Assertions.assertTrue(response.isEmpty());
  }
}
