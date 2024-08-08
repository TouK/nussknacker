---
sidebar_label: "Flink"
description: Flink specific model configuration
---

# Flink specific model configuration



| Parameter name                                              | Importance | Type     | Default value          | Description                                                                                                                                                                 |
|-------------------------------------------------------------|------------|----------|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| timeout                                                     | Medium     | duration | 10s                    | Timeout for invocation of scenario part (including enrichers)                                                                                                               |
| asyncExecutionConfig.bufferSize                             | Low        | int      | 200                    | Buffer size used for [async I/O](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/operators/asyncio/)                                             |
| asyncExecutionConfig.workers                                | Low        | int      | 8                      | Number of workers for thread pool used with [async I/O](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/operators/asyncio/)                      |
| asyncExecutionConfig.defaultUseAsyncInterpretation          | Medium     | boolean  | true                   | Should async I/O be used by scenarios by default - if you don't use many enrichers etc. you may consider setting this flag to false                                         |
| checkpointConfig.checkpointInterval                         | Medium     | duration | 10m                    | How often should checkpoints be performed by default                                                                                                                        |
| checkpointConfig.minPauseBetweenCheckpoints                 | Low        | duration | checkpointInterval / 2 | [Minimal pause](https://ci.apache.org/projects/flink/flink-docs-stable/docs/deployment/config/#execution-checkpointing-min-pause) between checkpoints                       |
| checkpointConfig.maxConcurrentCheckpoints                   | Low        | int      | 1                      | [Maximum concurrent checkpoints](https://ci.apache.org/projects/flink/flink-docs-stable/docs/deployment/config/#execution-checkpointing-max-concurrent-checkpoints) setting |
| checkpointConfig.tolerableCheckpointFailureNumber           | Low        | int      |                        | [Tolerable failed checkpoint](https://ci.apache.org/projects/flink/flink-docs-stable/docs/deployment/config/#execution-checkpointing-tolerable-failed-checkpoints) setting  |
| rocksDB.enable                                              | Medium     | boolean  | true                   | Enable RocksDB state backend support                                                                                                                                        |
| rocksDB.incrementalCheckpoints                              | Medium     | boolean  | true                   | Should incremental checkpoints be used                                                                                                                                      |
| rocksDB.dbStoragePath                                       | Low        | string   |                        | Allows to override RocksDB local data storage                                                                                                                               |
| enableObjectReuse                                           | Low        | boolean  | true                   | Should allow [object reuse](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/execution/execution_configuration/)                                             |
| globalParameters.explicitUidInStatefulOperators             | Low        | boolean  | true                   | Should consistent [operator uuids](https://ci.apache.org/projects/flink/flink-docs-stable/docs/ops/upgrading/#matching-operator-state) be used                              |
| globalParameters.useTypingResultTypeInformation             | Low        | boolean  | true                   | Enables using Nussknacker additional typing information for state serialization. It makes serialization much faster, currently consider it as experimental                  |
| globalParameters.forceSyncInterpretationForSyncScenarioPart | Low        | boolean  | true                   | Forces synchronous interpretation for scenario parts that does not contain any services (enrichers, processors). Applies for scenarios with async enabled                   |
| executionMode                                               | High       | string   | streaming              | Sets [execution mode](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/execution_mode). Allowed values: `streaming`, `batch`, `automatic`         |


<!-- TODO 
### KAFKA
-->

## Configuring exception handling

Exception handling can be customized using provided `EspExceptionConsumer`. By default, there are two available:
- `BrieflyLogging`
- `VerboselyLogging`
  More of them can be added with custom extensions. By default, basic error metrics are collected. If for some reason
  it's not desirable, metrics collector can be turned off with `withRateMeter: false` setting.
  When an exception is raised within a scenario, the handler uses `WithExceptionExtractor` to determine if it should be consumed
  (via `EspExceptionConsumer`) or rethrown. A custom extractor can be provided and indicated with optional `exceptionExtractor` setting.
  When no `exceptionExtractor` is set, handler uses `DefaultWithExceptionExtractor` (same as `exceptionExtractor: Default`).

Some handlers can have additional properties, e.g. built in logging handlers can add custom parameters to log. See example below.

```
exceptionHandler {
  type: BrieflyLogging
  withRateMeter: false
  exceptionExtractor: SomeCustomExtractor
  params: {
    additional: "value1"
  }
}    
```

Out of the box, Nussknacker provides following ExceptionHandler types:
- BrieflyLogging - log error to Flink logs (on `info` level, with stacktrace on `debug` level)
- VerboselyLogging - log error to Flink logs on `error` level, together with all variables (should be used mainly for debugging)
- Kafka - send errors to Kafka topic, see [common config](../../integration/KafkaIntegration.md#exception-handling) for the details.

### Configuring restart strategies

We rely on Flink restart strategies described [in documentation](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/ops/state/task_failure_recovery/).
It's also possible to configure restart strategies per scenario, using additional properties.

```
    restartStrategy {
      //if scenarioProperty is configured, strategy name will be used from this category: restartType = for-important, etc.
      //probably scenarioProperty should be configured with FixedValuesEditor
      //scenarioProperty: restartType. For simple cases one needs to configure only default strategy
      default: {
        strategy: fixed-delay
        attempts: 10
        delay: 10s
      }
      for-important {
        strategy: fixed-delay
        attempts: 30
      }
      fail-fast {
        strategy: none
      }
    }
```

### Flink Component provider configuration

#### Configuring offset for Tumbling aggregate time windows
`components.baseUnbounded.aggregateWindowsConfig.tumblingWindowsOffset` - use [ISO_8601 Duration](https://en.wikipedia.org/wiki/ISO_8601#Durations) format to configure it.
This configuration is optional, by default offset is equal 0.
This setting applies only to windows in tumbling aggregate. Might be useful when you need daily windows to be aligned to different than UTC timezone.
See example in Flink [docs](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/operators/windows/#tumbling-windows)
