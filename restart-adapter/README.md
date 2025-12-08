# Druid Restart Testing Adapter

This module provides a restart adapter for Apache Druid's `EmbeddedDruidCluster` to enable systematic restart testing using the RestartTestingFramework.

## Overview

The adapter supports restarting individual Druid servers with different crash modes:
- **GRACEFUL**: Clean shutdown followed by restart (uses `stop()` then `start()`)
- **CRASH**: Forced termination without lifecycle cleanup (forcefully kills executor)
- **DELAYED_CRASH**: Crash with delay before restart (simulates partial state propagation)

## Supported Server Types

- `coordinator` - Coordinator servers for segment assignment
- `overlord` - Overlord servers for task management
- `broker` - Broker servers for query execution
- `historical` - Historical servers for segment storage
- `indexer` - Indexer servers for running ingestion tasks
- `router` - Router servers for web console and request routing

## Usage

### Basic Example

```java
import org.apache.druid.testing.embedded.*;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

// Create cluster
EmbeddedDruidCluster cluster = EmbeddedDruidCluster
    .withEmbeddedDerbyAndZookeeper()
    .addServer(new EmbeddedCoordinator())
    .addServer(new EmbeddedCoordinator())  // HA
    .addServer(new EmbeddedBroker())
    .addServer(new EmbeddedOverlord());

cluster.start();

try {
    // Restart coordinator with crash mode
    // Framework automatically discovers the adapter via ServiceLoader
    RestartFramework.at("crash-test")
        .on(cluster)
        .restart("coordinator")
        .withIndex(0)
        .withMode(RestartMode.CRASH)
        .execute();

    // State is automatically captured, verified, and health checked
} finally {
    cluster.stop();
}
```

### Direct Adapter Usage

```java
import org.apache.druid.testing.restart.DruidClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.state.ClusterState;
import org.restarttest.health.HealthCheckResult;

DruidClusterAdapter adapter = new DruidClusterAdapter();

// Capture state before restart
ClusterState before = adapter.getStateCapture().captureState(cluster);

// Restart coordinator
adapter.restartNode(cluster, "coordinator", 0, RestartMode.CRASH);

// Wait for cluster to stabilize
adapter.waitActive(cluster);

// Verify state preserved
ClusterState after = adapter.getStateCapture().captureState(cluster);
adapter.getStateCapture().verifyState(cluster, before, after);

// Verify cluster health
HealthCheckResult health = adapter.getHealthCheck().checkHealth(cluster);
if (!health.isPassed()) {
    throw new Exception("Health check failed: " + health.getFailures());
}
```

### Restart All Nodes of a Type

```java
// Restart all coordinators
adapter.restartAllNodes(cluster, "coordinator", RestartMode.GRACEFUL);
```

## State Verification

The adapter automatically captures and verifies:

1. **Server Topology**: Count of each server type preserved
2. **Leadership Status**: Leaders re-elected for Coordinator and Overlord
3. **Datasources**: No datasources lost
4. **Segment Metadata**: Segment counts and IDs preserved (no data loss)

## Health Checks

The adapter includes four health checks:

1. **ServerTopologyCheck**: Verifies all servers discoverable via `sys.servers`
2. **LeadershipCheck**: Verifies Coordinator and Overlord leaders elected
3. **NodeDiscoveryCheck**: Verifies node discovery system working
4. **SegmentAvailabilityCheck**: Verifies segments available for querying

## Implementation Details

### Crash Mode

Crash mode uses reflection to forcefully terminate the server's executor service without calling the lifecycle `stop()` method. This simulates an abrupt process crash and tests the cluster's resilience to sudden failures.

```java
// Simplified crash implementation
Field executorField = EmbeddedServerLifecycle.class.getDeclaredField("executorService");
executorField.setAccessible(true);
ExecutorService executor = (ExecutorService) executorField.get(serverLifecycle);
executor.shutdownNow();  // Force kill without cleanup
lifecycleRef.set(null);  // Clear lifecycle
server.start();  // Restart
```

### Server Identification

The adapter uses reflection to access the package-protected `servers` list in `EmbeddedDruidCluster`:

```java
Field serversField = EmbeddedDruidCluster.class.getDeclaredField("servers");
serversField.setAccessible(true);
List<EmbeddedDruidServer<?>> servers = (List<EmbeddedDruidServer<?>>) serversField.get(cluster);
```

### Datasource Discovery

Datasources are discovered by querying the `sys.segments` table:

```sql
SELECT DISTINCT datasource FROM sys.segments
```

## Testing

Run tests with:

```bash
cd embedded-tests/restart-adapter
mvn test
```

### Test Classes

- `DruidAdapterBasicTest`: Basic adapter functionality (cluster type, node counting)
- `DruidAdapterRestartTest`: Restart modes for different server types
- `DruidStateCaptureTest`: State capture and verification

## Dependencies

- `restart-core` (1.0.0-SNAPSHOT): RestartTestingFramework core
- `druid-services`: Druid embedded testing infrastructure
- JUnit 4: Testing framework

## Architecture

```
DruidClusterAdapter
├── implements ClusterAdapter<EmbeddedDruidCluster>
├── uses DruidStateCapture (extends AbstractStateCapture)
├── uses CompositeHealthCheck with:
│   ├── ServerTopologyCheck
│   ├── LeadershipCheck
│   ├── NodeDiscoveryCheck
│   └── SegmentAvailabilityCheck
└── registered via ServiceLoader
```

## Limitations

1. **Reflection-based**: Uses reflection to access package-protected fields. May require `--add-opens` JVM flags in newer Java versions.
2. **ZooKeeper sessions**: Crash mode may cause ZooKeeper session timeouts. The adapter includes retry logic to handle this.
3. **Eventual consistency**: Segment availability checks allow for eventual consistency in metadata propagation.

## Future Enhancements

Potential improvements:
- Support for external metadata store (non-Derby)
- More granular health checks (specific segment load status)
- Performance metrics collection during restarts
- Support for task state verification (running/pending tasks)

## License

Licensed under the Apache License, Version 2.0.
