Configuration
=============

Configuration file is read by UI application during startup - currently it's not possible to change it without restart.
The format of configuration is [Typesafe Config](https://github.com/typesafehub/config).

The configuration files defines
* settings of UI itself
* settings of various services used by model
* means of communicating with Apache Flink cluster

Sample configuration looks like this:
```config
db {
  url: "jdbc:hsqldb:file:db/db;sql.syntax_ora=true"
  driver: "org.hsqldb.jdbc.JDBCDriver"
  user: "SA"
  password: ""
  connectionTimeout: 30000
}

categoriesConfig: {
  "FraudDetection": "streaming",
  "Recommendations": "streaming",
  "Default": "streaming"
}

usersFile: "./conf/users.conf"
environment: "demo"
attachmentsPath: "/tmp/touk/esp-frontend/attachments"
       
processTypes {
  streaming {
    engineConfig {    
      type: "flinkStreaming"
      restUrl: "http://localhost:8081"
      parallelism: 4
      jobManagerTimeout: 1m
    } 
    modelConfig {
      classPath: ["code-assembly.jar"]
      timeout: 10s
      checkpointConfig {
          checkpointInterval: 10m
          minPauseBetweenCheckpoints: 5m
          maxConcurrentCheckpoints: 1
          tolerableCheckpointFailureNumber: 6
      }
      delayBetweenAttempts: "10s"
      kafka = {
        kafkaAddress = "kafka:9092"
      }
    }
  }
}


metricsSettings {
  url: "http://localhost:3000/d/$dashboard?theme=dark&var-processName=$process&var-env=demo"
  defaultDashboard: "nussknacker-scenario"
  processingTypeToDashboard: {
    "request-response": "standalone",
    "streaming": "nussknacker-scenario"
  }
}

countsSettings {
  influxUrl: "http://localhost:3000/api/datasources/proxy/1/query"
  user: "admin"
  password: "admin"
}

```             
###Default configurations and overriding them
Default configuration for UI is in [defaultUiConfig.conf](https://github.com/TouK/nussknacker/blob/staging/ui/server/src/main/resources/defaultUiConfig.conf).
We don't use ```reference.conf``` at the moment, as classloaders of model and ui are not separated, and we don't want UI config to be passed to model. 

Default configuration of models is taken from ```defaultModelConfig.conf``` files in model jar (see e.g. [defaultModelConfig.conf](https://github.com/TouK/nussknacker/blob/staging/engine/flink/generic/src/main/resources/defaultModelConfig.conf)).
You can also use ```reference.conf``` in model jars, however we found some problems with substitutions (see docs in [ModelConfigLoader](https://github.com/TouK/nussknacker/blob/staging/engine/interpreter/src/main/scala/pl/touk/nussknacker/engine/modelconfig/ModelConfigLoader.scala)).
 
How can you override default configuration? 
Detailed rules are described in [documentation](https://github.com/lightbend/config#merging-config-trees). For example, if in the ```defaultModelConfig.conf``` we have following entries: 
```hocon 
{
   timeout: 10s
   delayBetweenAttempts: 10s
   customProperties {
    property1: 11
   } 
}
```              
you can override them in ```application.conf``` like this:
```hocon 
processTypes {
  type1 {
    modelConfig {
      timeout: 20s
      customProperties {
        property1: 13
      }
    }
  }
}
```  
Please note that you have to define overridden properties in appropriate ```modelConfig``` section. If you would need to
modify the config while loading the model, you can provide your own implementation of ```ModelConfigLoader```.

Overriding UI configuration is straightforward: 
```hocon
  environmentAlert {
    content: "DEVELOPMENT ENVIRONMENT" 
  }
```  
 
All configurations can also be overridden with environmental variables. Please consult [TypesafeConfig documentation](https://github.com/lightbend/config#optional-system-or-env-variable-overrides).
In particular, Nussknacker docker image is executed with ```-Dconfig.override_with_env_vars=true```   
For example, to override samples above you would have to define:
```shell script
   CONFIG_FORCE_processTypes_type1_modelConfig_timeout=30s
   CONFIG_FORCE_environmentAlert_content="MY environment"  
```

##UI configuration

###Database

###Categories
Every process has to belong to a group called category. For example, in one Nussknacker installation you can 
have processes detecting frauds and those implementing marketing campaigns. Category configuration looks like this:
```
categoriesConfig: {
  "marketing": "streaming",
  "fraud": "streaming",
}
```
For each category you have to define its processing type (`streaming` in examples above). You can read about processing
types and their configurations below.

### Process Toolbar Configuration

Toolbars and buttons at process window are configurable, you can configure params:

* uuid - optional uuid identifier which determines unique code for FE localstorage cache, 
  default: null - we generate uuid from hashcode config
* topLeft - optional top left panel, default: empty list
* bottomLeft - optional bottom left panel, default: empty list
* topRight - optional top right panel, default: empty list
* bottomRight - optional bottom right panel, default: empty list

Example configuration:

```
processToolbarConfig {
  defaultConfig {
    topLeft: [
      { type: "tips-panel" }
    ]
    topRight: [
      {
        type: "process-info-panel"
        buttons: [
          { type: "process-save", disabled: { subprocess: false, archived: true, type: "oneof" } }
          { type: "custom-link", title: "Metrics for $processName", url: "/metrics/$processId" }
        ]
      }
    ]
  }
}
```

We can also create special configuration for each category by:
```
processToolbarConfig {
  categoryConfig {
   "CategoryName" {
        id: "58f1acff-d864-4d66-9f86-0fa7319f7043"
        topLeft: [
          { type: "creator-panel", hidden: {subprocess: true, archived: false, type: "allof"} }
        ]
    } 
  }
}
```

#### Toolbar Panel Conditioning

Configuration allow us to:
* hiding panels
* hiding or disabling buttons

Each of this function can be configured by condition expression where we can use three parameters:
* `subprocess: boolean` - if true then condition match only subprocess, by default ignored
* `archived: boolean` - if true then condition match only archived, by default ignored
* `type: allof / oneof` - information about that checked will be only one condition or all conditions

#### Toolbar Panel Templating

Configuration allows to templating params like: 
* `name` - available only on Buttons
* `title`- available on Panels and  Buttons 
* `url` - available only on CustomLink and CustomAction buttons 
* `icon`- available only on Buttons

Right now we allow to template two elements:
* process id -`$processId`
* process name - `$processName`

Example usage:
* `title: "Metrics for $processName"`
* `name: "deploy $processName"`
* `url: "/metrics/$processId" `
* `icon: "/assets/process-icon-$processId"`

#### Default Process Panel Configuration

```
processToolbarConfig {
  defaultConfig {
    topLeft: [
      { type: "tips-panel" }
      { type: "creator-panel", hidden: { archived: true } }
      { type: "versions-panel" }
      { type: "comments-panel" }
      { type: "attachments-panel" }
    ]
    topRight: [
      {
        type: "process-info-panel"
        buttons: [
          { type: "process-save", title: "Save changes", disabled: { archived: true } }
          { type: "process-deploy", disabled: { subprocess: true, archived: true, type: "oneof" } }
          { type: "process-cancel", disabled: { subprocess: true, archived: true, type: "oneof" } }
          { type: "custom-link", name: "metrics", icon: "/assets/buttons/metrics.svg", url: "/metrics/$processName", disabled: { subprocess: true } }
        ]
      }
      {
        id: "view-panel"
        type: "buttons-panel"
        title: "view"
        buttons: [
          { type: "view-business-view" }
          { type: "view-zoom-in" }
          { type: "view-zoom-out" }
          { type: "view-reset" }
        ]
      }
      {
        id: "edit-panel"
        type: "buttons-panel"
        title: "edit"
        hidden: { archived: true }
        buttonsVariant: "small"
        buttons: [
          { type: "edit-undo" }
          { type: "edit-redo" }
          { type: "edit-copy" }
          { type: "edit-paste" }
          { type: "edit-delete" }
          { type: "edit-layout" }
        ]
      }
      {
        id: "process-panel"
        type: "buttons-panel"
        title: "process"
        buttons: [
          { type: "process-properties", hidden: { subprocess: true } }
          { type: "process-compare" }
          { type: "process-migrate", disabled: { archived: true } }
          { type: "process-import", disabled: { archived: true } }
          { type: "process-json" }
          { type: "process-pdf" }
          { type: "process-archive", hidden: { archived: true } }
          { type: "process-unarchive", hidden: { archived: false } }
        ]
      }
      {
        id: "test-panel"
        type: "buttons-panel"
        title: "test"
        hidden: { subprocess: true }
        buttons: [
          { type: "test-from-file", disabled: { archived: true } }
          { type: "test-generate", disabled: { archived: true } }
          { type: "test-counts" }
          { type: "test-hide" }
        ]
      }
      {
        id: "group-panel"
        type: "buttons-panel"
        title: "group"
        hidden: { archived: true }
        buttons: [
          { type: "group" }
          { type: "ungroup" }
        ]
      }
      { type: "details-panel" }
    ]
  }
}
```

###Monitoring config
```
metricsSettings {
  url: "http://localhost:3000/d/$dashboard?theme=dark&var-processName=$process&var-env=demo"
  defaultDashboard: "nussknacker-scenario"
  processingTypeToDashboard: {
    "request-response": "standalone",
    "streaming": "nussknacker-scenario"
  }
}
```

###Akka configuration

In `akka` section you can configure actor system used by GUI, e.g:
```
akka {
  http {
    server.parsing.max-content-length = 300000000 #300MB
  }
}

```

###Other configurations

* `usersFile` - location of file with user configuration
* `environment` - key of environment (used e.g. for alerts) - e.g. test or production
* `attachmentsPath` - location on disk where attachments will be stored

##Processing types 
One installation of Nussknacker can handle many different processing engines - currently the main supported engine is
Flink in streaming mode. Processing engines are defined in `processTypes` section. You can e.g. have two processing
types pointing to separate Flink clusters. Each processing engine has its name (e.g. `flinkStreaming`). 
Processing type configuration consists of two main parts:
* engine configuration
* model configuration

We describe them below

###Engine configuration
Configuration of communication with processing engine. Below we present Flink engine configuration as an example:

```
engingConfig {     
  type: "flinkStreaming"
  restUrl: "http://localhost:8081"
  parallelism: 4
  jobManagerTimeout: 1m
}
```
In this section you can put all configuration values for Flink client. We are using Flink REST API so the only
required parameter is `restUrl` - which defines location of Flink JobManager
* `jobManagerTimeout` (e.g. 1m) - timeout used in communication with Flink cluster

### Process configuration

Configuration of processes has few common keys:
*  `classPath` - list of files/URLs with jars with model for processes
*  `timeout` (e.g. 10s)- for synchronous services
*  `checkpointConfig` (more about checkpoint configuration you can find in [Flink Documentation](https://ci.apache.org/projects/flink/flink-docs-release-{{book.flinkMajorVersion}}/api/java/org/apache/flink/streaming/api/environment/CheckpointConfig.html) - only some options are available)
    * `checkpointInterval` - e.g. 10m
    * `minPauseBetweenCheckpoints` - optional, default to half of `checkpointInterval`, e.g. 5m
    * `maxConcurrentCheckpoints` - optional, default to 1, e.g. 4
    * `tolerableCheckpointFailureNumber` - optional, default 0, e.g. 6

If configuration does not contain `checkpointConfig`, `checkpointInterval` and process does not contain `checkpointInterval` in its properties then checkpoints are not enabled for process.

The rest of model configuration depends on your needs - all the properties defined here will be passed to ```ProcessConfigCreator``` as explained in [API](API.md) documentation.
                      
#### Configuring exception handling (Flink only)

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

##### Kafka exception handling

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

| Name         | Default value | Description |
| ------------ | ------------- | ------------|
| topic        | -             | Topic where errors will be sent. It should be configured separately (or topic `auto.create` setting should be enabled on Kafka cluster) | 
| stackTraceLengthLimit | 50   | Limit of stacktrace length that will be sent (0 to omit stacktrace at all)            | 
| includeHost  | true          | Should name of host where error occurred (e.g. TaskManager in case of Flink) be included. Can be misleading if there are many network interfaces or hostname is improperly configured)             |
| includeInputEvent | false    | Should input event be serialized (can be large or contain sensitive data so use with care)            |
| useSharedProducer | false    | For better performance shared Kafka producer can be used (by default it's created and closed for each error), shared Producer is kind of experimental feature and should be used with care            |
| additionalParams  | {}       | Map of fixed parameters that can be added to Kafka message            |

#### Configuring restart strategies (Flink only)
         
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
