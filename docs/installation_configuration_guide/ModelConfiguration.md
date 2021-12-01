---
sidebar_position: 5
---
# Model configuration

This part of configuration defines how to configure the Executor (e.g. Flink job) and its components for a given scenario type. It is processed not only at the Designer but also passed to the execution engine (e.g. Flink), that’s why it’s parsed and processed a bit differently: 

* Defaults can be defined in `defaultModelConfig.conf`. Standard deployment (e.g. with docker sample) has it [here](https://github.com/TouK/nussknacker/blob/staging/engine/flink/generic/src/main/resources/defaultModelConfig.conf).
* defaultModelConfig.conf is currently resolved both on designer (to extract information about types of data or during scenario testing) and on execution engine (e.g. on Flink). That’s why all environment variables used there have to be defined also on all Flink hosts (!). This is a technical limitation and may change in the future.
* Some Components can use a special mechanism which resolves and adds additional configuration during deployment, which is then passed to the execution engine. Such configuration is read and resolved only at the designer. Example: OpenAPI enrichers need to read its definition from external sites - so e.g. Flink cluster does not have to have access to the site with the definition. 

Look at [configuration areas](./Configuration#configuration-areas) to understand where Model configuration should be placed in Nussknacker configuration.

## Common settings settings 

| Parameter name                                     | Importance | Type     | Default value          | Description                                                                                                                                                                   |
| --------------                                     | ---------- | ----     | -------------          | -----------                                                                                                                                                                   |
| timeout                                            | Medium     | duration | 10s                    | Timeout for invocation of scenario part (including enrichers)                                                                                                                 |
| asyncExecutionConfig.bufferSize                    | Low        | int      | 200                    | Buffer size used for [async I/O](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/operators/asyncio/)                                               |
| asyncExecutionConfig.workers                       | Low        | int      | 8                      | Number of workers for thread pool used with [async I/O](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/operators/asyncio/)                        |
| asyncExecutionConfig.defaultUseAsyncInterpretation | Medium     | boolean  | true                   | Should async I/O be used by scenarios by default - if you don't use many enrichers etc. you may consider setting this flag to false                                           |
| checkpointConfig.checkpointInterval                | Medium     | duration | 10m                    | How often should checkpoints be performed by default                                                                                                                          |
| checkpointConfig.minPauseBetweenCheckpoints        | Low        | duration | checkpointInterval / 2 | [Minimal pause](https://ci.apache.org/projects/flink/flink-docs-stable/docs/deployment/config/#execution-checkpointing-min-pause) between checkpoints                         |
| checkpointConfig.maxConcurrentCheckpoints          | Low        | int      | 1                      | [Maximum concurrent checkpoints](https://ci.apache.org/projects/flink/flink-docs-stable/docs/deployment/config/#execution-checkpointing-max-concurrent-checkpoints) setting   |
| checkpointConfig.tolerableCheckpointFailureNumber  | Low        | int      |                        | [Tolerable failed checkpoint](https://ci.apache.org/projects/flink/flink-docs-stable/docs/deployment/config/#execution-checkpointing-tolerable-failed-checkpoints) setting    |
| rocksDB.enable                                     | Medium     | boolean  | true                   | Enable RocksDB state backend support                                                                                                                                          |
| rocksDB.incrementalCheckpoints                     | Medium     | boolean  | true                   | Should incremental checkpoints be used                                                                                                                                        |
| rocksDB.dbStoragePath                              | Low        | string   |                        | Allows to override RocksDB local data storage                                                                                                                                 |
| enableObjectReuse                                  | Low        | boolean  | true                   | Should allow [object reuse](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/execution/execution_configuration/)                                               |
| nkGlobalParameters.explicitUidInStatefulOperators  | Low        | boolean  | true                   | Should consistent [operator uids](https://ci.apache.org/projects/flink/flink-docs-stable/docs/ops/upgrading/#matching-operator-state) be used                                 |
| nkGlobalParameters.useTypingResultTypeInformation  | Low        | boolean  | true                   | Enables using Nussknacker additional typing information for state serialization. It makes serialization much faster, currently consider it as experimental                    |
| componentsGroupMapping                             | Low        | map      |                        | Override default grouping of basic components in toolbox panels. Component names are keys, while values are toolbox panels name (e.g. sources, enrichers etc.)                |
| componentActions                                   | Low        | `list[{id: string, title: string, icon: string, url: option[string], supportedComponentTypes: option[List[string]]}]` | `[{id: "usages", title: "Usages of component", icon: "/assets/components/actions/usages.svg"}]` | Component's actions configuration which are displayed at list of components.  Fields `title`, `icon`, `url` can contain templates: `$componentId` nad `$componentName` which are replaced by component data. Parameter `url` isn't required because some of actions like `usages` are build-in FE actions. Url is required when you want to do redirect to another page. Param `supportedComponentTypes` means component's types which can support actions.  |

<!-- TODO 
### Object naming
-->

### Configuring exception handling (Flink only)

Exception handling can be customized using provided `EspExceptionConsumer`. By default, there are two available:
- `BrieflyLogging`
- `VerboselyLogging`
More of them can be added with custom extensions. By default, basic error metrics are collected. If for some reason
  it's not desirable, metrics collector can be turned off with `withRateMeter: false` setting.

Some handlers can have additional properties, e.g. built in logging handlers can add custom parameters to log. See example below. 

```
exceptionHandler {
  type: BrieflyLogging
  withRateMeter: false
  params: {
    additional: "value1"
  }
}    
```
                                             
Out of the box, Nussknacker provides following ExceptionHandler types:
- BrieflyLogging - log error to Flink logs (on `info` level, with stacktrace on `debug` level)
- VerboselyLogging - log error to Flink logs on `error` level, together with all variables (should be used mainly for debugging)
- Kafka - send errors to Kafka topic

#### Kafka exception handling

Errors can be sent to specified Kafka topic in following json format (see below for format configuration options): 
```json
{
  "processName" : "Premium Customer Scenario",
  "nodeId" : "filter premium customers",
  "message" : "Unknown exception",
  "exceptionInput" : "SpelExpressionEvaluationException:Expression [1/0 != 10] evaluation failed, message: / by zero",
  "inputEvent" : "{ \" field1\": \"vaulue1\" }",
  "stackTrace" : "pl.touk.nussknacker.engine.api.exception.NonTransientException: mess\n\tat pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionConsumerSerializationSpec.<init>(KafkaExceptionConsumerSerializationSpec.scala:24)\n\tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n\tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)\n\tat java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)\n\tat java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:490)\n\tat java.base/java.lang.Class.newInstance(Class.java:584)\n\tat org.scalatest.tools.Runner$.genSuiteConfig(Runner.scala:1431)\n\tat org.scalatest.tools.Runner$.$anonfun$doRunRunRunDaDoRunRun$8(Runner.scala:1239)\n\tat scala.collection.immutable.List.map(List.scala:286)\n\tat org.scalatest.tools.Runner$.doRunRunRunDaDoRunRun(Runner.scala:1238)\n\tat org.scalatest.tools.Runner$.$anonfun$runOptionallyWithPassFailReporter$24(Runner.scala:1033)\n\tat org.scalatest.tools.Runner$.$anonfun$runOptionallyWithPassFailReporter$24$adapted(Runner.scala:1011)\n\tat org.scalatest.tools.Runner$.withClassLoaderAndDispatchReporter(Runner.scala:1509)\n\tat org.scalatest.tools.Runner$.runOptionallyWithPassFailReporter(Runner.scala:1011)\n\tat org.scalatest.tools.Runner$.run(Runner.scala:850)\n\tat org.scalatest.tools.Runner.run(Runner.scala)\n\tat org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner.runScalaTest2or3(ScalaTestRunner.java:38)\n\tat org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner.main(ScalaTestRunner.java:25)",
  "timestamp" : 1623758738000,
  "host" : "teriberka.pl",
  "additionalData" : {
    "scenarioCategory" : "Marketing"
  }
}
```
Following properties can be configured:

| Name                  | Default value | Description                                                                                                                                                                                |
| ------------          | ------------- | ------------                                                                                                                                                                               |
| topic                 | -             | Topic where errors will be sent. It should be configured separately (or topic `auto.create` setting should be enabled on Kafka cluster)                                                    |
| stackTraceLengthLimit | 50            | Limit of stacktrace length that will be sent (0 to omit stacktrace at all)                                                                                                                 |
| includeHost           | true          | Should name of host where error occurred (e.g. TaskManager in case of Flink) be included. Can be misleading if there are many network interfaces or hostname is improperly configured)     |
| includeInputEvent     | false         | Should input event be serialized (can be large or contain sensitive data so use with care)                                                                                                 |
| useSharedProducer     | false         | For better performance shared Kafka producer can be used (by default it's created and closed for each error), shared Producer is kind of experimental feature and should be used with care |
| additionalParams      | {}            | Map of fixed parameters that can be added to Kafka message                                                                                                                                 |

### Configuring restart strategies (Flink only)
         
We rely on Flink restart strategies described [in documentation](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/execution/task_failure_recovery/).
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
      for-very-important {
        strategy: fixed-delay
        attempts: 50
      }
    }
```
                             
### Configuration of UI attributes of components
In model configuration you can also define some UI attributes of components. This can be useful for tweaking of appearance of generated components (like from OpenAPI), 
in most cases you should not need to defined these settings. The settings you can configure include:
* icons - `icon` property
* documentation - `docsUrl` property
* should component be disabled - `disabled` property
* in which toolbox panel the component should appear (`componentGroup` property)  
* `params` configuration (allows to override default component settings):
  * `editor` - `BoolParameterEditor`, `StringParameterEditor`, `DateParameterEditor` etc. 
  * `validators` - `MandatoryParameterValidator`, `NotBlankParameterValidator`, `RegexpParameterValidator`
  * `defaultValue`
  * `label`

Example (see [dev application config](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/sample/src/main/resources/defaultModelConfig.conf#L18) for more examples):
```
  componentsUiConfig {
    customerService {
      params {
        serviceIdParameter {
            defaultValue: "customerId-10"
            editor: "StringParameterEditor"
            validators: [ 
              {
                type: "RegExpParameterValidator"
                pattern: "customerId-[0-9]*"
                message: "has to match customer id format"
                description: "really has to match..."
              }
            ]
            label: "Customer id (from CRM!)
        }
      }
      docsUrl: "https://en.wikipedia.org/wiki/Customer_service"
      icon: "icon_file.svg"
    }
  }

```

### Additional properties              

It's possible to add additional properties for scenario. 
They can be used for allowing more detailed scenario information (e.g. pass information about marketing campaign target etc.), 
they can also be used in various Nussknacker extensions: 

Example (see [dev application config](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/sample/src/main/resources/defaultModelConfig.conf#L61) for more examples):

```
additionalPropertiesConfig {
  campaignType: {
    editor: { type: "StringParameterEditor" }
    validators: [ { type: "MandatoryParameterValidator" } ]
    label: "Campaign type"
    defaultValue: "Generic campaign"
  }
  ...
}
```
Configuration is similar to [component configuration](#configuration-of-ui-attributes-of-components)


## Kafka configuration

Currently, it's only possible to use one Kafka cluster for one model configuration. This configuration is used for all
Kafka based sources and sinks
                      
Important thing to remember is that Kafka server addresses/schema registry addresses have to be resolvable from:
- Nussknacker Designer host (to enable schema discovery and scenario testing)
- Flink cluster (both jobmanagers and taskmanagers) hosts - to be able to run job

| Name                                                                              | Importance | Type                       | Default value    | Description                                                                                                                                                                                                                                                  |
| ----------                                                                        | ---------- | ----------------           | -------------    | ------------                                                                                                                                                                                                                                                 |
| kafka.kafkaAddress                                                                | High       | string                     |                  | Comma separated list of [bootstrap servers](https://kafka.apache.org/documentation/#producerconfigs_bootstrap.servers)                                                                                                                                       |
| kafka.kafkaProperties."schema.registry.url"                                       | High       | string                     |                  | Comma separated list of [schema registry](https://docs.confluent.io/platform/current/schema-registry/index.html)                                                                                                                                             |
| kafka.kafkaProperties                                                             | Medium     | map                        |                  | Additional configuration of [producers](https://kafka.apache.org/documentation/#producerconfigs) or [consumers](https://kafka.apache.org/documentation/#consumerconfigs)                                                                                     |
| kafka.useStringForKey                                                             | Medium     | boolean                    | true             | Should we assume that Kafka message keys are in plain string format (not in Avro)                                                                                                                                                                            |
| kafka.kafkaEspProperties.forceLatestRead                                          | Medium     | boolean                    | false            | If scenario is restarted without state (if started with non-empty Flink state, the offsets are always taken from Flink state), should offsets of source consumers be reset to latest (can be useful in test enrivonments)                                    |
| kafka.kafkaEspProperties.defaultMaxOutOfOrdernessMillis                           | Medium     | duration                   | 60s              | Configuration of [bounded of orderness watermark generator](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/event-time/built_in/#fixed-amount-of-lateness) used by Kafka sources                                                  |
| kafka.consumerGroupNamingStrategy                                                 | Low        | processId/processId-nodeId | processId-nodeId | How consumer groups for sources should be named                                                                                                                                                                                                              |
| kafka.avroKryoGenericRecordSchemaIdSerialization                                  | Low        | boolean                    | true             | Should AVRO messages from topics registered in schema registry be serialized in optimized way, by serializing only schema id, not the whole schema                                                                                                           |
| kafka.topicsExistenceValidationConfig.enabled                                     | Low        | boolean                    | false            | Should we validate existence of topics if no [auto.create.topics.enable](https://kafka.apache.org/documentation/#brokerconfigs_auto.create.topics.enable) is false on Kafka cluster - note, that it may require permissions to access Kafka cluster settings |
| kafka.topicsExistenceValidationConfig.validatorConfig.autoCreateFlagFetchCacheTtl | Low        | duration                   | 5 minutes        | TTL for checking Kafka cluster settings                                                                                                                                                                                                                      |
| kafka.topicsExistenceValidationConfig.validatorConfig.topicsFetchCacheTtl         | Low        | duration                   | 30 seconds       | TTL for caching list of existing topics                                                                                                                                                                                                                      |
| kafka.topicsExistenceValidationConfig.validatorConfig.adminClientTimeout          | Low        | duration                   | 500 milliseconds | Timeout for communicating with Kafka cluster                                                                                                                                                                                                                 |


## Additional components configuration 

### Configuration of component providers

```
  components {
    sqlHsql {
      providerType: sql
      jdbcUrl: jdbc:hsql...//
      admin/pass
    }
    sqlOracle {
      providerType: sql
      jdbcUrl: jdbc:oracle...//
      admin/pass
    }
    prinzH20 {
      providerType: prinzH20
      h2oLocation:
    }
    prinzMlFlow {
      //this is not needed, as configuration is named just as provider
      //providerType: prinzMLFlow
      mlFlowUrl:
    }
    //not needed in our domain
    aggregation {
      disabled: true
    }
  }
```

### Extra components providers 

You can read about list of extra components in [Extra components](ExtraComponents.md) section
