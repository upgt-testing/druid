# Module Location Update

## Change Summary

The `restart-adapter` module has been moved from:

**Old Location:**
```
druid/embedded-tests/restart-adapter/
```

**New Location:**
```
druid/restart-adapter/
```

## Changes Made

1. **Directory Move**: Module moved to root of Druid repository
2. **POM Update**: Parent `relativePath` updated from `../../pom.xml` to `../pom.xml`
3. **Build Verification**: Rebuilt successfully at new location

## Build Status

✅ **BUILD SUCCESS** at new location
```
[INFO] Total time:  14.211 s
[INFO] BUILD SUCCESS
```

## New Module Structure

```
druid/
├── restart-adapter/           # <-- NEW LOCATION
│   ├── pom.xml
│   ├── BUILD_COMPLETE.md
│   ├── README.md
│   └── src/
│       └── main/
│           ├── java/org/apache/druid/testing/embedded/
│           │   ├── DruidClusterAdapter.java
│           │   ├── DruidStateCapture.java
│           │   └── health/
│           │       ├── ServerTopologyCheck.java
│           │       ├── LeadershipCheck.java
│           │       ├── NodeDiscoveryCheck.java
│           │       └── SegmentAvailabilityCheck.java
│           └── resources/
│               └── META-INF/services/
│                   └── org.restarttest.core.ClusterAdapter
├── services/
├── processing/
└── ... (other modules)
```

## Build Commands (Updated)

### Standard Build
```bash
cd /home/shuai/xlab/restart_testing/druid/restart-adapter
mvn clean install
```

### Docker Build
```bash
docker run --rm \
  -v /home/shuai/xlab/restart_testing:/workspace \
  -v /home/shuai/.m2:/root/.m2 \
  -w /workspace/druid/restart-adapter \
  maven:3.8-openjdk-11 \
  mvn clean install
```

## Integration (No Change)

The module is still accessed via the same Maven coordinates:

```xml
<dependency>
  <groupId>org.apache.druid</groupId>
  <artifactId>druid-restart-adapter</artifactId>
  <version>${project.parent.version}</version>
  <scope>test</scope>
</dependency>
```

ServiceLoader auto-discovery continues to work without changes.

## Rationale

Moving the module to the root level:
- ✅ Makes it a top-level module alongside other Druid modules
- ✅ Separates restart testing infrastructure from embedded-tests
- ✅ Provides better visibility as a standalone component
- ✅ Maintains the same Maven artifact coordinates

---

*Location updated: December 7, 2025*
