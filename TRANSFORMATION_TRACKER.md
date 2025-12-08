# Test Transformation Progress Tracker

This document tracks the progress of test transformations for the Druid restart testing project.

## Legend
- [ ] Transformation not started yet
- [x] Transformation completed

## Test List

### Indexing Tests
- [x] org.apache.druid.testing.embedded.indexing.IndexParallelTaskTest - 12 restart points injected across 2 test methods
- [x] org.apache.druid.testing.embedded.indexing.IndexTaskTest - 6 restart points injected across 5 test methods
- [x] org.apache.druid.testing.embedded.indexing.KafkaDataFormatsTest - 28 restart points injected across 14 test methods
- [x] org.apache.druid.testing.embedded.indexing.ConcurrentAppendReplaceTest - 5 restart points injected across 1 test method
- [x] org.apache.druid.testing.embedded.indexing.KafkaClusterMetricsTest - 9 restart points injected across 3 test methods
- [x] org.apache.druid.testing.embedded.indexing.IngestionSmokeTest - 16 restart points injected across 5 test methods

### Compaction Tests
- [x] org.apache.druid.testing.embedded.compact.CompactionSparseColumnTest - 9 restart points injected across 3 test methods
- [x] org.apache.druid.testing.embedded.compact.AutoCompactionTest - 132 restart points injected across 27 test methods
- [x] org.apache.druid.testing.embedded.compact.CompactionTaskTest - 22 restart points injected across 7 test methods
- [x] org.apache.druid.testing.embedded.compact.AutoCompactionUpgradeTest - 2 restart points injected across 1 test method

### Docker Tests
- [x] org.apache.druid.testing.embedded.docker.CustomNodeRoleDockerTest - 2 restart points injected across 1 test method
- [x] org.apache.druid.testing.embedded.docker.IngestionBackwardCompatibilityDockerTest - 16 restart points injected across 5 test methods (inherited from IngestionSmokeTest_RestartInjected)
- [x] org.apache.druid.testing.embedded.docker.IngestionDockerTest - 16 restart points injected across 5 test methods (inherited from IngestionSmokeTest_RestartInjected)
- [N/A] org.apache.druid.testing.embedded.docker.LatestImageDockerTest - Interface/marker (not a test class)

### MSQ Tests
- [x] org.apache.druid.testing.embedded.msq.MultiStageQueryTest - 8 restart points injected across 2 test methods
- [x] org.apache.druid.testing.embedded.msq.MSQWorkerFaultToleranceTest - 6 restart points injected across 1 test method
- [x] org.apache.druid.testing.embedded.msq.MSQKeyStatisticsSketchMergeModeTest - 9 restart points injected across 3 test methods
- [x] org.apache.druid.testing.embedded.msq.EmbeddedDurableShuffleStorageTest - 10 restart points injected across 5 test methods
- [x] org.apache.druid.testing.embedded.msq.EmbeddedMSQRealtimeUnnestQueryTest - 4 restart points injected across 2 test methods
- [x] org.apache.druid.testing.embedded.msq.EmbeddedMSQRealtimeQueryTest - 35 restart points injected across 18 test methods
- [N/A] org.apache.druid.testing.embedded.msq.BaseRealtimeQueryTest - Base class with no @Test methods
- [N/A] org.apache.druid.testsEx.msq.ITSQLBasedBatchIngestionTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testsEx.msq.ITMSQReindexTest - Not an embedded test (Docker-based integration test)

### Server Tests
- [x] org.apache.druid.testing.embedded.server.OverlordClientTest - 8 restart points injected across 8 test methods
- [x] org.apache.druid.testing.embedded.server.CoordinatorClientTest - 7 restart points injected across 7 test methods
- [x] org.apache.druid.testing.embedded.server.HighAvailabilityTest - 9 restart points injected across 2 test methods
- [x] org.apache.druid.testing.embedded.server.HistoricalCloningTest - 9 restart points injected across 1 test method
- [x] org.apache.druid.testing.embedded.server.CoordinatorPauseTest - 6 restart points injected across 1 test method
- [x] org.apache.druid.testing.embedded.server.HttpEmitterEventCollectorTest - 4 restart points injected across 1 test method
- [x] org.apache.druid.testing.embedded.server.HttpRemoteTaskRunnerWorkerFailTest - 4 restart points injected across 1 test method

### Schema Tests
- [N/A] org.apache.druid.testing.embedded.schema.SystemServerPropertiesTableTest - Test does not exist in codebase
- [N/A] org.apache.druid.testing.embedded.schema.CentralizedSchemaMetadataQueryDisabledTest - Nested test container (all tests @Disabled or extend other transformed tests)
- [N/A] org.apache.druid.testing.embedded.schema.CentralizedSchemaPublishFailureTest - Nested test container (all tests @Disabled)

### Query Tests
- [x] org.apache.druid.testing.embedded.query.QueryVirtualStorageTest - 6 restart points injected across 2 test methods
- [N/A] org.apache.druid.testing.embedded.query.SqlQueryHttpRequestHeadersTest - Test does not exist in codebase
- [x] org.apache.druid.testing.embedded.query.UnionQueryTest - 6 restart points injected across 1 test method

### Auth Tests
- [N/A] org.apache.druid.testing.embedded.auth.BasicAuthConfigurationTest - Test does not exist in codebase
- [N/A] org.apache.druid.testing.embedded.auth.BasicAuthLdapConfigurationTest - Test does not exist in codebase
- [x] org.apache.druid.testing.embedded.auth.BasicAuthIndexingTest - 2 restart points injected across 1 test method (inherits 6 restart points from IndexTaskTest_RestartInjected)
- [x] org.apache.druid.testing.embedded.auth.BasicAuthMSQTest - 8 restart points injected across 4 test methods
- [x] org.apache.druid.testing.embedded.auth.BasicAuthAuditTest - 2 restart points injected across 1 test method

### Catalog Tests
- [x] org.apache.druid.testing.embedded.catalog.CatalogRestTest - 8 restart points injected across 2 test methods
- [x] org.apache.druid.testing.embedded.catalog.CatalogIngestErrorTest - 8 restart points injected across 4 test methods
- [x] org.apache.druid.testing.embedded.catalog.CatalogIngestAndQueryTest - 28 restart points injected across 7 test methods (abstract base class)
- [x] org.apache.druid.testing.embedded.catalog.CatalogInsertAndQueryTest - 2 restart points injected (partial: 1 of 7 test methods in parent class CatalogIngestAndQueryTest_RestartInjected)
- [x] org.apache.druid.testing.embedded.catalog.CatalogReplaceAndQueryTest - 2 restart points injected (partial: 1 of 7 test methods in parent class CatalogIngestAndQueryTest_RestartInjected)

### Lookup Tests
- [x] org.apache.druid.testing.embedded.lookup.JdbcLookupTest - 5 restart points injected across 1 test method

### Indexer Tests
- [N/A] org.apache.druid.testing.embedded.indexer.IndexerTest - Not an embedded test (Docker-based integration test in testsEx)
- [N/A] org.apache.druid.testing.embedded.indexer.ITCombiningInputSourceParallelIndexTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.indexer.ITSystemTableBatchIndexTaskTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.indexer.ITOverwriteBatchIndexTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.indexer.ITTransformTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.indexer.ITHttpInputSourceTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.indexer.ITSqlInputSourceTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.indexer.ITLocalInputSourceAllFormatSchemalessTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.indexer.ITLocalInputSourceAllInputFormatTest - Not an embedded test (Docker-based integration test)

### Cloud Storage Tests
- [N/A] org.apache.druid.testing.embedded.gcs.AbstractGcsInputSourceParallelIndexTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.minio.ITS3SQLBasedIngestionTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.minio.ITS3ToS3ParallelIndexTest - Not an embedded test (Docker-based integration test)
- [x] org.apache.druid.testing.embedded.minio.MinIOStorageTest - 6 restart points injected across 5 test methods (inherited from IndexTaskTest_RestartInjected)
- [N/A] org.apache.druid.testing.embedded.minio.MinIOStorageResourceTest - Resource test (tests MinIO resource, not EmbeddedDruidCluster)
- [N/A] org.apache.druid.testing.embedded.azure.ITAzureSQLBasedIngestionTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.azure.ITAzureToAzureParallelIndexTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.azure.ITAzureV2SQLBasedIngestionTest - Not an embedded test (Docker-based integration test)
- [N/A] org.apache.druid.testing.embedded.azure.ITAzureV2ParallelIndexTest - Not an embedded test (Docker-based integration test)

### Database Tests
- [x] org.apache.druid.testing.embedded.mariadb.MariaDBMetadataStoreTest - 6 restart points injected across 5 test methods (inherited from IndexTaskTest_RestartInjected)
- [N/A] org.apache.druid.testing.embedded.mariadb.MariaDBMetadataResourceTest - Resource test (tests MariaDB resource, not EmbeddedDruidCluster)
- [x] org.apache.druid.testing.embedded.psql.PostgreSQLMetadataStoreTest - 6 restart points injected across 5 test methods (inherited from IndexTaskTest_RestartInjected)
- [N/A] org.apache.druid.testing.embedded.psql.PostgreSQLMetadataResourceTest - Resource test (tests PostgreSQL resource, not EmbeddedDruidCluster)

### Kubernetes Tests
- [N/A] org.apache.druid.testing.embedded.k8s.KubernetesTaskRunnerDockerTest - Docker-based test (requires Kubernetes)
- [N/A] org.apache.druid.testing.embedded.k8s.KubernetesClusterDockerTest - Docker-based test (requires Kubernetes)

### Other Tests
- [x] org.apache.druid.indexing.kafka.simulate.EmbeddedKafkaSupervisorTest - 8 restart points injected across 1 test method
- [x] org.apache.druid.catalog.compact.CatalogCompactionTest - 8 restart points injected across 1 test method

---

**Total Tests**: 74 (43 completed + 31 N/A)
**Completed**: 43
**N/A (not applicable)**: 31
**Remaining**: 0
**Progress**: 100% ✓ All applicable tests transformed!
