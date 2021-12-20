# Overview

Please make sure you know common [Glossary](/documentation/about/GLOSSARY) and [SpEL](../scenarios_authoring/Spel.md) (especially the Data types section) before proceeding further. 

This part of the documentation describes various ways of customizing Nussknacker - from adding own Components to adding listeners for various Designer actions. 
The main way of adding customizations to Nussknacker is [ServiceLoader](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html) 

**Please make sure to put jars with custom code on right classpath**
- Customizations of model (in particular `ComponentProviders`) should be configured in [Model config](../installation_configuration_guide/Configuration) in 
`modelConfig.classpath`. 
- Code of Designer customizations should go to the main designer classpath (e.g. put the jars in the `lib` folder)
 
## Types

Types of expressions are based on Java types. Nussknacker provides own abstraction of type, which can contain more information about given type than pure Java class - e.g. object type (like in [Typescript](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#object-types)) is represented in runtime as Java `Map`, but during compilation we know the structure of this map. 
We also handle union types (again, similar to [Typescript](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#union-types)) and we have `Unknown` type which is represented as Java `Object` in runtime, but behaves a bit like [Typescript any](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#any) (please note that `Unknown` should be avoided as default [Security settings](./Security) settings prohibit omitting typechecking with `Unknown`.
 
`TypingResult` is the main class (sealed trait) that represents type of expression in Nussknacker.
`Typed` object has many methods for constructing `TypingResult`


      
## Components and ComponentProviders

[Components](https://docs.nussknacker.io/about/GLOSSARY#component) are main method of customizing Nussknacker. Components are created by configured `ComponentProvider` instances. 
There are following types of components:
- `SourceFactory`
- `SinkFactory`
- `CustomStreamTransformer` - types of transformations depend on type of Engine
- `Service` - mainly for defining stateless enrichments


## Other SPIs for Nussknacker customization (documentation will follow soon...)

### Model customization

- Flink specific
  - [FlinkEspExceptionConsumerProvider](https://github.com/TouK/nussknacker/blob/staging/engine/flink/api/src/main/scala/pl/touk/nussknacker/engine/flink/api/exception/FlinkEspExceptionConsumer.scala)
  - [FlinkCompatibilityProvider](https://github.com/TouK/nussknacker/blob/staging/engine/flink/engine/src/main/scala/pl/touk/nussknacker/engine/process/FlinkCompatibilityProvider.scala)
  - [SerializersRegistrar](https://github.com/TouK/nussknacker/blob/staging/engine/flink/api/src/main/scala/pl/touk/nussknacker/engine/flink/api/serialization/SerializersRegistrar.scala)
  - [TypingResultAwareTypeInformationCustomisation](https://github.com/TouK/nussknacker/blob/staging/engine/flink/api/src/main/scala/pl/touk/nussknacker/engine/flink/api/typeinformation/TypingResultAwareTypeInformationCustomisation.scala)
  - [TypeInformationDetection](https://github.com/TouK/nussknacker/blob/staging/engine/flink/api/src/main/scala/pl/touk/nussknacker/engine/flink/api/typeinformation/TypeInformationDetection.scala)
- [NodeAdditionalInfoProvider](https://github.com/TouK/nussknacker/blob/staging/interpreter/src/main/scala/pl/touk/nussknacker/engine/additionalInfo/NodeAdditionalInfoProvider.scala)
- [ToJsonEncoder](https://github.com/TouK/nussknacker/blob/staging/utils/util/src/main/scala/pl/touk/nussknacker/engine/util/json/BestEffortJsonEncoder.scala)
- [ObjectNamingProvider](https://github.com/TouK/nussknacker/blob/staging/utils/util/src/main/scala/pl/touk/nussknacker/engine/util/namespaces/ObjectNamingProvider.scala)
- [ModelConfigLoader](https://github.com/TouK/nussknacker/blob/staging/interpreter/src/main/scala/pl/touk/nussknacker/engine/modelconfig/ModelConfigLoader.scala)
- [ProcessMigrations](https://github.com/TouK/nussknacker/blob/staging/interpreter/src/main/scala/pl/touk/nussknacker/engine/migration/ProcessMigration.scala)
- [DictServicesFactory](https://github.com/TouK/nussknacker/blob/staging/api/src/main/scala/pl/touk/nussknacker/engine/api/dict/DictServicesFactory.scala)

### Designer customization

- [ProcessChangeListenerFactory](https://github.com/TouK/nussknacker/blob/staging/ui/listener-api/src/main/scala/pl/touk/nussknacker/ui/listener/ProcessChangeListenerFactory.scala)
- Security
  - [AuthenticationProvider](https://github.com/TouK/nussknacker/blob/staging/security/src/main/scala/pl/touk/nussknacker/ui/security/api/AuthenticationProvider.scala)
  - [OAuth2ServiceFactory](https://github.com/TouK/nussknacker/blob/staging/security/src/main/scala/pl/touk/nussknacker/ui/security/oauth2/OAuth2ServiceFactory.scala)
- [CountsReporterCreator](https://github.com/TouK/nussknacker/blob/staging/ui/processReports/src/main/scala/pl/touk/nussknacker/processCounts/CountsReporter.scala)

                         
## Contents of customization packages.

Customization code should be placed in jar files on correct classpath:
- Customizations of model (in particular `ComponentProviders`) should be configured in [Model config](../installation_configuration_guide/Configuration) in 
`modelConfig.classpath`. 
- Code of Designer customizations should go to the main designer classpath (e.g. put the jars in the `lib` folder)

The jar file should be fatjar containing all libraries necessary for running your customization, 
except for dependencies provided by execution engine. In particular, for custom component implementation, 
following dependencies **should** be marked as `provided` and not be part of customization jar:
- All Nussknacker modules with names ending in `-api`, e.g. `nussknacker-api`, `nussknacker-flink-api`, `nussknacker-lite-api`
- `nussknacker-util`, `nussknacker-flink-util`
- Basic Flink dependencies: `flink-streaming-scala`, `flink-runtime`, `flink-statebackend-rocksdb` etc. for Flink components
- `nussknacker-kafka-util` for Lite components

**Please remember that `provided` dependency are not transitive, i.e. if you depend on e.g. `nussknacker-flink-kafka-util`
you still have to declare dependency on `nussknacker-flink-util` explicitly 
(see [Maven documentation](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#dependency-scope) for further info).** 

Your code should ideally depend only on `nussknacker-xxx-api` packages and not on implementation modules, like 
`nussknacker-interpreter`, `nussknacker-flink-executor`, `nussknacker-lite-runtime`. They should only be 
needed in `test` scope. If you find you need to depend on those modules, please bear in mind that:
- they should be `provided` and not included in fatjar.
- they contain implementation details and their API should not be considered stable. 
                                              
The `nussknacker-xxx-util` modules should be used with caution, they provide utility classes that are 
often needed when implementing own components, but their API is subject to change. 