# Overview

Please make sure you know common [Glossary](https://docs.nussknacker.io/documentation/about/GLOSSARY) and [SpEL](../scenarios_authoring/Spel) (especially the Data types section) before proceeding further. 

This part of the documentation describes various ways of customizing Nussknacker - from adding own Components to adding listeners for various Designer actions. 
The main way of adding customizations to Nussknacker is [ServiceLoader](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html) - 

## Types

Types of expressions are based on Java types. Nussknacker provides own abstraction of type, which can contain more information about given type than pure Java class - e.g. object type (like in [Typescript](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#object-types)) is represented in runtime as Java `Map`, but during compilation we know the structure of this map. 
We also handle union types (again, similar to [Typescript](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#union-types)) and we have `Unknown` type which is represented as Java `Object` in runtime, but behaves a bit like [Typescript any](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#any) (please note that `Unknown` should be avoided as default [Security settings](./Security) settings prohibit omitting typechecking with `Unknown`.
 
`TypingResult` is the main class (sealed trait) that represents type of expression in Nussknacker.
`Typed` object has many methods for constructing `TypingResult`


      
## Components and ComponentProviders

[Components]() are main method of customizing Nussknacker. Components are created by configured `ComponentProvider` instances. 
ComponentProviders are discovered with 

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
