# Prompt: Transform Druid Embedded Cluster Test with Restart Position Injection

## Objective

Transform an existing Druid embedded cluster test to inject restart positions for distributed system restart testing. The transformation will generate:
1. A new test file with `_RestartInjected` suffix
2. A restart configuration file for the Maven plugin

## Input

- **Test File Path**: Path to the original test file (e.g., `/path/to/TestDruidOperations.java`)
- **Test Class**: Fully-qualified class name (e.g., `org.apache.druid.server.TestDruidOperations`)

## Output

1. **Generated Test File**: `{OriginalFileName}_RestartInjected.java` at the same directory as the input file
2. **Restart Configuration**: `restart-config.json` in the `restarts-config/` directory under the same module directory as the test file, if not exist create it.

## Transformation Instructions

### Step 1: Analyze the Original Test

Read the input test file and identify:

1. **Cluster Setup**: Find the Druid cluster instance variable (e.g., `EmbeddedDruidCluster cluster` or `EmbeddedDruidCluster druidCluster`)
   - **CRITICAL**: The cluster object may be inherited from a parent base test class rather than being directly initialized in the test class itself
   - **IMPORTANT**: Carefully check the class hierarchy (look for `extends` clauses) and examine parent classes for cluster initialization
   - Common patterns:
     - Direct initialization: `private EmbeddedDruidCluster cluster;` in the test class
     - Inherited from parent: `extends DruidTestBase` where parent has the cluster field
     - Static shared cluster: `protected static EmbeddedDruidCluster sharedCluster;` in base class
     - Field with different name: `cluster`, `druidCluster`, `embeddedCluster`, etc.
   - **Strategy**: If the cluster is not found in the test class, traverse up the inheritance hierarchy to locate it
2. **Test Methods**: Identify all `@Test` annotated methods
3. **Critical Operations**: Look for operations that involve state transitions, such as:
   - Data ingestion: Task submission, data loading, streaming ingestion
   - Query operations: SQL queries, native queries, scan queries
   - Segment operations: Segment publishing, loading, dropping, compaction
   - Coordinator operations: Segment balancing, load queue management, rules evaluation
   - Indexing tasks: Batch ingestion tasks, streaming tasks (Kafka/Kinesis)
   - Compaction: Auto-compaction, manual compaction tasks
   - Metadata operations: Datasource creation, segment metadata updates
   - Supervisor operations: Starting/stopping supervisors, resetting offsets
   - Overlord operations: Task management, task status tracking
   - Retention operations: Segment retention, kill tasks
   - Schema operations: Schema changes, dimension/metric updates

### Step 2: Identify Restart Points

For each test method, identify potential restart points based on these criteria:

**Good Restart Points** (inject here):
- After data ingestion task submission but before completion
- After segment publishing
- During query execution (for consistency testing)
- After compaction task submission
- During segment loading/balancing
- After supervisor start/stop
- After metadata updates
- Before/after kill task execution
- During multi-datasource operations
- After coordinator rule changes
- During segment handoff
- After Historical segment loading
- During task completion/failure handling

**Poor Restart Points** (avoid):
- Before cluster setup (no cluster exists yet)
- After cluster teardown (cluster already destroyed)
- During trivial operations (simple getters with no state changes)
- Operations that are too fast to test meaningful state

**Naming Convention for Restart Positions**:
- Use descriptive, lowercase names with underscores
- Pattern: `{operation}_{context}`
- Examples:
  - `after_task_submit`
  - `after_segment_publish`
  - `during_query`
  - `after_compaction_start`
  - `after_segment_load`
  - `before_kill_task`
  - `after_supervisor_start`
  - `during_ingestion`
  - `after_metadata_update`
  - `after_segment_handoff`
  - `before_coordinator_run`
  - `after_task_complete`

### Step 3: Generate the Restart-Injected Test File

Create a new test file with the following transformations:

#### 3.1 Package and Imports

```java
// Keep original package declaration
package org.apache.druid.server;

// Add these imports at the top (if not already present)
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

// Keep all original imports
```

#### 3.2 Class Declaration

```java
// Original class name: TestDruidOperations
// New class name: TestDruidOperations_RestartInjected

// Keep the same inheritance structure!
// If original: public class TestDruidOperations extends DruidTestBase
// Then use:    public class TestDruidOperations_RestartInjected extends DruidTestBase
public class TestDruidOperations_RestartInjected {
    // Keep all original fields and variables
}
```

**CRITICAL**: If the test class extends a base class, preserve this inheritance in the generated test. The cluster object may be defined in the parent class. You must carefully trace through the inheritance hierarchy to find the cluster object.

#### 3.3 Cluster Setup and Teardown

Keep the `@Before`/`@BeforeAll`/`@BeforeEach` and `@After`/`@AfterAll`/`@AfterEach` methods unchanged:

```java
@BeforeAll
public static void setUp() throws Exception {
    // Keep original setup code unchanged
}

@AfterAll
public static void tearDown() throws Exception {
    // Keep original teardown code unchanged
}
```

**Note**: Setup methods may be inherited from parent class. In that case, don't add setup methods to the generated test. Carefully examine the parent class to understand the cluster initialization pattern.

#### 3.4 Transform Test Methods

For each `@Test` method, apply the following transformations:

**Original Test Method**:
```java
@Test
public void testIngestionAndQuery() throws Exception {
    String dataSource = "wikipedia";

    // Submit ingestion task
    String taskId = overlord.submitTask(ingestionSpec);

    // Wait for task completion
    overlord.waitForTaskCompletion(taskId);

    // Query the data
    String query = "SELECT COUNT(*) FROM wikipedia";
    String result = cluster.runSql(query);

    assertTrue(result.contains("100"));
}
```

**Transformed Test Method**:
```java
@Test
public void testIngestionAndQuery() throws Exception {
    String dataSource = "wikipedia";

    // Submit ingestion task
    String taskId = overlord.submitTask(ingestionSpec);

    // RESTART POINT 1: after_task_submit
    RestartFramework.at("after_task_submit")
        .on(cluster)  // Use the cluster instance (may be from parent class)
        .restart("overlord")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Wait for task completion
    overlord.waitForTaskCompletion(taskId);

    // RESTART POINT 2: after_task_complete
    RestartFramework.at("after_task_complete")
        .on(cluster)
        .restart("historical")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Query the data
    String query = "SELECT COUNT(*) FROM wikipedia";
    String result = cluster.runSql(query);

    // RESTART POINT 3: during_query
    RestartFramework.at("during_query")
        .on(cluster)
        .restart("broker")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    assertTrue(result.contains("100"));
}
```

**Injection Pattern**:

1. **After Task Operations**:
   ```java
   overlord.submitTask(spec);

   // Inject restart point
   RestartFramework.at("after_task_submit")
       .on(cluster)
       .restart("overlord")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

2. **After Segment Operations**:
   ```java
   // Segment published

   // Inject restart point
   RestartFramework.at("after_segment_publish")
       .on(cluster)
       .restart("coordinator")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

3. **Before Critical Operations**:
   ```java
   // Inject restart point before kill task
   RestartFramework.at("before_kill_task")
       .on(cluster)
       .restart("coordinator")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();

   coordinator.killSegments(dataSource);
   ```

4. **During Long Operations**:
   ```java
   // Start large query
   String query = "SELECT * FROM large_table";

   // Inject restart point during execution
   RestartFramework.at("during_query")
       .on(cluster)
       .restart("broker")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();

   String result = cluster.runSql(query);
   ```

#### 3.5 Druid-Specific Node Roles

Druid has six primary node types:

- **`coordinator`**: Manages segment distribution and load balancing across Historical nodes
- **`overlord`**: Manages task submission, scheduling, and lifecycle
- **`broker`**: Routes queries to appropriate data nodes and merges results
- **`historical`**: Serves immutable segments and handles scan queries
- **`indexer`** (or `middleManager`): Executes ingestion tasks and creates segments
- **`router`** (optional): Routes requests to appropriate services

**Default Restart Configuration**:
Use these defaults for all injected restart points:
- **For task operations**: `"overlord"` (manages task lifecycle)
- **For segment operations**: `"coordinator"` or `"historical"` (manages segment distribution/serving)
- **For query operations**: `"broker"` or `"historical"` (handles query routing/execution)
- **For ingestion operations**: `"indexer"` (executes ingestion tasks)
- **For cluster-wide operations**: Multiple node types
- **Node Index**: `0` (first node)
- **Restart Mode**: `RestartMode.GRACEFUL` (default, safest)

#### 3.6 Node Role Selection Guidelines

| Operation Type | Primary Node Role | Secondary Node Role | Reason |
|----------------|-------------------|---------------------|--------|
| Task submission/management | `overlord` | - | Overlord manages task lifecycle |
| Segment publishing | `coordinator` | `historical` | Coordinator assigns, Historical serves |
| Segment loading | `historical` | `coordinator` | Historical loads, Coordinator orchestrates |
| Segment compaction | `overlord` | `coordinator` | Overlord runs compaction, Coordinator manages segments |
| Query execution | `broker` | `historical` | Broker routes, Historical executes |
| Data ingestion | `indexer` | `overlord` | Indexer executes, Overlord manages |
| Segment balancing | `coordinator` | `historical` | Coordinator balances, Historical loads/drops |
| Supervisor operations | `overlord` | - | Overlord manages supervisors |
| Kill/retention tasks | `coordinator` | - | Coordinator manages retention |
| Metadata updates | `coordinator` | `overlord` | Both interact with metadata |
| Segment handoff | `indexer` | `historical` | Indexer creates, Historical receives |
| Rule evaluation | `coordinator` | - | Coordinator evaluates rules |

### Step 4: Generate Restart Configuration File

Create `restarts-config/restart-config.json` with the following structure:

```json
{
  "tests": [
    {
      "testClass": "org.apache.druid.server.TestDruidOperations_RestartInjected",
      "testMethod": "testIngestionAndQuery",
      "restartPoints": [
        {
          "position": "after_task_submit",
          "targets": ["overlord"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        },
        {
          "position": "after_task_complete",
          "targets": ["historical", "coordinator"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "during_query",
          "targets": ["broker", "historical"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

#### Configuration Generation Rules

For each test method in the transformed test:

1. **Create a test specification** with:
   - `testClass`: The fully-qualified name of the generated test class
   - `testMethod`: The test method name (same as original)
   - `restartPoints`: Array of restart point configurations

2. **For each restart point** injected in the test method:
   - `position`: The position identifier used in `.at("...")`
   - `targets`: Array of node roles to test
   - `modes`: Array of restart modes to test

#### Target Selection for Druid Operations

**Coordinator-only operations** (`["coordinator"]`):
- Segment load balancing
- Segment retention/kill tasks
- Rule evaluation
- Load queue management
- Segment assignment decisions
- Compaction planning

**Overlord-only operations** (`["overlord"]`):
- Task submission
- Task status tracking
- Supervisor management
- Task queue management
- Resource allocation for tasks

**Broker-only operations** (`["broker"]`):
- Query routing
- Result merging
- Query planning
- Cache management

**Historical-only operations** (`["historical"]`):
- Segment serving
- Segment caching
- Scan execution
- Segment loading/dropping

**Indexer-only operations** (`["indexer"]`):
- Task execution
- Data ingestion
- Segment creation
- Segment publishing

**Multiple Node Types** (combinations):
- Task + Segment lifecycle: `["overlord", "coordinator", "historical"]`
- Query execution: `["broker", "historical"]`
- Ingestion + handoff: `["indexer", "historical", "coordinator"]`
- Compaction: `["overlord", "coordinator", "historical"]`
- Full cluster operations: `["coordinator", "overlord", "broker", "historical", "indexer"]`

#### Mode Selection Guidelines

- **`["GRACEFUL"]`**: Basic test, verify restart works
  - Use for: Initial testing, simple state transitions

- **`["GRACEFUL", "CRASH"]`**: Standard test, verify crash recovery
  - Use for: Task operations, segment operations, query operations

- **`["GRACEFUL", "CRASH", "DELAYED_CRASH"]`**: Advanced test, verify timing-sensitive operations
  - Use for: Segment handoff, task completion, metadata updates, distributed coordination, concurrent operations

### Step 5: File Placement

1. **Generated Test File**:
   - Location: Same directory as original test file
   - Name: `{OriginalClassName}_RestartInjected.java`
   - Example: `TestDruidOps.java` → `TestDruidOps_RestartInjected.java`

2. **Restart Configuration**:
   - Location: `restarts-config/` directory under the same module directory as the test file
   - Name: `restart-config.json`
   - If file exists, append to the `tests` array (avoid duplicates)
   - If file doesn't exist, create new file

## Example Transformation

### Input: `TestIngestionQuery.java`

```java
package org.apache.druid.server.coordinator;

import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedBroker;
import org.apache.druid.testing.embedded.EmbeddedHistorical;
import org.apache.druid.testing.embedded.EmbeddedOverlord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestIngestionQuery {
    private EmbeddedDruidCluster cluster;

    @Before
    public void setUp() throws Exception {
        cluster = EmbeddedDruidCluster.withEmbeddedDerbyAndZookeeper()
                .addServer(new EmbeddedOverlord())
                .addServer(new EmbeddedBroker())
                .addServer(new EmbeddedHistorical());
        cluster.start();
    }

    @After
    public void tearDown() throws Exception {
        if (cluster != null) {
            cluster.stop();
        }
    }

    @Test
    public void testSimpleIngestion() throws Exception {
        String dataSource = "test";

        // Submit ingestion task
        String taskId = submitIngestionTask(dataSource);

        // Wait for completion
        waitForTaskSuccess(taskId);

        // Query data
        String result = cluster.runSql("SELECT COUNT(*) FROM " + dataSource);

        assertTrue(result.contains("100"));
    }
}
```

### Output 1: `TestIngestionQuery_RestartInjected.java`

```java
package org.apache.druid.server.coordinator;

import org.apache.druid.testing.embedded.EmbeddedDruidCluster;
import org.apache.druid.testing.embedded.EmbeddedBroker;
import org.apache.druid.testing.embedded.EmbeddedHistorical;
import org.apache.druid.testing.embedded.EmbeddedOverlord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static org.junit.Assert.*;

public class TestIngestionQuery_RestartInjected {
    private EmbeddedDruidCluster cluster;

    @Before
    public void setUp() throws Exception {
        cluster = EmbeddedDruidCluster.withEmbeddedDerbyAndZookeeper()
                .addServer(new EmbeddedOverlord())
                .addServer(new EmbeddedBroker())
                .addServer(new EmbeddedHistorical());
        cluster.start();
    }

    @After
    public void tearDown() throws Exception {
        if (cluster != null) {
            cluster.stop();
        }
    }

    @Test
    public void testSimpleIngestion() throws Exception {
        String dataSource = "test";

        // Submit ingestion task
        String taskId = submitIngestionTask(dataSource);

        // RESTART POINT 1: after_task_submit
        RestartFramework.at("after_task_submit")
            .on(cluster)
            .restart("overlord")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // Wait for completion
        waitForTaskSuccess(taskId);

        // RESTART POINT 2: after_task_complete
        RestartFramework.at("after_task_complete")
            .on(cluster)
            .restart("historical")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // Query data
        String result = cluster.runSql("SELECT COUNT(*) FROM " + dataSource);

        // RESTART POINT 3: after_query
        RestartFramework.at("after_query")
            .on(cluster)
            .restart("broker")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertTrue(result.contains("100"));
    }
}
```

### Output 2: `restarts-config/restart-config.json`

```json
{
  "tests": [
    {
      "testClass": "org.apache.druid.server.coordinator.TestIngestionQuery_RestartInjected",
      "testMethod": "testSimpleIngestion",
      "restartPoints": [
        {
          "position": "after_task_submit",
          "targets": ["overlord"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        },
        {
          "position": "after_task_complete",
          "targets": ["historical", "coordinator"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_query",
          "targets": ["broker", "historical"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

## Druid-Specific Patterns

### Pattern 1: Task Lifecycle Testing

```java
@Test
public void testTaskLifecycle() throws Exception {
    // Submit task
    String taskId = overlord.submitTask(spec);

    RestartFramework.at("after_submit")
        .on(cluster)
        .restart("overlord")
        .execute();

    // Running
    waitForTaskRunning(taskId);

    RestartFramework.at("during_running")
        .on(cluster)
        .restart("indexer")
        .execute();

    // Complete
    waitForTaskSuccess(taskId);

    RestartFramework.at("after_complete")
        .on(cluster)
        .restart("coordinator")
        .execute();
}
```

### Pattern 2: Segment Lifecycle Testing

```java
@Test
public void testSegmentLifecycle() throws Exception {
    // Publish segment
    publishSegment(dataSource);

    RestartFramework.at("after_publish")
        .on(cluster)
        .restart("coordinator")
        .execute();

    // Load segment
    waitForSegmentLoad(dataSource);

    RestartFramework.at("after_load")
        .on(cluster)
        .restart("historical")
        .execute();

    // Query segment
    queryData(dataSource);

    RestartFramework.at("after_query")
        .on(cluster)
        .restart("broker")
        .execute();
}
```

### Pattern 3: Compaction Testing

```java
@Test
public void testCompaction() throws Exception {
    // Ingest initial data
    ingestData(dataSource, interval1);
    ingestData(dataSource, interval2);

    RestartFramework.at("before_compact")
        .on(cluster)
        .restart("coordinator")
        .execute();

    // Submit compaction
    String taskId = coordinator.submitCompactionTask(dataSource);

    RestartFramework.at("after_compact_submit")
        .on(cluster)
        .restart("overlord")
        .execute();

    // Wait for compaction
    waitForTaskSuccess(taskId);

    RestartFramework.at("after_compact")
        .on(cluster)
        .restart("historical")
        .execute();

    // Verify compacted segments
    verifyCompaction(dataSource);
}
```

### Pattern 4: Supervisor Testing

```java
@Test
public void testSupervisor() throws Exception {
    // Start supervisor
    String supervisorId = overlord.startSupervisor(kafkaSpec);

    RestartFramework.at("after_supervisor_start")
        .on(cluster)
        .restart("overlord")
        .execute();

    // Wait for ingestion
    waitForDataIngestion(dataSource, recordCount);

    RestartFramework.at("during_ingestion")
        .on(cluster)
        .restart("indexer")
        .execute();

    // Verify data
    verifyData(dataSource);

    // Stop supervisor
    overlord.stopSupervisor(supervisorId);

    RestartFramework.at("after_supervisor_stop")
        .on(cluster)
        .restart("overlord")
        .execute();
}
```

### Pattern 5: Query Testing

```java
@Test
public void testComplexQuery() throws Exception {
    // Ingest test data
    ingestTestData();

    // Execute query
    String query = "SELECT dim1, SUM(metric1) FROM datasource GROUP BY dim1";

    RestartFramework.at("before_query")
        .on(cluster)
        .restart("broker")
        .execute();

    String result = cluster.runSql(query);

    RestartFramework.at("after_query")
        .on(cluster)
        .restart("historical")
        .execute();

    // Verify results
    verifyQueryResult(result);
}
```

### Pattern 6: Retention Testing

```java
@Test
public void testRetention() throws Exception {
    // Ingest old data
    ingestOldSegments(dataSource, oldInterval);

    // Apply retention rules
    coordinator.setRetentionRules(dataSource, rules);

    RestartFramework.at("after_rule_apply")
        .on(cluster)
        .restart("coordinator")
        .execute();

    // Wait for coordinator run
    waitForCoordinatorRun();

    RestartFramework.at("after_coordinator_run")
        .on(cluster)
        .restart("coordinator")
        .execute();

    // Verify segments dropped
    verifySegmentsDropped(dataSource, oldInterval);
}
```

## Validation Checklist

After transformation, verify:

- [ ] Generated test file compiles without errors
- [ ] All original test logic is preserved
- [ ] Restart points are placed at meaningful Druid operations
- [ ] Restart position names are descriptive and Druid-specific
- [ ] Node roles (coordinator/overlord/broker/historical/indexer/router) are correctly chosen
- [ ] Configuration file has correct fully-qualified class names
- [ ] Configuration file includes all restart points from the test
- [ ] Target arrays match the operation type
- [ ] Mode arrays are appropriate for timing sensitivity
- [ ] Files are placed in correct locations
- [ ] Original test file is not modified (only new files created)
- [ ] **CRITICAL**: Cluster object is correctly identified (may be from parent class)
- [ ] Inheritance structure is preserved if test extends a base class

## Advanced Scenarios

### Multiple Test Methods

If the original test has multiple `@Test` methods:

1. Transform each method independently
2. Inject restart points in each method
3. Create a separate test specification for each method in the configuration

Example configuration:
```json
{
  "tests": [
    {
      "testClass": "org.apache.druid.server.TestDruid_RestartInjected",
      "testMethod": "testIngestion",
      "restartPoints": [...]
    },
    {
      "testClass": "org.apache.druid.server.TestDruid_RestartInjected",
      "testMethod": "testQuery",
      "restartPoints": [...]
    }
  ]
}
```

### Helper Methods

If the test has helper methods:

1. **Do not inject restart points in helper methods**
2. Only inject in `@Test` annotated methods
3. Keep helper methods unchanged

### Cluster Object in Parent Class

**CRITICAL**: The most common scenario in Druid tests is that the `EmbeddedDruidCluster` object is defined in a parent base class, not in the test class itself.

**Step-by-step approach to locate the cluster object**:

1. Check if the test class extends a base class (look for `extends` keyword)
2. If yes, examine the parent class for cluster fields
3. Look for common field names: `cluster`, `druidCluster`, `embeddedCluster`
4. Check if the field is `private`, `protected`, or `public` - you may need to use the same access pattern
5. In the transformed test, use the inherited cluster object for restart injection

Example:
```java
// Parent class: DruidTestBase.java
public abstract class DruidTestBase {
    protected static EmbeddedDruidCluster cluster;

    @BeforeAll
    public static void setupCluster() {
        cluster = EmbeddedDruidCluster.withEmbeddedDerbyAndZookeeper()
                .addServer(new EmbeddedOverlord())
                .addServer(new EmbeddedBroker())
                .addServer(new EmbeddedHistorical());
        cluster.start();
    }
}

// Test class: TestDruidQuery.java
public class TestDruidQuery extends DruidTestBase {
    @Test
    public void testQuery() {
        // cluster is inherited from parent
        String result = cluster.runSql("SELECT * FROM test");
    }
}

// Transformed test: TestDruidQuery_RestartInjected.java
public class TestDruidQuery_RestartInjected extends DruidTestBase {
    @Test
    public void testQuery() {
        RestartFramework.at("before_query")
            .on(cluster)  // Use inherited cluster object
            .restart("broker")
            .execute();

        String result = cluster.runSql("SELECT * FROM test");
    }
}
```

### High Availability (HA) Configurations

For tests with multiple Coordinators or Overlords:

```java
// Restart active coordinator
RestartFramework.at("after_failover")
    .on(cluster)
    .restart("coordinator")
    .withIndex(0)  // Active coordinator
    .execute();

// Or restart all coordinators
RestartFramework.at("restart_all_coordinators")
    .on(cluster)
    .restart("coordinator")
    .withIndex("all")
    .execute();
```

Configuration:
```json
{
  "position": "after_failover",
  "targets": ["coordinator"],
  "modes": ["GRACEFUL", "CRASH"]
}
```

### Tests Without Obvious Restart Points

If a test has no clear state transitions:

1. Inject restart points within the range of (a) after cluster setup and (b) before cluster teardown
2. Evenly distribute restart points to cover the test execution
3. You MUST use percentage-based positions (e.g., `at_25_percent`, `at_50_percent`) to at least cover 4 points during the test execution
4. Find cluster operations to place restart points around

**IMPORTANT**: You are NOT allowed to skip any test transformation due to lack of restart points. Always inject at least one restart point per test method.

## Common Druid Operations and Suggested Restart Points

| Druid Operation | Suggested Restart Point Name | Node Role | Timing |
|-----------------|------------------------------|-----------|--------|
| Task submission | `after_task_submit` | `overlord` | After |
| Task completion | `after_task_complete` | `overlord`, `coordinator` | After |
| Segment publishing | `after_segment_publish` | `coordinator` | After |
| Segment loading | `after_segment_load` | `historical` | After |
| Query execution | `during_query`, `after_query` | `broker`, `historical` | During/After |
| Compaction start | `after_compaction_start` | `overlord` | After |
| Compaction complete | `after_compaction_complete` | `coordinator`, `historical` | After |
| Supervisor start | `after_supervisor_start` | `overlord` | After |
| Supervisor stop | `after_supervisor_stop` | `overlord` | After |
| Kill task | `before_kill_task`, `after_kill_task` | `coordinator` | Before/After |
| Segment handoff | `during_handoff`, `after_handoff` | `indexer`, `historical` | During/After |
| Rule changes | `after_rule_change` | `coordinator` | After |
| Coordinator run | `before_coordinator_run`, `after_coordinator_run` | `coordinator` | Before/After |
| Data ingestion | `during_ingestion` | `indexer` | During |
| Metadata update | `after_metadata_update` | `coordinator`, `overlord` | After |

## Notes

- **Non-invasive**: Original test file is never modified
- **Incremental**: Can transform tests one at a time
- **Compatible**: Generated tests can run both with and without restart injection
- **Configurable**: Configuration file allows easy adjustment of test matrix
- **Druid-aware**: Node role selection is specific to Druid architecture
- **CRITICAL**: Always carefully check for cluster objects in parent base classes

## Dependencies

Ensure the following dependencies are included in the test's module to use the Restart Testing Framework:

```xml
<dependencies>
    <!-- Existing dependencies... -->

    <!-- Restart Testing Framework - Core -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- Restart Testing Framework - Druid Adapter -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-druid-adapter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Build Requirements

**IMPORTANT**: Druid tests require Java 11 to build and compile.

Before building or running Druid tests with restart injection:

```bash
# Ensure Java 11 is being used
java -version  # Should show Java 11

# If using SDKMAN or similar:
# sdk use java 11.x.x

# Then build the tests
mvn clean test
```
