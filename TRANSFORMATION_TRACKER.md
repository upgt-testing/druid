# Test Transformation Progress Tracker

This document tracks the progress of test transformations for the Druid restart testing project.

## Legend
- [ ] Transformation not started yet
- [x] Transformation completed

## Test List

### Indexing Tests
- [ ] org.apache.druid.testing.embedded.indexing.IndexParallelTaskTest
- [ ] org.apache.druid.testing.embedded.indexing.IndexTaskTest
- [ ] org.apache.druid.testing.embedded.indexing.KafkaDataFormatsTest
- [ ] org.apache.druid.testing.embedded.indexing.ConcurrentAppendReplaceTest
- [ ] org.apache.druid.testing.embedded.indexing.KafkaClusterMetricsTest
- [ ] org.apache.druid.testing.embedded.indexing.IngestionSmokeTest

### Compaction Tests
- [ ] org.apache.druid.testing.embedded.compact.CompactionSupervisorTest
- [ ] org.apache.druid.testing.embedded.compact.CompactionSparseColumnTest
- [ ] org.apache.druid.testing.embedded.compact.AutoCompactionTest
- [ ] org.apache.druid.testing.embedded.compact.CompactionTaskTest
- [ ] org.apache.druid.testing.embedded.compact.AutoCompactionUpgradeTest

### Docker Tests
- [ ] org.apache.druid.testing.embedded.docker.CustomNodeRoleDockerTest
- [ ] org.apache.druid.testing.embedded.docker.IngestionBackwardCompatibilityDockerTest
- [ ] org.apache.druid.testing.embedded.docker.IngestionDockerTest

### MSQ Tests
- [ ] org.apache.druid.testing.embedded.msq.MultiStageQueryTest
- [ ] org.apache.druid.testing.embedded.msq.MSQWorkerFaultToleranceTest
- [ ] org.apache.druid.testing.embedded.msq.MSQKeyStatisticsSketchMergeModeTest
- [ ] org.apache.druid.testing.embedded.msq.EmbeddedDurableShuffleStorageTest
- [ ] org.apache.druid.testing.embedded.msq.EmbeddedMSQRealtimeUnnestQueryTest
- [ ] org.apache.druid.testing.embedded.msq.EmbeddedMSQRealtimeQueryTest
- [ ] org.apache.druid.testing.embedded.msq.ITSQLBasedBatchIngestionTest
- [ ] org.apache.druid.testing.embedded.msq.ITMSQReindexTest

### Server Tests
- [ ] org.apache.druid.testing.embedded.server.OverlordClientTest
- [ ] org.apache.druid.testing.embedded.server.CoordinatorClientTest
- [ ] org.apache.druid.testing.embedded.server.HighAvailabilityTest
- [ ] org.apache.druid.testing.embedded.server.HistoricalCloningTest
- [ ] org.apache.druid.testing.embedded.server.CoordinatorPauseTest
- [ ] org.apache.druid.testing.embedded.server.HttpEmitterEventCollectorTest
- [ ] org.apache.druid.testing.embedded.server.HttpRemoteTaskRunnerWorkerFailTest

### Schema Tests
- [ ] org.apache.druid.testing.embedded.schema.SystemServerPropertiesTableTest

### Query Tests
- [ ] org.apache.druid.testing.embedded.query.QueryVirtualStorageTest
- [ ] org.apache.druid.testing.embedded.query.SqlQueryHttpRequestHeadersTest
- [ ] org.apache.druid.testing.embedded.query.UnionQueryTest

### Auth Tests
- [ ] org.apache.druid.testing.embedded.auth.BasicAuthConfigurationTest
- [ ] org.apache.druid.testing.embedded.auth.BasicAuthLdapConfigurationTest
- [ ] org.apache.druid.testing.embedded.auth.BasicAuthMSQTest
- [ ] org.apache.druid.testing.embedded.auth.BasicAuthAuditTest

### Catalog Tests
- [ ] org.apache.druid.testing.embedded.catalog.CatalogRestTest
- [ ] org.apache.druid.testing.embedded.catalog.CatalogIngestErrorTest
- [ ] org.apache.druid.testing.embedded.catalog.CatalogInsertAndQueryTest
- [ ] org.apache.druid.testing.embedded.catalog.CatalogReplaceAndQueryTest

### Lookup Tests
- [ ] org.apache.druid.testing.embedded.lookup.JdbcLookupTest

### Indexer Tests
- [ ] org.apache.druid.testing.embedded.indexer.IndexerTest
- [ ] org.apache.druid.testing.embedded.indexer.ITCombiningInputSourceParallelIndexTest
- [ ] org.apache.druid.testing.embedded.indexer.ITSystemTableBatchIndexTaskTest
- [ ] org.apache.druid.testing.embedded.indexer.ITOverwriteBatchIndexTest
- [ ] org.apache.druid.testing.embedded.indexer.ITTransformTest
- [ ] org.apache.druid.testing.embedded.indexer.ITHttpInputSourceTest
- [ ] org.apache.druid.testing.embedded.indexer.ITSqlInputSourceTest
- [ ] org.apache.druid.testing.embedded.indexer.ITLocalInputSourceAllFormatSchemalessTest
- [ ] org.apache.druid.testing.embedded.indexer.ITLocalInputSourceAllInputFormatTest

### Cloud Storage Tests
- [ ] org.apache.druid.testing.embedded.gcs.AbstractGcsInputSourceParallelIndexTest
- [ ] org.apache.druid.testing.embedded.minio.ITS3SQLBasedIngestionTest
- [ ] org.apache.druid.testing.embedded.minio.ITS3ToS3ParallelIndexTest
- [ ] org.apache.druid.testing.embedded.minio.MinIOStorageTest
- [ ] org.apache.druid.testing.embedded.azure.ITAzureSQLBasedIngestionTest
- [ ] org.apache.druid.testing.embedded.azure.ITAzureToAzureParallelIndexTest
- [ ] org.apache.druid.testing.embedded.azure.ITAzureV2SQLBasedIngestionTest
- [ ] org.apache.druid.testing.embedded.azure.ITAzureV2ParallelIndexTest

### Database Tests
- [ ] org.apache.druid.testing.embedded.mariadb.MariaDBMetadataStoreTest
- [ ] org.apache.druid.testing.embedded.psql.PostgreSQLMetadataStoreTest

### Kubernetes Tests
- [ ] org.apache.druid.testing.embedded.k8s.KubernetesTaskRunnerDockerTest
- [ ] org.apache.druid.testing.embedded.k8s.KubernetesClusterDockerTest

### Other Tests
- [ ] org.apache.druid.indexing.kafka.simulate.EmbeddedKafkaSupervisorTest
- [ ] org.apache.druid.catalog.compact.CatalogCompactionTest

---

**Total Tests**: 65
**Completed**: 0
**Remaining**: 65
**Progress**: 0%
