# Standalone Maven Project

## Overview

The `druid-restart-adapter` is now a **standalone Maven project** within the Druid repository. It does not inherit from Druid's parent POM and has independent versioning.

## Maven Coordinates

```xml
<groupId>org.apache.druid</groupId>
<artifactId>druid-restart-adapter</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>jar</packaging>
```

## Project Structure

```
druid/restart-adapter/                    # Standalone project root
├── pom.xml                               # Self-contained POM (no parent)
├── src/
│   └── main/
│       ├── java/org/apache/druid/testing/embedded/
│       │   ├── DruidClusterAdapter.java
│       │   ├── DruidStateCapture.java
│       │   └── health/
│       │       ├── ServerTopologyCheck.java
│       │       ├── LeadershipCheck.java
│       │       ├── NodeDiscoveryCheck.java
│       │       └── SegmentAvailabilityCheck.java
│       └── resources/
│           └── META-INF/services/
│               └── org.restarttest.core.ClusterAdapter
└── *.md                                  # Documentation files
```

## Key Differences from Child Module

### Before (Child Module)
```xml
<parent>
  <groupId>org.apache.druid</groupId>
  <artifactId>druid</artifactId>
  <version>35.0.0</version>
  <relativePath>../pom.xml</relativePath>
</parent>

<artifactId>druid-restart-adapter</artifactId>
<!-- Inherited version, properties, and plugin management -->
```

### After (Standalone)
```xml
<!-- No parent POM -->

<groupId>org.apache.druid</groupId>
<artifactId>druid-restart-adapter</artifactId>
<version>1.0.0-SNAPSHOT</version>

<properties>
  <druid.version>35.0.0</druid.version>
  <maven.compiler.source>1.8</maven.compiler.source>
  <maven.compiler.target>1.8</maven.compiler.target>
  <!-- All properties explicitly defined -->
</properties>

<!-- All dependencies explicitly declared with versions -->
<!-- All plugins explicitly configured -->
```

## Dependencies

The adapter has minimal dependencies:

1. **restart-core** (1.0.0-SNAPSHOT) - RestartTestingFramework core
2. **druid-services** (35.0.0, provided scope) - EmbeddedDruidCluster
3. **druid-services** (35.0.0, test-jar, provided scope) - Embedded server classes
4. **slf4j-api** (1.7.36) - Logging interface

## Build Requirements

- **Java**: 11+ (to compile against Druid 35.0.0)
- **Maven**: 3.6+
- **restart-core**: Must be installed in local Maven repository

## Build Instructions

### Standard Build (requires Java 11)
```bash
cd /home/shuai/xlab/restart_testing/druid/restart-adapter
mvn clean install
```

### Docker Build (recommended)
```bash
docker run --rm \
  -v /home/shuai/xlab/restart_testing:/workspace \
  -v /home/shuai/.m2:/root/.m2 \
  -w /workspace/druid/restart-adapter \
  maven:3.8-openjdk-11 \
  mvn clean install
```

## Installation

The build produces:
```
target/druid-restart-adapter-1.0.0-SNAPSHOT.jar
```

Installed to local Maven repository:
```
~/.m2/repository/org/apache/druid/druid-restart-adapter/1.0.0-SNAPSHOT/
```

## Integration with Druid Tests

### Add Dependency to Test Module

In your Druid test module's `pom.xml`:

```xml
<dependency>
  <groupId>org.apache.druid</groupId>
  <artifactId>druid-restart-adapter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

### Automatic Discovery

The adapter is automatically discovered via ServiceLoader. No manual registration needed:

```java
@Test
public void testRestart() throws Exception {
    EmbeddedDruidCluster cluster = EmbeddedDruidCluster
        .withEmbeddedDerbyAndZookeeper()
        .addServer(new EmbeddedCoordinator())
        .addServer(new EmbeddedBroker());

    cluster.start();

    try {
        // Adapter auto-discovered by RestartTestingFramework
        RestartFramework.at("test")
            .on(cluster)
            .restart("coordinator")
            .withMode(RestartMode.CRASH)
            .execute();
    } finally {
        cluster.stop();
    }
}
```

## Independent Versioning

The adapter uses **independent versioning** (1.0.0-SNAPSHOT) separate from Druid's release cycle:

- **Druid version**: 35.0.0 (referenced as dependency)
- **Adapter version**: 1.0.0-SNAPSHOT (independent)

This allows:
- Independent releases of the adapter
- Testing with multiple Druid versions (by changing `druid.version` property)
- No coupling to Druid's release process

## Relationship to Druid Repository

The adapter:
- Lives in the Druid repository (`druid/restart-adapter/`)
- Is **NOT** part of Druid's multi-module build
- Is **NOT** included in Druid releases
- Builds independently with its own versioning
- References Druid artifacts as external dependencies

## Rationale for Standalone Structure

1. **Decoupled from Druid Build**: Can build without building entire Druid
2. **Independent Testing**: Test infrastructure separate from production code
3. **Flexible Versioning**: Can update adapter without Druid release
4. **Clear Boundaries**: Explicit dependency management
5. **Reusable**: Can be used across different Druid versions

## Build Verification

Last successful build:
```
[INFO] BUILD SUCCESS
[INFO] Total time:  15.757 s
[INFO] Finished at: 2025-12-07T...
```

Artifact installed:
```
Installing /workspace/druid/restart-adapter/target/druid-restart-adapter-1.0.0-SNAPSHOT.jar
  to /root/.m2/repository/org/apache/druid/druid-restart-adapter/1.0.0-SNAPSHOT/
```

---

**Conversion Date**: December 7, 2025
**Status**: Standalone project with independent versioning
