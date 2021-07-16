## Model configuration

This part of configuration defines how to configure the executor (e.g. Flink job) and its components for a given scenario type. It is processed not only at the designer but also passed to the execution engine (e.g. Flink), that’s why it’s parsed and processed a bit differently. 

* Defaults can be defined in `defaultModeConfig.conf. `Standard deployment (e.g. with docker sample) has it [here/link]
* `defaultModelConfig.conf` is resolved both on designer and on execution engine (e.g. on Flink). That’s why all environment variables used there have to be defined also on all Flink hosts!
* Some components can use special mechanism which resolve and add additional configuration during deployment, which is then passed to the execution engine. Example: OpenAPI...
* …
* Defaults
* Configuration reload
* Configuration resolved within designer vs configuration resolved during execution

### General settings 

| Parameter name                                     | Importance | Type     | Default value          | Description |
| --------------                                     | ---------- | ----     | -------------          | ----------- |
| timeout                                            | Medium     | duration | 10s                    |             |
| asyncExecutionConfig.bufferSize                    | Low        | int      | 200                    |             |
| asyncExecutionConfig.workers                       | Low        | int      | 8                      |             |
| asyncExecutionConfig.defaultUseAsyncInterpretation | Medium     | boolean  | true                   |             |
| checkpointConfig.checkpointInterval                | Medium     | duration | 10m                    |             |
| checkpointConfig.minPauseBetweenCheckpoints        | Low        | duration | checkpointInterval / 2 |             |
| checkpointConfig.maxConcurrentCheckpoints          | Low        | int      | 1                      |             |
| checkpointConfig.tolerableCheckpointFailureNumber  | Low        | int      |                        |             |
| rocksDB.checkpointDataUri                          | High       | string   |                        |             |
| rocksDB.incrementalCheckpoints                     | Medium     | boolean  | true                   |             |
| rocksDB.dbStoragePath                              | Low        | string   |                        |             |
| enableObjectReuse                                  | Low        | boolean  | true                   |             |
| nkGlobalParameters.explicitUidInStatefulOperators  | Low        | boolean  | true                   |             |
| nkGlobalParameters.useTypingResultTypeInformation  | Low        | boolean  | false                  |             |
| eventTimeMetricSlideDuration                       | Low        | duration |                        |             |
| nodeCategoryMapping                                | Low        |          |                        |             |

### Additional properties              

TODO!

### Object naming
    
TODO!

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
                             
### Configuration of component providers

```
  componentProviders {
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

###Configuration of UI attributes of components
In model configuration you can also define some UI attributes of components. These include:
* default values of fields
* icons

```
  nodes {
    containsDefaultValue {
      defaultValues {
        parameterName = "parameterValue"
      }
    },
    serviceWithDocumentation {
      docsUrl: "https://en.wikipedia.org/wiki/Customer_service"
    }
    hasSpecialIcon {
      icon: "icon_file.svg"
    }
  }

```
* `containsDefaultValue`, `hasSpecialIcon`, `serviceWithDocumentation` - nodes names
* `parameterName` - node parameter name you'd like to assign default value
* `parameterValue` - value of default parameter
* `docsUrl` - link to documentation (e.g. confluence page)
* `icon`- path to icon file


### Kafka

Currently, it's only possible to                            

| Name                                                                              | Importance | Type                       | Default value    | Description  |
| ----------                                                                        | ---------- | ----------------           | -------------    | ------------ |
| kafka.kafkaAddress                                                                | High       | string                     |                  |              |
| kafka.kafkaProperties."schema.registry.url"                                       | High       | string                     |                  |              |
| kafka.kafkaProperties                                                             | Medium     | map                        |                  |              |
| kafka.useStringForKey                                                             | Medium     | boolean                    | true             |              |
| kafka.kafkaEspProperties.forceLatestRead                                          | Medium     | boolean                    | false            |              |
| kafka.kafkaEspProperties.defaultMaxOutOfOrdernessMillis                           | Medium     | duration                   | 60s              |              |
| kafka.kafkaEspProperties.autoRegisterRecordSchemaIdSerialization                  | Low        | boolean                    | true             |              |
| kafka.consumerGroupNamingStrategy                                                 | Low        | processId/processId-nodeId | processId-nodeId |              |
| kafka.avroKryoGenericRecordSchemaIdSerialization                                  | Low        | boolean                    | true             |              |
| kafka.topicsExistenceValidationConfig.enabled                                     | Low        | boolean                    | false            |              |
| kafka.topicsExistenceValidationConfig.validatorConfig.autoCreateFlagFetchCacheTtl | Low        | duration                   | 5 minutes        |              |
| kafka.topicsExistenceValidationConfig.validatorConfig.topicsFetchCacheTtl         | Low        | duration                   | 30 seconds       |              |
| kafka.topicsExistenceValidationConfig.validatorConfig.adminClientTimeout          | Low        | duration                   | 500 milliseconds |              |


## Additional components configuration 

* [OpenAPI](../components/OpenAPI.md)
* [SQL](../components/Sql.md)