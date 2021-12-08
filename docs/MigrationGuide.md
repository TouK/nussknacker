
# Migration guide

To see the biggest differences please consult the [changelog](Changelog.md).

## In version 1.2.0 (Not released yet)

### Configuration changes

* [#2483](https://github.com/TouK/nussknacker/pull/2483) `COUNTS_URL` environment variable is not `INFLUXDB_URL`, without `query` path part.
* [#2493](https://github.com/TouK/nussknacker/pull/2493) kafka configuration should be moved to components provider configuration - look at `components.kafka` in dev-application.conf for example
* [#2624](https://github.com/TouK/nussknacker/pull/2624) Default name for `process` tag is now `scenario`. This affects metrics and count functionalities. 
  Please update you Flink/Telegraf setup accordingly (see [nussknacker-quickstart](https://github.com/TouK/nussknacker-quickstart/tree/main/telegraf) for details). 
  If you still want to use `process` tag (e.g. you have a lot of dashboards), please set `countsSettings.metricsConfig.scenarioTag` setting to `process`
  Also, dashboard links format changed, see [documentation](https://docs.nussknacker.io/documentation/docs/installation_configuration_guide/DesignerConfiguration#metric-dashboard) for the details.

### Other changes

* [#2600](https://github.com/TouK/nussknacker/pull/2600) `ScenarioInterpreter`, `ScenarioInterpreterWithLifecycle` now takes additional
  generic parameter: `Input`. `ScenarioInterpreter.invoke` takes `ScenarioInputBatch` which now contains list of `SourceId -> Input` instead of
  `SourceId -> Context`. Logic of `Context` preparation should be done in `LiteSource` instead of before `ScenarioInterpreter.invoke`. invocation
  It means that `LiteSource` also takes this parameter and have a new method `createTransformation`.
* [#2554](https://github.com/TouK/nussknacker/pull/2554) Maven artifact `nussknacker-kafka-flink-util` become `nussknacker-flink-kafka-util` and `nussknacker-avro-flink-util` become `nussknacker-flink-avro-util`.
  General naming convention is `nussknacker-$runtimeType-$moduleName`. Components inside distribution changed layout to `components(/$runtimeType)/componentName.jar` e.g. `components/flink/kafka.jar` or `components/openapi.jar`
  `KafkaSource` become `FlinkKafkaSource`, `ConsumerRecordBasedKafkaSource` become `FlinkConsumerRecordBasedKafkaSource`, `KafkaSink` become `FlinkKafkaSink`, `KafkaAvroSink` become `FlinkKafkaAvroSink`
* [#2535](https://github.com/TouK/nussknacker/pull/2535) Rename `standalone` to `request-response`: 
  * Renamed modules and artifacts
  * Move `request-response` modules to `base` dir. 
  * `standalone` in package names changed to `requestresponse`
  * `Standalone` in class/variable names changed to `RequestResponse`
  * `DeploymentManager/Service` uses dedicated format of status DTO, instead of the ones from `deployment-manager-api`
  * Removed old, deprecated `jarPath` settings, in favour of `classPath` used in other places
* [#2582](https://github.com/TouK/nussknacker/pull/2582) `KafkaUtils.toProducerProperties` setup only basic properties now (`bootstrap.servers` and serializers) - before the change it
  was setting options which are not always good choice (for transactional producers wasn't)

## In version 1.1.0
:::info
Summary:
- A lot of internal refactoring was made to separate code/API specific for Flink.
  If your deployment has custom components pay special attention to:
  - `Lifecycle` management
  - Kafka components
  - Differences in artifacts and packages
- Some of the core dependencies: cats, cats-effect and circe were upgraded. It affects mainly code, but it may 
  also have inpact on state compatibility and performance. 
- Default Flink version was bumped do 1.14 - see https://github.com/TouK/nussknacker-flink-compatibility on how to run Nu on older Flink versions.
- Execution of SpEL expressions is now checked more strictly, due to security considerations. These checks can be overridden with custom `ExpressionConfig`. 
:::
:::info
- Apart from that:
  - minor configuration naming changes
  - removal of a few of minor, not documented features (e.g. SQL Variable)
:::
    
* [#2176](https://github.com/TouK/nussknacker/pull/2176) `EnrichDeploymentWithJarDataFactory` was replaced with `ProcessConfigEnricher`.
* [#2278](https://github.com/TouK/nussknacker/pull/1422) SQL Variable is removed         
* [#2280](https://github.com/TouK/nussknacker/pull/2280) Added optional `defaultValue` field to `Parameter`. In `GenericNodeTransformation` can be set to `None` - values will be determined automatically.
* [#2289](https://github.com/TouK/nussknacker/pull/2289) Savepoint path in `/api/adminProcessManagement/deploy` endpoint is passed as a `savepointPath` parameter instead of path segment.
* [#2293](https://github.com/TouK/nussknacker/pull/2293) Enhancement: change `nodeCategoryMapping` configuration to `componentsGroupMapping`
* [#2301](https://github.com/TouK/nussknacker/pull/2301) [#2620](https://github.com/TouK/nussknacker/pull/2620) `GenericNodeTransformation.initialParameters` was removed - 
  now `GenericNodeTransformation.contextTransformation` is used instead. To make Admin tab -> Invoke service form working, use `WithLegacyStaticParameters` trait
* [#2409](https://github.com/TouK/nussknacker/pull/2409) `JsonValidator` is now not determined by default based on `JsonParameterEditor` but must be explicitly defined by `@JsonValidator` annotation 
* [#2304](https://github.com/TouK/nussknacker/pull/2304) Upgrade to Flink 1.14. Pay attention to Flink dependencies - in some (e.g. runtime) there is no longer scala version.
* [#2295](https://github.com/TouK/nussknacker/pull/2295) `FlinkLazyParameterFunctionHelper` allows (and sometimes requires) correct exception handling
* [#2307](https://github.com/TouK/nussknacker/pull/2307) Changed `nussknacker-kafka` module name to `nussknacker-kafka-util`  
* [#2310](https://github.com/TouK/nussknacker/pull/2310) Changed `nussknacker-process` module name to `nussknacker-flink-engine`  
* [#2300](https://github.com/TouK/nussknacker/pull/2300) [#2343](https://github.com/TouK/nussknacker/pull/2343) Enhancement: refactor and improvements at components group:
  * Provided `ComponentGroupName` as VO
  * `SingleNodeConfig` was renamed to `SingleComponentConfig` and moved from `pl.touk.nussknacker.engine.api.process` package to `pl.touk.nussknacker.engine.api.component`
  * Configuration `category` in node configuration was replaced by `componentGroup`
  * Configuration `nodes` in model configuration was replaced by `componentsUiConfig`
  * Additional refactor: `ProcessToolbarService` moved from `pl.touk.nussknacker.ui.service` package to `pl.touk.nussknacker.ui.process`
  * Additional refactor: `ProcessToolbarService` moved from `pl.touk.nussknacker.ui.service` package to `pl.touk.nussknacker.ui.process`
  * `DefinitionPreparer` was renamed to `ComponentDefinitionPreparer`
  * `NodesConfigCombiner` was removed
  * REST API /api/processDefinitionData/* response JSON was changed:
    * `nodesToAdd` was renamed to `componentGroups`
    * `posibleNode` was renamed to `components`
    * `nodesConfig` was renamed to `componentsConfig`
    *  config `icon` property from `componentsConfig` right now should be relative to `http.publicPath` e.g. `/assets/components/Filter.svg` (before was just `Filter.svg`) or url (with `http` / `https`)
* [#2346](https://github.com/TouK/nussknacker/pull/2346) Remove `endResult` from `Sink` in graph. 
  * `Sink` no longer defines `testOutput` method - they should be handled by respective implementations
  * Change in definition of `StandaloneSink` previously `StandaloneSinkWithParameters`, as output always has to be computed with sink parameters now
  * Changes in definition of `FlinkSink`, to better handle capturing test data
  * Removal of `.sink` method in `GraphBuilder` - use `.emptySink` if suitable
* [#2331](https://github.com/TouK/nussknacker/pull/2331) 
  * `KafkaAvroBaseTransformer` companion object renamed to `KafkaAvroBaseComponentTransformer` 
  * `KryoGenericRecordSchemaIdSerializationSupport` renamed to `GenericRecordSchemaIdSerializationSupport` 
* [#2305](https://github.com/TouK/nussknacker/pull/2305) Enhancement: change `processingTypeToDashboard` configuration to `scenarioTypeToDashboard`
* [#2296](https://github.com/TouK/nussknacker/pull/2296) Scenarios & Fragments have separate TypeSpecificData implementations. Also, we remove `isSubprocess` field from process json, and respectively from MetaData constructor. See corresponding db migration `V1_031__FragmentSpecificData.scala`
* [#2368](https://github.com/TouK/nussknacker/pull/2368) `WithCategories` now takes categories as an `Option[List[String]]` instead of `List[String]`. 
You should wrap given list of categories with `Some(...)`. `None` mean that component will be available in all categories.
* [#2360](https://github.com/TouK/nussknacker/pull/2360) `union`, `union-memo` and `dead-end` components were extracted from `model/genericModel.jar` to `components/baseComponents.jar`
If you have your own `application.conf` which changes `scenarioTypes`, you should add `"components/baseComponents.jar"` entry into `classPath` array
* [#2337](https://github.com/TouK/nussknacker/pull/2337) Extract base engine from standalone
  * Common functionality of base engine (i.e. microservice based, without Flink) is extracted to `base-api` and `base-runtime`
  * new API for custom components (`pl.touk.nussknacker.engine.baseengine.api.customComponentTypes`)
  * `StandaloneProcessInterpreter` becomes `StandaloneScenarioEngine`
  * Replace `Either[NonEmptyList[Error], _]` with `ValidatedNel[Error, _]` as return type
  * `StandaloneContext` becomes `EngineRuntimeContext`
* [#2349](https://github.com/TouK/nussknacker/pull/2349) `queryable-state` module was removed, `FlinkQueryableClient` was moved to `nussknacker-flink-manager`. `PrettyValidationErrors`, `CustomActionRequest` and `CustomActionResponse` moved from `nussknacker-ui` to `nussknacker-restmodel`.
* [#2361](https://github.com/TouK/nussknacker/pull/2361) Removed `security` dependency from `listener-api`. `LoggedUser` replaced with dedicated class in `listener-api`.
* [#2385](https://github.com/TouK/nussknacker/pull/2385) Deprecated `CustomStreamTransformer.clearsContext` was removed. Use
```
@MethodToInvoke
def execute(...) = 
  ContextTransformation
    .definedBy(ctx => Valid(ctx.clearVariables ...))
    .implementedBy(...)
}
```
instead.
* [#2348](https://github.com/TouK/nussknacker/pull/2348) [#2459](https://github.com/TouK/nussknacker/pull/2459) [#2486](https://github.com/TouK/nussknacker/pull/2486) 
  [#2490](https://github.com/TouK/nussknacker/pull/2490) [#2496](https://github.com/TouK/nussknacker/pull/2496) [#2536](https://github.com/TouK/nussknacker/pull/2536)
  Introduce `KafkaDeserializationSchema` and `KafkaSerializationSchema` traits to decouple from flink dependency. move `KeyedValue` to `nussknacker-util`, move `SchemaRegistryProvider` to `utils/avro-util`
  To move between nussknacker's/flink's Kafka(De)serializationSchema use `wrapToFlink(De)serializatioinSchema` from `FlinkSerializationSchemaConversions`.
  `SchemaRegistryProvider` and `ConfluentSchemaRegistryProvider` is now in `nussknacker-avro-util` module. `FlinkSourceFactory` is gone - use `SourceFactory` instead.
  `KafkaSourceFactory`, `KafkaAvroSourceFactory`, `KafkaSinkFactory`, `KafkaAvroSinkFactory`, and `ContextIdGenerator` not depends on flink.
  Extracted `KafkaSourceImplFactory`, `KafkaSinkImplFactory` and `KafkaAvroSinkImplFactory` which deliver implementation of component (after all validations and parameters evaluation).
  Use respectively: `FlinkKafkaSourceImplFactory`, `FlinkKafkaSinkImplFactory` and `FlinkKafkaAvroSinkImplFactory` to deliver flink implementations.
  Moved non-flink specific serializers, deserializers, `BestEffortAvroEncoder`, `ContextIdGenerator`s and `RecordFormatter`s to kafka-util/avro-util
  `KafkaDelayedSourceFactory` is now `DelayedKafkaSourceFactory`. `FixedRecordFormatterFactoryWrapper` moved to `RecordFormatterFactory`
* [#2477](https://github.com/TouK/nussknacker/pull/2477) `FlinkContextInitializer` and `FlinkGenericContextInitializer` merged to `ContextInitializer`, 
 `BasicFlinkContextInitializer` and `BasicFlinkGenericContextInitializer` merged to `BasicContextInitializer`. All of them moved to `pl.touk.nussknacker.engine.api.process` package.
 `ContextInitializer.validationContext` returns `ValidatedNel` - before this change errors during context initialization weren't accumulated.
 `ContextInitializingFunction` now is a scala's function instead of Flink's MapFunction. You should wrap it with `RichLifecycleMapFunction` to make sure that it will be opened correctly by Flink. 
 `InputMeta` was moved to `kafka-util` module. 
* [#2389](https://github.com/TouK/nussknacker/pull/2389) [#2391](https://github.com/TouK/nussknacker/pull/2391) `deployment-manager-api` module was extracted and `DeploymentManagerProvider`,
`ProcessingTypeData` and `QueryableClient` was moved from `interpreter` into it. `DeploymentManager`, `CustomAction` and `ProcessState` was moved from `api` to `deployment-manager-api`. `ProcessingType` was moved to `rest-model` package.
* [#2393](https://github.com/TouK/nussknacker/pull/2393) Added `ActorSystem`, `ExecutionContext` and `SttpBackend` into `DeploymentManagerProvider.createDeploymentManager`. During clean ups
also was removed `nussknacker-http-utils` dependency to `async-http-client-backend-future` and added `SttpBackend` to `CountsReporterCreator.createReporter` arguments.
* [#2397](https://github.com/TouK/nussknacker/pull/2397) Common `EngineRuntimeContext` lifecycle and `MetricsProvider`. This
may cause __runtime__ consequences - make sure your custom services/listeners invoke `open`/`close` correctly - especially in complex inheritance scenarios.
  * `Lifecycle` has now `EngineRuntimeContext` as parameter, `JobData` is embedded in it.
  * `TimeMeasuringService` replaces `GenericTimeMeasuringService`, Flink/Standalone flavours of `TimeMeasuringService` are removed
  * `EngineRuntimeContext` and `MetricsProvider` moved to base API, `RuntimeContextLifecycle` moved to base API as `Lifecycle`
  * `GenericInstantRateMeter` is now `InstantRateMeter`
  * Flink `RuntimeContextLifecycle` should be replaced in most cases by `Lifecycle`
  * In Flink engine `MetricsProvider` (obtained with `EngineRuntimeContext`) should be used in most places instead of `MetricUtils`
* [#2486](https://github.com/TouK/nussknacker/pull/2486) `Context.withInitialId` is deprecated now - use `EngineRuntimeContext.contextIdGenerator` instead.
  `EngineRuntimeContext` can be accessible via `FlinkCustomNodeContext.convertToEngineRuntimeContext`
* [#2377](https://github.com/TouK/nussknacker/pull/2377) [#2534](https://github.com/TouK/nussknacker/pull/2534) Removed `clazz` from `SourceFactory`. Remove generic parameter from `Source` and `SourceFactory`. 
  Return type of source should be returned either by:
  - `returnType` field of `@MethodToInvoke`
  - `ContextTransformation` API
  - `GenericNodeTransformer` API
  - `SourceFactory.noParam` 
* [#2453](https://github.com/TouK/nussknacker/pull/2453) Custom actions for `PeriodicDeploymentManager` now can be defined and implemented outside this class, in `PeriodicCustomActionsProvider` created by `PeriodicCustomActionsProviderFactory`.
  If you do not need them, just pass `PeriodicCustomActionsProviderFactory.noOp` to object's `PeriodicDeploymentManager` factory method.
* [#2501](https://github.com/TouK/nussknacker/pull/2501) `nussknacker-baseengine-components` module renamed to `nussknacker-lite-base-components`
* [#2221](https://github.com/TouK/nussknacker/pull/2221) ReflectUtils `fixedClassSimpleNameWithoutParentModule` renamed to `simpleNameWithoutSuffix`
* [#2495](https://github.com/TouK/nussknacker/pull/2495) TypeSpecificDataInitializer trait change to TypeSpecificDataInitializ
* [2245](https://github.com/TouK/nussknacker/pull/2245) `FailedEvent` has been specified in `FailedOnDeployEvent` and `FailedOnRunEvent`
## In version 1.0.0

* [#1439](https://github.com/TouK/nussknacker/pull/1439) [#2090](https://github.com/TouK/nussknacker/pull/2090) Upgrade do Flink 1.13.
  * `setTimeCharacteristic` is deprecated, and should be handled automatically by Flink. 
  * `UserClassLoader` was removed, use appropriate Flink objects or context ClassLoader. 
  * RocksDB configuration is turned on by `rocksdb.enable` instead of `rocksdb.checkpointDataUri` which is not used now. 
* [#2133](https://github.com/TouK/nussknacker/pull/2133) SQL Variable is hidden in generic model, please look at comment in `defaultModelConfig.conf`
* [#2152](https://github.com/TouK/nussknacker/pull/2152) `schedulePropertyExtractor` parameter of `PeriodicDeploymentManagerProvider`
  was changed to a factory, replace with a lambda creating the original property extractor.
* [#2159](https://github.com/TouK/nussknacker/pull/2159) `useTypingResultTypeInformation` option is now enabled by default
* [#2108](https://github.com/TouK/nussknacker/pull/2108) Changes in `ClassExtractionSettings`:
  - Refactor of classes defining extraction rules,
  - `TypedClass` has private `apply` method, please use `Typed.typedClass`
  - Fewer classes/methods are accessible in SpEL, in particular Scala collections, internal time API, methods returning or having parameters from excluded classes
* Changes in `OAuth2` security components:
  - refactoring of `OpenIdConnectService`, now it's named `GenericOidcService` (it's best to use `OidcService`, which can handle most of the configuration automatically)
* New security settings, in particular new flags in `ExpressionConfig`:
  - `strictMethodsChecking`
  - `staticMethodInvocationsChecking`
  - `methodExecutionForUnknownAllowed`
  - `dynamicPropertyAccessAllowed`
  - `spelExpressionExcludeList`
* [#2101](https://github.com/TouK/nussknacker/pull/2101) Global permissions can be arbitrary string, for admin user it's not necessary to return global permissions
* [#2182](https://github.com/TouK/nussknacker/pull/2182) To avoid classloader leaks during SQL `DriverManager` registration, HSQLDB (used e.g. for SQL Variable) is no longer included in model jars, it should be added in Flink `lib` dir 

## In version 0.4.0

* [#1479](https://github.com/TouK/nussknacker/pull/1479) `ProcessId` and `VersionId` moved to API included in `ProcessVersion`, remove spurious `ProcessId` and `ProcessVersionId` in restmodel.
* [#1422](https://github.com/TouK/nussknacker/pull/1422) Removed `ServiceReturningType` and `WithExplicitMethod`, use `EagerServiceWithStaticParameters`, `EnricherContextTransformation` or `SingleInputGenericNodeTransformation`
* [#1845](https://github.com/TouK/nussknacker/pull/1845)
  `AuthenticatorData` has been renamed to `AuthenticationResources` and changed into a trait, `apply` construction has
  been preserved. `AuthenticatorFactory` and its `createAuthenticator`  method has been renamed to
  `AuthenticationProvider` and `createAuthenticationResources`. It is recommended to rename the main class of any custom
  authentication module to `<Something>AuthenticationProvider` accordingly.
* [#1542](https://github.com/TouK/nussknacker/pull/1542)
  `KafkaConfig` now has new parameter `topicsExistenceValidationConfig`. When `topicsExistenceValidationConfig.enabled = true`
  Kafka sources/sinks do not validate if provided topic does not exist and cluster is configured with `auto.create.topics.enable=false`
* [#1416](https://github.com/TouK/nussknacker/pull/1416)
 `OAuth2Service` has changed. You can still use your old implementation by importing `OAuth2OldService` with an alias.
 `OAuth2ServiceFactory.create` method now accepts implicit parameters for an `ExecutionContext` and `sttp.HttpBackend`.
  You can ignore them to maintain previous behaviour, but it is always better to use them instead of locally defined ones.   
* [#1346](https://github.com/TouK/nussknacker/pull/1346) `AggregatorFunction` now takes type of stored state that can be 
  `immutable.SortedMap` (previous behaviour) or `java.util.Map` (using Flink's serialization) and `validatedStoredType` parameter for 
  providing better `TypeInformation` for aggregated values
* [#1343](https://github.com/TouK/nussknacker/pull/1343) `FirstAggregator` changed serialized state, it is not compatible, 
  ```Aggregator``` trait has new method ```computeStoredType``` 
* [#1352](https://github.com/TouK/nussknacker/pull/1352) and [#1568](https://github.com/TouK/nussknacker/pull/1568) AvroStringSettings class has been introduced, which allows control
  whether Avro type ```string``` is represented by ```java.lang.String``` (also in runtime) or ```java.lang.CharSequence```
  (implemented in runtime by ```org.apache.avro.util.Utf8```). This setting is available through environment variable 
  ```AVRO_USE_STRING_FOR_STRING_TYPE``` - default is `true`. Please mind that this setting is global - it applies to all processes running on Flink
  and also requires restarting TaskManager when changing the value.
* [#1361](https://github.com/TouK/nussknacker/pull/1361) Lazy variables are removed, you should use standard enrichers for those cases.
  Their handling has been source of many problems and they made it harder to reason about the exeuction of process.   
* [#1373](https://github.com/TouK/nussknacker/pull/1373) Creating `ClassLoaderModelData` directly is not allowed, use
  `ModelData.apply` with plain config, wrapping with ModelConfigToLoad by yourself is not needed.
* [#1406](https://github.com/TouK/nussknacker/pull/1406) `ServiceReturningType` is deprecated in favour of `EagerService` 
* [#1445](https://gihub.com/TouK/nussknacker/pull/1445) `RecordFormatter` now handles `TestDataSplit` for Kafka sources. It is required in `KafkaSource` creation, instead of `TestDataSplit` 
* [#1433](https://github.com/TouK/nussknacker/pull/1433) Pass DeploymentData to process via JobData, additional parameters to deployment methods are needed. Separate `ExternalDeploymentId` from `DeploymentId` (generated by NK)
* [#1466](https://github.com/TouK/nussknacker/pull/1466) `ProcessManager.deploy` can return `ExternalDeploymentId`       
* [#1464](https://github.com/TouK/nussknacker/pull/1464) 
  - Slight change of API of `StringKeyedValueMapper`
  - Change of semantics of some parameters of `AggregatorFunction`, `AggregatorFunctionMixin` (storedAggregateType becomes aggregateElementType)  
* [#1405](https://github.com/TouK/nussknacker/pull/1405) 'KafkaAvroSink' requires more generic 'AvroSinkValue' as value parameter
* [#1510](https://github.com/TouK/nussknacker/pull/1510)
  - Change of `FlinkSource` API: sourceStream produces stream of initialized `Context` (`DataStream[Context]`)
    This initialization step was previously performed within `FlinkProcessRegistrar.registerSourcePart`. Now it happens explicitly within the flink source.
  - `FlinkIntermediateRawSource` is used as an extension to flink sources, it prepares source with typical stream transformations (add source function, set uid, assign timestamp, initialize `Context`)
  - `FlinkContextInitializer` is used to initialize `Context`. It provides map function that transforms raw event (produced by flink source function) into `Context` variable.
    Default implementation of `FlinkContextInitializer`, see `BasicFlinkContextInitializer`, sets raw event value to singe "input" variable.
  - For sources based on `GenericNodeTransformation` it allows to initialize `Context` with more than one variable.
    Default implementation of initializer, see `BasicFlinkGenericContextInitializer`, provides default definition of variables as a `ValidationContext` with single "input" variable.
    The implementation requires to provide separately the definition of "input" variable type (`TypingResult`).
    See `GenericSourceWithCustomVariablesSample`.
  - To enable "test source" functionality, a source needs to be extended with `SourceTestSupport`.
  - For flink sources that use `TestDataParserProvider` switch to `FlinkSourceTestSupport` (which is used to provide "test source" functionality for flink sources).
  - Old `TestDataParserProvider` is renamed to `SourceTestSupport`
  - To enable test data generator for "test source" , a source needs to be extended with both `SourceTestSupport` and `TestDataGenerator`.
    What was related to "test source" functionality and was obsolete in `FlinkSource` now is excluded to `FlinkSourceTestSupport`.
  - `FlinkCustomNodeContext` has access to `TypeInformationDetection`, it allows to get TypeInformation for the node stream mapping from ValidationContext.
  - For kafka sources `RecordFormatter` parses raw test data to `ConsumerRecord` which fits into deserializer (instead of `ProducerRecord` that required another transformation).
  - Definitions of names of common `Context` variables are moved to `VariableConstants` (instead of `Interpreter`).
* [#1497](https://github.com/TouK/nussknacker/pull/1497) Changes in `PeriodicProcessManager`, change `PeriodicProperty` to `ScheduleProperty`
* [#1499](https://github.com/TouK/nussknacker/pull/1499)
  - trait `KafkaAvroDeserializationSchemaFactory` uses both key and value ClassTags and schemas (instead of value-only), check the order of parameters.
  - ClassTag is provided in params in avro key-value deserialization schema factory: `KafkaAvroKeyValueDeserializationSchemaFactory`
  - `BaseKafkaAvroSourceFactory` is able to read both key and value schema determiner to build proper DeserializationSchema (support for keys is not fully introduced in this change)
* [#1514](https://github.com/TouK/nussknacker/pull/1514) `ExecutionConfigPreparer` has different method parameter - `JobData`, which has more info than previous parameters
* [#1532](https://github.com/TouK/nussknacker/pull/1532) `TypedObjectTypingResult#fields` uses now `scala.collection.immutable.ListMap` to keep fields order
* [#1546](https://github.com/TouK/nussknacker/pull/1546) `StandaloneCustomTransformer` now takes a list of `Context` objects, to process them in one go
* [#1557](https://github.com/TouK/nussknacker/pull/1556) Some classes from standalone engine were moved to standalone api to remove engine to (model) utils dependency:
  `StandaloneContext`, `StandaloneContextLifecycle`, `MetricsProvider`
* [#1558](https://github.com/TouK/nussknacker/pull/1558) `FlinkProcessRegistrar` takes configuration directly from `FlinkProcessCompiler` (this can affect some tests setup)
* [#1631](https://github.com/TouK/nussknacker/pull/1631) Introduction of `nussknacker.config.locations` property, drop use of standard `config.file` property. Model configuration no longer has direct access to root UI config.
* [#1512](https://github.com/TouK/nussknacker/pull/1512)
  - Replaced `KafkaSourceFactory` with source based on `GenericNodeTransformation`, which gives access to setup of `ValidationContext` and `Context` initialization.
    To migrate `KafkaSourceFactory`:
    - provide deserializer factory (source factory requires deserialization to `ConsumerRecord`):
      - use `ConsumerRecordDeserializationSchemaFactory` with current `DeserializationSchema` as a value deserializer, add key deserializer (e.g. org.apache.kafka.common.serialization.StringDeserializer)
      - or use `FixedValueDeserializationSchemaFactory` with simple key-as-string deserializer
    - provide RecordFormatterFactory
      - use `ConsumerRecordToJsonFormatterFactory` for whole key-value-and-metadata serialization
      - or, for value-only-and-without-metadata scenario, you can use current `RecordFormater` wrapped in `FixedRecordFormatterFactoryWrapper`
    - provide timestampAssigner that is able to extract time from `ConsumerRecord[K, V]`
  - Removed `BaseKafkaSourceFactory` with multiple topics support:
    use `KafkaSourceFactory` instead, see "source with two input topics" test case
  - Removed `SingleTopicKafkaSourceFactory`:
    use `KafkaSourceFactory` with custom `prepareInitialParameters`, `contextTransformation` and `extractTopics` to alter parameter list and provide constant topic value.
  - `TypingResultAwareTypeInformationCustomisation` is moved to package pl.touk.nussknacker.engine.flink.api.typeinformation

  Example of source with value-only deserialization and custom timestampAssigner:
  ```
  // provide new deserializer factory with old schema definition for event's value
  val oldSchema = new EspDeserializationSchema[SampleValue](bytes => io.circe.parser.decode[SampleValue](new String(bytes)).right.get)
  val schemaFactory: KafkaDeserializationSchemaFactory[ConsumerRecord[String, SampleValue]] = new FixedValueDeserializationSchemaFactory(oldSchema)

  // ... provide timestampAssigner that extracts timestamp from SampleValue.customTimestampField
  // ... or use event's metadata: record.timestamp()
  def timestampExtractor(record: ConsumerRecord[String, SampleValue]): Long = record.value().customTimestampField
  val watermarkHandler = StandardTimestampWatermarkHandler.boundedOutOfOrderness[ConsumerRecord[String, SampleValue]](timestampExtractor, java.time.Duration.ofMinutes(10L))
  val timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[String, SampleValue]]] = Some(watermarkHandler)

  // ... provide RecordFormatterFactory that allows to generate and parse test data with key, headers and other metadata
  val formatterFactory: RecordFormatterFactory = new ConsumerRecordToJsonFormatterFactory[String, SampleValue]

  // ... and finally
  val sourceFactory = new KafkaSourceFactory[String, SampleValue](schemaFactory, timestampAssigner, formatterFactory, dummyProcessObjectDependencies)
  ```
* [#1651](https://github.com/TouK/nussknacker/pull/1651) `KafkaAvroSourceFactory` provides additional #inputMeta variable with event's metadata.
  - That source now has key and value type parameters. That parameters are relevant for sources handling `SpecificRecord`s. For `GenericRecords` use explicitly `KafkaAvroSourceFactory[Any, Any]`.
  - `SpecificRecordKafkaAvroSourceFactory` extends whole `KafkaAvroSourceFactory` with context validation and initialization
  - New flag in `KafkaConfig`: `useStringForKey` determines if event's key should be intepreted as ordinary String (which is default scenario). It is used in deserialization and for generating/parsing test data.
  - `SchemaRegistryProvider` now provides factories to produce SchemaRegistryClient and RecordFormatter.
  - For `ConfluentSchemaRegistryProvider` KafkaConfig and ProcessObjectDependencies (that contains KafkaConfig data) are no longer required. That configuration is required by factories in the moment the creation of requested objects
    that happens in `KafkaAvroSourceFactory` (and that makes that all objects within `KafkaAvroSourceFactory` see the same kafka configuration).
  - Removed:
    - `BaseKafkaAvroSourceFactory`, the class is incorporated into `KafkaAvroSourceFactory` to provide elastic approach to create KafkaSource
    - `with ReturningType` for generic types (this is defined by ValidationContext, see also `KafkaContextInitializer` that allows to return more than one variable)
    - `KafkaAvroValueDeserializationSchemaFactory` (source requires deserialization to `ConsumerRecord[K, V]`, there are only deserializers based on `KafkaAvroKeyValueDeserializationSchemaFactory`)
    - `ConfluentKafkaAvroDeserializationSchemaFactory`, use `ConfluentKeyValueKafkaAvroDeserializationFactory`
    - `TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory`, this approach is deprecated due to #inputMeta variable that contains key data

  To migrate `KafkaAvroSourceFactory`:
    - Provide `KafkaConfig` with correct `useStringForKey` flag value. By default we want to EvictableStatehandle keys as ordinary String and all topics related to such config require only value schema definitions (key schemas are ignored).
      For specific scenario, when complex key with its own schema is provided, this flag is false and all topics related to this config require both key and value schema definitions.
      Example of default KafkaConfig override:
      `override protected def prepareKafkaConfig: KafkaConfig = super.prepareKafkaConfig.copy(useStringForKey = false)`
    - provide your own `SchemaRegistryProvider` (or use `ConfluentSchemaRegistryProvider`)
    - custom RecordFormatter can be wrapped in `FixedRecordFormatterFactoryWrapper` (or keep `ConfluentAvroToJsonFormatterFactory`)
    - provide timestampAssigner that is able to extract time from `ConsumerRecord[K, V]` (see example above)
* [#1741](https://github.com/TouK/nussknacker/pull/1741) Minor changes in `KafkaUtils`, `NonTransientException` uses `Instant` instead of `LocalDateTime`
* [#1806](https://github.com/TouK/nussknacker/pull/1806) Remove old, deprecated API:
  - `EvictableState`, `RichEvictableState` - use `EvictableStateFunction`
  - `checkpointInterval` - use `checkpointConfig.checkpointInterval`
  - old versions of `sampleTransformers` - use newer ones
  - `MiniClusterExecutionEnvironment.runningJobs()` - use `flinkMiniClusterHolder.runningJobs()`  
* [#1807](https://github.com/TouK/nussknacker/pull/1807) Removed `jdbcServer`, please use Postgres for production-ready setups
* [#1799](https://github.com/TouK/nussknacker/pull/1799)
  - RecordFormatterFactory instead of one, uses two type parameters: K, V
  - ConfluentAvroToJsonFormatter is produced by ConfluentAvroToJsonFormatterFactory
  - ConfluentAvroToJsonFormatter produces test data in valid json format, does not use Separator
  - ConfluentAvroMessageFormatter has asJson method instead of writeTo
  - ConfluentAvroMessageReader has readJson method instead of readMessage
  Example test data object:
  ```
  {"keySchemaId":null,"valueSchemaId":1,"consumerRecord":{"key":null,"value":{"first":"Jan","last":"Kowalski"},"topic":"testAvroRecordTopic1","partition":0,"offset":0,"timestamp":1624279687756,"timestampType":"CreateTime","headers":{},"leaderEpoch":0}}
  ```
* [#1663](https://github.com/TouK/nussknacker/pull/1663) Default `FlinkExceptionHandler` implementations are deprecated, use `ConfigurableExceptionHandler` instead.
* [#1731](https://github.com/TouK/nussknacker/pull/1731) RockDB config's flag `incrementalCheckpoints` is turned on by default.
* [#1825](https://github.com/TouK/nussknacker/pull/1825) Default dashboard renamed from `flink-esp` to `nussknacker-scenario`
* [#1836](https://github.com/TouK/nussknacker/pull/1836) Change default `kafka.consumerGroupNamingStrategy` to `processId-nodeId`.     
* [#1357](https://github.com/TouK/nussknacker/pull/1357) Run mode added to nodes. `ServiceInvoker` interface was extended with new, implicit `runMode` parameter. 
* [#1836](https://github.com/TouK/nussknacker/pull/1836) Change default `kafka.consumerGroupNamingStrategy` to `processId-nodeId`.
* [#1886](https://github.com/TouK/nussknacker/pull/1886) aggregate-sliding with emitWhenEventLeft = true, aggregate-tumbling and aggregate-session components now
  doesn't emit full context of variables that were before node (because of performance reasons and because that wasn't obvious which one context is emitted).
  If you want to emit some information other than aggregated value and key (availabled via new `#key` variable), you should use `#AGG.map` expression in `aggregateBy`.
* [#1910](https://github.com/TouK/nussknacker/pull/1910) `processTypes` renamed to `scenarioTypes`. You can still use old `processTypes` configuration. Old configuration will be removed in version `0.5.0`. 
* Various naming changes:
  * [#1917](https://github.com/TouK/nussknacker/pull/1917) configuration of `engineConfig` to `deploymentConfig`                           
  * [#1921](https://github.com/TouK/nussknacker/pull/1921) `ProcessManager` to `DeploymentManager`                           
  * [#1927](https://github.com/TouK/nussknacker/pull/1927) Rename `outer-join` to `single-side-join`
   
          
## In version 0.3.0

* [#1313](https://github.com/TouK/nussknacker/pull/1313) Kafka Avro API passes `KafkaConfig` during `TypeInformation` determining
* [#1305](https://github.com/TouK/nussknacker/pull/1305) Kafka Avro API passes `RuntimeSchemaData` instead of `Schema` in various places
* [#1304](https://github.com/TouK/nussknacker/pull/1304) `SerializerWithSpecifiedClass` was moved to `flink-api` module.
* [#1044](https://github.com/TouK/nussknacker/pull/1044) Upgrade to Flink 1.11. Current watermark/timestamp mechanisms are deprectated in Flink 1.11, 
 new API ```TimestampWatermarkHandler``` is introduced, with ```LegacyTimestampWatermarkHandler``` as wrapper for previous mechanisms.
* [#1244](https://github.com/TouK/nussknacker/pull/1244) `Parameter` has new parameter 'variablesToHide' with `Set` of variable names
that will be hidden before parameter's evaluation
* [#1159](https://github.com/TouK/nussknacker/pull/1159) [#1170](https://github.com/TouK/nussknacker/pull/1170) Changes in `GenericNodeTransformation` API:
  - Now `implementation` takes additional parameter with final state value determined during `contextTransformation`
  - `DefinedLazyParameter` and `DefinedEagerParameter` holds `expression: TypedExpression` instead of `returnType: TypingResult`
  - `DefinedLazyBranchParameter` and `DefinedEagerBranchParameter` holds `expressionByBranchId: Map[String, TypedExpression]` instead of `returnTypeByBranchId: Map[String, TypingResult]`
* [#1083](https://github.com/TouK/nussknacker/pull/1083)
  - Now `SimpleSlidingAggregateTransformerV2` and `SlidingAggregateTransformer` is deprecated in favour of `SlidingAggregateTransformerV2`
  - Now `SimpleTumblingAggregateTransformer` is deprecated in favour of `TumblingAggregateTransformer`
  - Now `SumAggregator`, `MaxAggregator` and `MinAggregator` doesn't change type of aggregated value (previously was changed to Double)
  - Now `SumAggregator`, `MaxAggregator` and `MinAggregator` return null instead of `0D`/`Double.MaxValue`/`Double.MinValue` for case when there was no element added before `getResult`

* [#1149](https://github.com/TouK/nussknacker/pull/1149) FlinkProcessRegistrar refactor (can affect test code) 
* [#1166](https://github.com/TouK/nussknacker/pull/1166) ```model.conf``` should be renamed to ```defaultModelConfig.conf```
* [#1218](https://github.com/TouK/nussknacker/pull/1218) FlinkProcessManager is no longer bundled in ui uber-jar. In docker/tgz distribution
* [#1255](https://github.com/TouK/nussknacker/pull/1255) Moved displaying `Metrics tab` to `customTabs`
* [#1257](https://github.com/TouK/nussknacker/pull/1257) Improvements: Flink test util package
    - Added methods: `cancelJob`, `submitJob`, `listJobs`, `runningJobs` to `FlinkMiniClusterHolder`
    - Deprecated: `runningJobs`, from `MiniClusterExecutionEnvironment`
    - Removed: `getClusterClient` from `FlinkMiniClusterHolder` interface, because of flink compatibility at Flink 1.9 
    - Renamed: `FlinkStreamingProcessRegistrar` to `FlinkProcessManager`
* [#1303](https://github.com/TouK/nussknacker/pull/1303) TypedObjectTypingResult has a new field: additionalInfo

## In version 0.2.0

* [#1104](https://github.com/TouK/nussknacker/pull/1104) Creation of `FlinkMiniCluster` is now extracted from `StoppableExecutionEnvironment`. You should create it using e.g.:
  ```
  val flinkMiniClusterHolder = FlinkMiniClusterHolder(FlinkTestConfiguration.configuration(parallelism))
  flinkMiniClusterHolder.start()
  ```
  and then create environment using:
  ```
  flinkMiniClusterHolder.createExecutionEnvironment()
  ```
  . At the end you should cleanup `flinkMiniClusterHolder` by:
  ```
  flinkMiniClusterHolder.stop()
  ```
  . `FlinkMiniClusterHolder` should be created once for test class - it is thread safe and resource expensive. `MiniClusterExecutionEnvironment` in the other hand should be created for each process.
  It is not thread safe because underlying `StreamExecutionEnvironment` is not. You can use `FlinkSpec` to achieve that.
* [#1077](https://github.com/TouK/nussknacker/pull/1077)
  - `pl.touk.nussknacker.engine.queryablestate.QueryableClient` was moved from `queryableState` module to `pl.touk.nussknacker.engine.api.queryablestate` package in `api` module
  - `pl.touk.nussknacker.engine.queryablestate.QueryableState` was moved to `pl.touk.nussknacker.engine.api.queryablestate`
  - CustomTransformers from `pl.touk.nussknacker.engine.flink.util.transformer` in `flinkUtil` module were moved to new `flinkModelUtil` module.
  - `pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator` was moved from `interpreter` module to `pl.touk.nussknacker.engine.util.process` package in `util` module
* [#1039](https://github.com/TouK/nussknacker/pull/1039) Generic parameter of `LazyParameter[T]` has upper bound AnyRef now to avoid problems with bad type extraction.
It caused changes `Any` to `AnyRef` in a few places - mainly `FlinkLazyParameterFunctionHelper` and `FlinkCustomStreamTransformation`
* [#1039](https://github.com/TouK/nussknacker/pull/1039) `FlinkStreamingProcessRegistrar.apply` has a new parameter of type `ExecutionConfigPreparer`.
In production code you should pass `ExecutionConfigPreparer.defaultChain()` there and in test code you should pass `ExecutionConfigPreparer.unOptimizedChain()`. See scaladocs for more info.
If you already have done some Flink's `ExecutionConfig` set up before you've registered process, you should consider create your own chain using `ExecutionConfigPreparer.chain()`.
* [#1039](https://github.com/TouK/nussknacker/pull/1039) `FlinkSourceFactory` doesn't take `TypeInformation` type class as a generic parameter now. Instead of this, it takes `ClassTag`.
`TypeInformation` is determined during source creation. `typeInformation[T]` method was moved from `BasicFlinkSource` to `FlinkSource` because still must be some place to determine it for tests purpose.
* [#965](https://github.com/TouK/nussknacker/pull/965) 'aggregate' node in generic model was renamed to 'aggregate-sliding'
* [#922](https://github.com/TouK/nussknacker/pull/922) HealthCheck API has new structure, naming and json responses:
  - old `/healthCheck` is moved to `/healthCheck/process/deployment`
  - old `/sanityCheck` is moved to `/healthCheck/process/validation`
  - top level `/healthCheck` indicates general "app-is-running" state

* [#879](https://github.com/TouK/nussknacker/pull/879) Metrics use variables by default, see [docs](https://github.com/TouK/nussknacker/blob/staging/docs/Metrics.md)
  to enable old mode, suitable for graphite protocol. To use old way of sending:
    - put `globalParameters.useLegacyMetrics = true` in each model configuration (to configure metrics sending in Flink)
    - put: 
    ```
    countsSettings {
      user: ...
      password: ...
      influxUrl: ...
      metricsConfig {
        nodeCountMetric: "nodeCount.count"
        sourceCountMetric: "source.count"
        nodeIdTag: "action"
        countField: "value"

      }
    }
    ```
* Introduction to KafkaAvro API: 
    [#871](https://github.com/TouK/nussknacker/pull/871), 
    [#881](https://github.com/TouK/nussknacker/pull/881), 
    [#903](https://github.com/TouK/nussknacker/pull/903), 
    [#981](https://github.com/TouK/nussknacker/pull/981), 
    [#989](https://github.com/TouK/nussknacker/pull/989), 
    [#998](https://github.com/TouK/nussknacker/pull/998), 
    [#1007](https://github.com/TouK/nussknacker/pull/1007), 
    [#1014](https://github.com/TouK/nussknacker/pull/1014),
    [#1034](https://github.com/TouK/nussknacker/pull/1034),
    [#1041](https://github.com/TouK/nussknacker/pull/1041)

API for `KafkaAvroSourceFactory` was changed:

`KafkaAvroSourceFactory` old way:
```
val clientFactory = new SchemaRegistryClientFactory
val source = new KafkaAvroSourceFactory(
  new AvroDeserializationSchemaFactory[GenericData.Record](clientFactory, useSpecificAvroReader = false),
  clientFactory,
  None,
  processObjectDependencies = processObjectDependencies
)
```

`KafkaAvroSourceFactory` new way :
```
val schemaRegistryProvider = ConfluentSchemaRegistryProvider(processObjectDependencies)
val source = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
```

Provided new API for Kafka Avro Sink:

```
val kafkaAvroSinkFactory = new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)
```

Additional changes:
- Bump up confluent package to 5.5.0
- (Refactor Kafka API) Moved `KafkaSourceFactory` to `pl.touk.nussknacker.engine.kafka.sink` package
- (Refactor Kafka API) Changed `BaseKafkaSourceFactory`, now it requires `deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T]`
- (Refactor Kafka API) Moved `KafkaSinkFactory` to `pl.touk.nussknacker.engine.kafka.source` package
- (Refactor Kafka API) Renamed `SerializationSchemaFactory` to `KafkaSerializationSchemaFactory`
- (Refactor Kafka API) Renamed `DeserializationSchemaFactory` to `KafkaDeserializationSchemaFactory`
- (Refactor Kafka API) Renamed `FixedDeserializationSchemaFactory` to `FixedKafkaDeserializationSchemaFactory`
- (Refactor Kafka API) Renamed `FixedSerializationSchemaFactory` to `FixedKafkaSerializationSchemaFactory`
- (Refactor Kafka API) Removed `KafkaSerializationSchemaFactoryBase`
- (Refactor Kafka API) Replaced `KafkaKeyValueSerializationSchemaFactoryBase` by `KafkaAvroKeyValueSerializationSchemaFactory` (it handles only avro case now)
- (Refactor Kafka API) Removed `KafkaDeserializationSchemaFactoryBase`
- (Refactor Kafka API) Replaced `KafkaKeyValueDeserializationSchemaFactoryBase` by `KafkaAvroKeyValueDeserializationSchemaFactory` (it handles only avro case now)
- (Refactor KafkaAvro API) Renamed `AvroDeserializationSchemaFactory` to `ConfluentKafkaAvroDeserializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Renamed `AvroKeyValueDeserializationSchemaFactory` to `ConfluentKafkaAvroDeserializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Renamed `AvroSerializationSchemaFactory` to `ConfluentAvroSerializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Renamed `AvroKeyValueSerializationSchemaFactory` to `ConfluentAvroKeyValueSerializationSchemaFactory` and moved to `avro.schemaregistry.confluent` package
- (Refactor KafkaAvro API) Removed `FixedKafkaAvroSourceFactory` and `FixedKafkaAvroSinkFactory` (now we don't support fixed schema)
- (Refactor Kafka API) Replaced `topics: List[String]` by `List[PreparedKafkaTopic]` and removed `processObjectDependencies` in `KafkaSource`

Be aware that we are using avro 1.9.2 instead of default Flink's 1.8.2 (for java time logical types conversions purpose).

* [#1013](https://github.com/TouK/nussknacker/pull/1013) Expression evaluation is synchronous now. It shouldn't cause any problems 
(all languages were synchronous anyway), but some internal code may have to change.

## In version 0.1.2

* [#957](https://github.com/TouK/nussknacker/pull/957) Custom node `aggregate` from `generic` model has changed parameter 
 from `windowLengthInSeconds` to `windowLength` with human friendly duration input. If you have used it in process, you need
 to insert correct value again.
* [#954](https://github.com/TouK/nussknacker/pull/954) `TypedMap` is not a case class wrapping scala Map anymore. If you have
 done some pattern matching on it, you should use `case typedMap: TypedMap => typedMap.asScala` instead.

## In version 0.1.1

* [#930](https://github.com/TouK/nussknacker/pull/930) `DeeplyCheckingExceptionExtractor` was moved from `nussknacker-flink-util`
  module to `nussknacker-util` module.
* [#919](https://github.com/TouK/nussknacker/pull/919) `KafkaSource` constructor now doesn't take `consumerGroup`. Instead of this
 it computes `consumerGroup` on their own based on `kafka.consumerGroupNamingStrategy` in `modelConfig` (default set to `processId`).
 You can also override it by `overriddenConsumerGroup` optional parameter.
 Regards to this changes, `KafkaConfig` has new, optional parameter `consumerGroupNamingStrategy`.
* [#920](https://github.com/TouK/nussknacker/pull/920) `KafkaSource` constructor now takes `KafkaConfig` instead of using one
 that was parsed by `BaseKafkaSourceFactory.kafkaConfig`. Also if you parse Typesafe Config to `KafkaSource` on your own, now you should
 use dedicated method `KafkaConfig.parseConfig` to avoid further problems when parsing strategy will be changed.
* [#914](https://github.com/TouK/nussknacker/pull/914) `pl.touk.nussknacker.engine.api.definition.Parameter` has deprecated
 main factory method with `runtimeClass` parameter. Now should be passed `isLazyParameter` instead. Also were removed `runtimeClass`
 from variances of factory methods prepared for easy testing (`optional` method and so on).

## In version 0.1.0

* [#755](https://github.com/TouK/nussknacker/pull/755) Default async execution context does not depend on parallelism.
 `asyncExecutionConfig.parallelismMultiplier` has been deprecated and should be replaced with `asyncExecutionConfig.workers`.
 8 should be sane default value.
* [#722](https://github.com/TouK/nussknacker/pull/722) Old way of configuring Flink and model (via `flinkConfig` and `processConfig`) is removed.
 `processTypes` configuration should be used from now on. Example:
    ```
    flinkConfig {...}
    processConfig {...}
    ```
    becomes:
    ```
    processTypes {
      "type e.g. streaming" {
        deploymentConfig { 
          type: "flinkStreaming"
          PUT HERE PROPERTIES OF flinkConfig FROM OLD CONFIG 
        }
        modelConfig {
          classPath: PUT HERE VALUE OF flinkConfig.classPath FROM OLD CONFIG
          PUT HERE PROPERTIES OF processConfig FROM OLD CONFIG
        }
      }
    }
    ```
* [#763](https://github.com/TouK/nussknacker/pull/763) Some API traits (ProcessManager, DictRegistry DictQueryService, CountsReporter) now extend `java.lang.AutoCloseable`.
* Old way of additional properties configuration should be replaced by the new one, which is now mapped to `Map[String, AdditionalPropertyConfig]`. Example in your config:
    ```
    additionalFieldsConfig: {
      mySelectProperty {
        label: "Description"
        type: "select"
        isRequired: false
        values: ["true", "false"]
      }
    }
    ```
    becomes:
    ```
    additionalPropertiesConfig {
      mySelectProperty {
        label: "Description"
        defaultValue: "false"
        editor: {
          type: "FixedValuesParameterEditor",
          possibleValues: [
            {"label": "Yes", "expression": "true"},
            {"label": "No", "expression": "false"}
          ]
        }
      }
    }
    ```  
* [#588](https://github.com/TouK/nussknacker/pull/588) [#882](https://github.com/TouK/nussknacker/pull/882) `FlinkSource` API changed, current implementation is now `BasicFlinkSource`
* [#839](https://github.com/TouK/nussknacker/pull/839) [#882](https://github.com/TouK/nussknacker/pull/882) `FlinkSink` API changed, current implementation is now `BasicFlinkSink`
* [#841](https://github.com/TouK/nussknacker/pull/841) `ProcessConfigCreator` API changed; note that currently all process objects are invoked with `ProcessObjectDependencies` as a parameter. The APIs of `KafkaSinkFactory`, `KafkaSourceFactory`, and all their implementations were changed. `Config` is available as property of `ProcessObjectDependencies` instance.
* [#863](https://github.com/TouK/nussknacker/pull/863) `restUrl` in `deploymentConfig` need to be preceded with protocol. Host with port only is not allowed anymore.
* Rename `grafanaSettings` to `metricsSettings` in configuration.

## In version 0.0.12

* Upgrade to Flink 1.7
* Refactor of custom transformations, dictionaries, unions, please look at samples in example or generic to see API changes
* Considerable changes to authorization configuration, please look at sample config to see changes
* Circe is now used by default instead of Argonaut, but still can use Argonaut in Displayable

## In version 0.0.11

* Changes in CustomStreamTransformer implementation, LazyInterpreter became LazyParameter, please look at samples to see changes in API

## In version 0.0.8

* Upgrade to Flink 1.4
* Change of format of Flink cluster configuration
* Parameters of sources and sinks are expressions now - automatic update of DB is available
* Change of configuration of Grafana dashboards
* Custom processes are defined in main configuration file now
