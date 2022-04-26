---
sidebar_position: 5
---
# Model configuration

This part of configuration defines how to configure Components and some of the runtime behaviour (e.g. error handling) for a given scenario type (Streaming-Lite or Streaming-Flink). It is processed not only at the Designer but also passed to the execution engine (e.g. Flink), that’s why it’s parsed and processed a bit differently: 

* Defaults can be defined in `defaultModelConfig.conf`. Standard deployment (e.g. with docker sample) has it [here](https://github.com/TouK/nussknacker/blob/staging/defaultModel/src/main/resources/defaultModelConfig.conf).
* defaultModelConfig.conf is currently resolved both on designer (to extract information about types of data or during scenario testing) and on execution engine (e.g. on Flink or in Streaming-Lite runtime). That’s why all environment variables used there have to be defined also on all Flink/Streaming-Lite runtime hosts (!). This is a technical limitation and may change in the future.
* Some Components can use a special mechanism which resolves and adds additional configuration during deployment, which is then passed to the execution engine. Such configuration is read and resolved only at the designer. Example: OpenAPI enrichers need to read its definition from external sites - so e.g. Flink cluster does not have to have access to the site with the definition. 

Look at [configuration areas](./#configuration-areas) to understand where Model configuration should be placed in Nussknacker configuration.
                  
## ClassPath configuration

Nussknacker looks for components and various extensions in jars on the Model classpath, default config [example here](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/application.conf) to see where classpath can be configured.

By default, the following configuration is used:
```
classPath: ["model/defaultModel.jar", "model/flinkExecutor.jar", "components/flink"]
```
Make sure you have all necessary entries properly configured:
- Jar with model - unless you used custom model, this should be `model/defaultModel.jar`
- All jars with additional components, e.g. `"components/flink/flinkBase.jar", "components/flink/flinkKafka.jar"`
- `flinkExecutor.jar` for Flink Engine. This contains executor of scenarios in Flink cluster.

Note that as classPath elements you can use:
- full URLs (e.g. "https://repo1.maven.org/maven2/pl/touk/nussknacker/nussknacker-lite-base-components_2.12/1.1.0/nussknacker-lite-base-components_2.12-1.1.0.jar")
- file paths (absolute or relative to Nussknacker installation dir)
- paths to directories (again, absolute or relative) - in this case all files in the directory will be used (including the ones found in subdirectories).

If the given path element in the `classPath` is relative, it should be relative to the path determined by the `$WORKING_DIR ` [environment variable](./Installation.md#basic-environment-variables).

<!-- TODO 
### Object naming
-->

## Components configuration 

Nussknacker comes with a set of provided components. Some of them (e.g. `filter`, `variable`, aggregations in Flink, `for-each`, `union`) are 
predefined and accessible by default. Others need additional configuration - the most important ones are enrichers, 
where you have to set e.g. JDBC URL or external service address.

Check documentation of available components that you can configure:
- [OpenAPI](../components/OpenAPI.md) Supports accessing external APIs directly from scenario 
- [SQL](../components/Sql.md)         Supports access to SQL database engines    


### Configuration of component providers

Below you can see typical component configuration, each section describes configuration of one component provider.

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
      #this is not needed, as configuration is named just as provider
      #providerType: prinzMLFlow
      mlFlowUrl:
    }
    #we can disable particular component provider, if it's not needed in our installation
    #note: you cannot disable certain basic components like filter, variable, switch and split
    aggregation {
      disabled: true
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

Example (see [dev application config](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/resources/defaultModelConfig.conf#L18) for more examples):
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

### Component links

You can add additional links that will be shown in `Components` tab. They can be used e.g. to point to 
custom dashboards with component performance or point to some external system (link to documentation is configured by default). 
The config format is as follows:
```
componentLinks: [
  {
    id: "sourceSystem"
    title: "Source system"
    icon: "/assets/components/CustomNode.svg"
    url: "https://myCustom.com/dataSource/$componentName" 
    supportedComponentTypes: ["openAPIEnricher1"]
  }
]
```
Fields `title`, `icon`, `url` can contain templates: `$componentId` nad `$componentName` which are replaced by component data. Param `supportedComponentTypes` means component's types which can support links.

### Component group mapping

You can override default grouping of basic components in toolbox panels with `componentsGroupMapping` setting. Component names are keys, while values are toolbox panels name (e.g. sources, enrichers etc.)                |

## Common Kafka configuration

### Kafka connection configuration

Both engines share common Kafka configuration, see [Streaming-Lite](model/Lite#kafka-configuration) or [Streaming-Flink](model/Flink#kafka-configuration) docs for details on configuring sources/sinks.

Important thing to remember is that Kafka server addresses/schema registry addresses have to be resolvable from:
- Nussknacker Designer host (to enable schema discovery and scenario testing)
- Streaming-Lite runtime - to be able to run job

| Name                                                                          | Importance | Type             | Default value    | Description                                                                                                                                                                                                                                                  |
|-- --------------------------------------------------------------------------- | ---------- | ---------------- | -------------    | ------------                                                                                                                                                                                                                                                 |
| kafkaAddress                                                                  | High       | string           |                  | Comma separated list of [bootstrap servers](https://kafka.apache.org/documentation/#producerconfigs_bootstrap.servers)                                                                                                                                       |
| kafkaProperties."schema.registry.url"                                         | High       | string           |                  | Comma separated list of [schema registry](https://docs.confluent.io/platform/current/schema-registry/index.html)                                                                                                                                             |
| kafkaProperties                                                               | Medium     | map              |                  | Additional configuration of [producers](https://kafka.apache.org/documentation/#producerconfigs) or [consumers](https://kafka.apache.org/documentation/#consumerconfigs)                                                                                     |
| useStringForKey                                                               | Medium     | boolean          | true             | Should we assume that Kafka message keys are in plain string format (not in Avro)                                                                                                                                                                            |
| kafkaEspProperties.forceLatestRead                                            | Medium     | boolean          | false            | If scenario is restarted, should offsets of source consumers be reset to latest (can be useful in test enrivonments)                                                                                                                                         |
| topicsExistenceValidationConfig.enabled                                       | Low        | boolean          | false            | Should we validate existence of topics if no [auto.create.topics.enable](https://kafka.apache.org/documentation/#brokerconfigs_auto.create.topics.enable) is false on Kafka cluster - note, that it may require permissions to access Kafka cluster settings |
| topicsExistenceValidationConfig.validatorConfig.autoCreateFlagFetchCacheTtl   | Low        | duration         | 5 minutes        | TTL for checking Kafka cluster settings                                                                                                                                                                                                                      |
| topicsExistenceValidationConfig.validatorConfig.topicsFetchCacheTtl           | Low        | duration         | 30 seconds       | TTL for caching list of existing topics                                                                                                                                                                                                                      |
| topicsExistenceValidationConfig.validatorConfig.adminClientTimeout            | Low        | duration         | 500 milliseconds | Timeout for communicating with Kafka cluster                                                                                                                                                                                                                 |
| schemaRegistryCacheConfig.availableSchemasExpirationTime                      | Low        | duration         | 10 seconds       | How often available schemas cache will be invalidated. This determine how long you will wait after adding new schema or new schema version until it will be available in Designer                                                                            |
| schemaRegistryCacheConfig.parsedSchemaAccessExpirationTime                    | Low        | duration         | 2 hours          | How long parsed schema will be cached after first access to it                                                                                                                                                                                               |
| schemaRegistryCacheConfig.maximumSize                                         | Low        | number           | 10000            | Maximum entries size for each caches: available schemas cache and parsed schema cache                                                                                                                                                                        |

### Kafka exception handling


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


Following properties can be configured (please look at correct engine page : [Streaming-Lite](model/Lite#exception-handling) or [Streaming-Flink](model/Flink#configuring-exception-handling), 
to see where they should be set):

| Name                  | Default value | Description                                                                                                                                                                                |
| ------------          | ------------- | ------------                                                                                                                                                                               |
| topic                 | -             | Topic where errors will be sent. It should be configured separately (or topic `auto.create` setting should be enabled on Kafka cluster)                                                    |
| stackTraceLengthLimit | 50            | Limit of stacktrace length that will be sent (0 to omit stacktrace at all)                                                                                                                 |
| includeHost           | true          | Should name of host where error occurred (e.g. TaskManager in case of Flink) be included. Can be misleading if there are many network interfaces or hostname is improperly configured)     |
| includeInputEvent     | false         | Should input event be serialized (can be large or contain sensitive data so use with care)                                                                                                 |
| useSharedProducer     | false         | For better performance shared Kafka producer can be used (by default it's created and closed for each error), shared Producer is kind of experimental feature and should be used with care |
| additionalParams      | {}            | Map of fixed parameters that can be added to Kafka message                                                                                                                                 |

## Scenario's additional properties              

It's possible to add additional properties for scenario. 
They can be used for allowing more detailed scenario information (e.g. pass information about marketing campaign target etc.), 
they can also be used in various Nussknacker extensions: 

Example (see [dev application config](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/resources/defaultModelConfig.conf#L61) for more examples):

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
