# Changelog

1.3.0 (Not released yet)
------------------------
* [#2841](https://github.com/TouK/nussknacker/pull/2841) Some performance improvements - reduced number of serialization round-trips for scenario jsons  
* [#2741](https://github.com/TouK/nussknacker/pull/2741) [#2841](https://github.com/TouK/nussknacker/pull/2841) Remove custom scenario (custom process)
* [#2773](https://github.com/TouK/nussknacker/pull/2773) Using VersionId / ProcessId / ProcessName instead of Long or String
* [#2830](https://github.com/TouK/nussknacker/pull/2830) `RunMode` is renamed to `ComponanteUseCase` and `Normal` value is split into: `EngineRuntime`, `Validation`, `ServiceQuery`, `TestDataGeneration`. `RunMode.Test` becomes `ComponanteUseCase.TestRuntime`
* [#2825](https://github.com/TouK/nussknacker/pull/2825), [#2868](https://github.com/TouK/nussknacker/pull/2868) API refactorings:
  * Division of API by usage: `nussknacker-components-api`, 
  `nussknacker-scenario-api`, `nussknacker-extensions-api`
  * API cleanup, some classes moved to `utils` or `interpreter`, 
    untangling dependencies, see [migration guide](MigrationGuide.md) for the details
* [#2886](https://github.com/TouK/nussknacker/pull/2886) Add explicit serialVersionUID for classes registered by `Serializers.registerSerializers`.
* [#2887](https://github.com/TouK/nussknacker/pull/2887) Request-response engine in embedded mode                                                 
* [#2890](https://github.com/TouK/nussknacker/pull/2890) Fixed displaying configured labels for node details fields.
 
1.2.0 (11 Feb 2022)
------------------------
* Added component tab
* [#2537](https://github.com/TouK/nussknacker/pull/2537) Refactoring of `LazyParameter` API:
  * `map`, `product` and `pure` methods don't require `LazyParameterInterpreter` implicit parameter anymore: can be used in other place then `LazyParameterInterpreterFunction`
  * `pure` method moved to `LazyParameter` companion object
  * new `sequence` method added to `LazyParameter` companion object
  * `map` method now takes `TypingResult => TypingResult` instead of just `TypingResult` to be visible what is relation between input and output type
* [#2535](https://github.com/TouK/nussknacker/pull/2535), [#2625](https://github.com/TouK/nussknacker/pull/2625) Rename `standalone` to `request-response`, 
  move request-response modules to `base` dir. Also - small refactorings in the engine and configuration format
* [#2483](https://github.com/TouK/nussknacker/pull/2483) Embedded DeploymentManager for Lite Streaming.
* [#2441](https://github.com/TouK/nussknacker/pull/2441) avro sink supports defaults of primitive avro types
* [#2498](https://github.com/TouK/nussknacker/pull/2498), [#2499](https://github.com/TouK/nussknacker/pull/2499), [#2503](https://github.com/TouK/nussknacker/pull/2503), [#2539](https://github.com/TouK/nussknacker/pull/2539) EspExceptionHandler is removed from ProcessConfigCreator.
  Flink engine uses now fixed exception handler: FlinkExceptionHandler. All deprecated FlinkEspExceptionHandler implementations are removed.
* [#2543](https://github.com/TouK/nussknacker/pull/2543) Eager parameters can have helpers injected.
* [#2493](https://github.com/TouK/nussknacker/pull/2493) kafka configuration is now provided by components provider configuration, if not provided avroKryoGenericRecordSchemaIdSerialization default is set to true - previously false
* [#2569](https://github.com/TouK/nussknacker/pull/2569) Flink aggregations are now part of flinkBaseComponents. `flink-model-util` is no longer needed and is removed.
* [#2651](https://github.com/TouK/nussknacker/pull/2651) Fixed behaviour of fragments which use components which clear context. 
* [#2564](https://github.com/TouK/nussknacker/pull/2564) Flink union simplification, it now takes only 'Output expression' parameters for branches (previously 'value' parameter), output variable must be of the same type
* [#2671](https://github.com/TouK/nussknacker/pull/2671) Bumped libs:
  * akka 2.15 -> 2.16
  * akka-http 10.1 -> 10.2
  * akka-http-circe 1.28 -> 1.38
* [#2684](https://github.com/TouK/nussknacker/pull/2684) Handled 'Restarting' state in Embedded DeploymentManager when the embedded scenario is failing
* [#2686](https://github.com/TouK/nussknacker/pull/2686) Rename `ServiceWithStaticParameters` to `EagerServiceWithStaticParameters` to avoid confusion about lazy and eager parameters used by default
* [#2695](https://github.com/TouK/nussknacker/pull/2695) Replaced `nodeId` with `NodeComponentInfo` in `NuExceptionInfo`
* [#2746](https://github.com/TouK/nussknacker/pull/2746) `modelConfig.classPath` can handle directories
* [#2775](https://github.com/TouK/nussknacker/pull/2775) Fixed: kafka-registry-typed-json source was recognizing logical types during typing but during evaluation were used raw, underlying types
* [#2790](https://github.com/TouK/nussknacker/pull/2790) Update Flink to 1.14.3
* [#2794](https://github.com/TouK/nussknacker/pull/2794) Added `for-each` component to basic flink components

1.1.1 (01 Feb 2022)
--------------------
* [#2660](https://github.com/TouK/nussknacker/pull/2660) Fix for handling errors after split in async mode
* [#2744](https://github.com/TouK/nussknacker/pull/2744) Ugly resource waste fixed in component drag preview
* [#2754](https://github.com/TouK/nussknacker/pull/2754) Fix error with pasting node,
* [#2807](https://github.com/TouK/nussknacker/pull/2807) Fix default values for GenericNodeTransformation

1.1.0 (07 Dec 2021)
------------------------
* [#2176](https://github.com/TouK/nussknacker/pull/2176) Allow to enrich periodic scenario config on initial schedule and each deployment.
* [#2179](https://github.com/TouK/nussknacker/pull/2179) Permission allowing for editing scenario on FE, but not saving etc.
* [#2150](https://github.com/TouK/nussknacker/pull/2150)
Better handling of multiple schedules in batch periodic engine - fixed running one time scenarios and improved current scenario status reporting.
* [#2208](https://github.com/TouK/nussknacker/pull/2208) Upgrade libraries: cats 2.6.x, cats-effect 2.5.x, circe 0.14.x
* [#1422](https://github.com/TouK/nussknacker/pull/1422) Remove `ServiceReturningType` and `WithExplicitMethod`, added helpers, small refactor
* [#2278](https://github.com/TouK/nussknacker/pull/1422) SQL Variable is removed          
* [#2280](https://github.com/TouK/nussknacker/pull/2280) Default values for parameters can be setup programmatically now - thanks to `@DefaultValue` annotation and `Parameter.defaultValue` field.
* [#2293](https://github.com/TouK/nussknacker/pull/2293) Enhancement: change `nodeCategoryMapping` configuration to `componentsGroupMapping` 
* [#2169](https://github.com/TouK/nussknacker/pull/2169) Add Apache Ignite support to SQL Component by implementing
a custom DB metadata provider that extends the standard JDBC Driver with missing features.
* [#2301](https://github.com/TouK/nussknacker/pull/2301) [#2366](https://github.com/TouK/nussknacker/pull/2366) 
  [#2409](https://github.com/TouK/nussknacker/pull/2409) [#2477](https://github.com/TouK/nussknacker/pull/2477) Simplification of component API:
  * `GenericNodeTransformation.initialParameters` was removed
  * `GenericNodeTransformation.fallbackFinalResult` introduced for not handle step, with default graceful strategy
  * `GenericNodeTransformation.contextTransformation` now handles `ParameterValidator` properly. Invalid value is handled as `FailedToDefineParameter`
and `GenericNodeTransformation.implementation` is not invoked in this case
  * `FinalResults.forValidation` utility method added to easily handle situation when you need to make some validation on context of variables (e.g. add variable checking if it already exists) 
* [#2245](https://github.com/TouK/nussknacker/pull/2245) Periodic process scheduler retries failed scenario deployments based on PeriodicBatchConfig. 
  Breaking change in PeriodicProcessListener FailedEvent. Failed event is split into FailedOnDeployEvent and FailedOnRunEvent. 
  Please note that this mechanism only retries when failure on deployment occurs - failure recovery of running scenario should be handled by [restart strategy](https://docs.nussknacker.io/docs/installation_configuration_guide/ModelConfiguration#configuring-restart-strategies-flink-only)
* [#2304](https://github.com/TouK/nussknacker/pull/2304) Upgrade to Flink 1.14
* [#2295](https://github.com/TouK/nussknacker/pull/2295) `FlinkLazyParameterFunctionHelper` has additional methods to handle exceptions during evaluation gracefully
* [#2300](https://github.com/TouK/nussknacker/pull/2300) Enhancement: refactor and improvements at components group
* [#2347](https://github.com/TouK/nussknacker/pull/2347) Support for implicit type conversions between `String` and various value classes (`Locale` etc.) 
* [#2346](https://github.com/TouK/nussknacker/pull/2346) Remove `endResult` from `Sink` in graph. 
* [#2331](https://github.com/TouK/nussknacker/pull/2331) [#2496](https://github.com/TouK/nussknacker/pull/2496) Refactor `nussknacker-avro-flink-util` module. Move non-flink specific classes to new `nussknacker-avro-util` module. 
* [#2348](https://github.com/TouK/nussknacker/pull/2348) [#2459](https://github.com/TouK/nussknacker/pull/2459) [#2486](https://github.com/TouK/nussknacker/pull/2486) 
  [#2490](https://github.com/TouK/nussknacker/pull/2490) [#2496](https://github.com/TouK/nussknacker/pull/2496)
  Refactor `nussknacker-kafka-flink-util` module. Move non-flink specific classes to `nussknacker-kafka-util` module.
* [#2344](https://github.com/TouK/nussknacker/pull/2344) Redesign of `#DATE` and `#DATE_FORMAT` utilities.
* [#2305](https://github.com/TouK/nussknacker/pull/2305) Enhancement: change `processingTypeToDashboard` configuration to `scenarioTypeToDashboard`
* [#2374](https://github.com/TouK/nussknacker/pull/2374) Auto-loaded `ComponentProvider`s
* [#2337](https://github.com/TouK/nussknacker/pull/2337) Extract base engine from standalone
  * Common functionality of base engine (i.e. microservice based, without Flink) is extracted to `base-api` and `base-runtime`
  * It's possible to use generic effect type instead of `Future`
  * Possibility to accumulate errors
  * New API for custom components (transformers and sinks)
* [#2349](https://github.com/TouK/nussknacker/pull/2349) Removed module `queryable-state`, `FlinkQueryableClient` was moved to `nussknacker-flink-manager`.
* `PrettyValidationErrors`, `CustomActionRequest` and `CustomActionResponse` moved from `nussknacker-ui` to `nussknacker-restmodel`.
* [#2361](https://github.com/TouK/nussknacker/pull/2361) Removed `security` dependency from `listener-api`. `LoggedUser` replaced with dedicated class in `listener-api`.
* [#2367](https://github.com/TouK/nussknacker/pull/2367), [#2406](https://github.com/TouK/nussknacker/pull/2406) Simple kafka-based streaming scenario interpreter. 
  Stateless, with basic kafka sinks and sources. This is MVP, not intended for direct usage, more work with sources, sinks and invoking will come in next PRs                                                                     
* [#2377](https://github.com/TouK/nussknacker/pull/2377) Remove `clazz` from `SourceFactory`. It was used mainly for test sources. 
* [#2534](https://github.com/TouK/nussknacker/pull/2534) Remove generic parameter from `Source` and `SourceFactory`. It was used mainly to determine `TypingResult` in `SourceFactory.noParam`
* [#2397](https://github.com/TouK/nussknacker/pull/2397) Common `EngineRuntimeContext` lifecycle and `MetricsProvider`, cleaning unnecessary dependencies on Flink 
* [#2486](https://github.com/TouK/nussknacker/pull/2486) Aggregates now producing context id in similar format as sources - will be visible in "Test case" during usage of tests mechanism
* [#2465](https://github.com/TouK/nussknacker/pull/2465) aggregate-sliding emitWhenEventLeft parameter changed default value from true to false
* [#2474](https://github.com/TouK/nussknacker/pull/2474) Interpreter return type changed from `F[Either[List[InterpretationResult], EspExceptionInfo[_ <: Throwable]]]` to `F[List[Either[InterpretationResult, EspExceptionInfo[_ <: Throwable]]]]`.
  Hence, e.g. multiple branches in Graph can be evaluated, both positively and negatively at the same time.
* [#2540](https://github.com/TouK/nussknacker/pull/2540) It's possible to use different Effects than `Future` in request-response (standalone) runtime. `InvocationMetrics` are no longer 
  automatically computed, as they are `Future` dependent - see `StandaloneRequestHandler` how to enable them.

1.0.0 (24 Sep 2021)
------------------------
* [#1968](https://github.com/TouK/nussknacker/pull/1968) `BestEffortJsonEncoder` uses `ServiceLoader` mechanism to
load additional encoders.
* [#1439](https://github.com/TouK/nussknacker/pull/1439) Upgrade to Flink 1.13
* [#1993](https://github.com/TouK/nussknacker/pull/1993) Demo was moved to https://github.com/TouK/nussknacker-quickstart. 
Some additional refactors done: logback configuration enhancements, simpler run.sh script, removed docker defaults from default configs.
* [#2105](https://github.com/TouK/nussknacker/pull/2105) [#2112](https://github.com/TouK/nussknacker/pull/2112)
Better handling Flink's job deploying - we report job initialization as a "DURING_DEPLOY" instead of "RUNNING" now, and we are checking available slots on Flink before deploy
* [#2152](https://github.com/TouK/nussknacker/pull/2152) Possibility to create `SchedulePropertyExtractor` using deployment manager's configuration.
* [#2133](https://github.com/TouK/nussknacker/pull/2133) SQL Variable is hidden in generic model
* [#2101](https://github.com/TouK/nussknacker/pull/2101) Global permissions can be arbitrary string, can configure all top tabs (by default `Scenarios` is available)
* [#2103](https://github.com/TouK/nussknacker/pull/2103) Counts work correctly with different timezones, `counts.queryMode` defaults to `SumOfDifferencesForRestarts`
* [#2104](https://github.com/TouK/nussknacker/pull/2104) SQL component can retrieve table names for completion
* [#2028](https://github.com/TouK/nussknacker/pull/2028) Limit (row and bytes) for generating and using test data
* Various improvements in security/OAuth components
  * [#2042](https://github.com/TouK/nussknacker/pull/2042) `redirectUrl` is optional
  * [#2070](https://github.com/TouK/nussknacker/pull/2070) separate, easy to use OIDC `AuthenticationProvider`
  * [#2079](https://github.com/TouK/nussknacker/pull/2079) anonymous access for OAuth2
  * [#2093](https://github.com/TouK/nussknacker/pull/2093) appending role claims from OAuth2 token
  * [#1933](https://github.com/TouK/nussknacker/pull/1933) being able to configure own FE `AuthenticationProvider` with module federation  
* [#2046](https://github.com/TouK/nussknacker/pull/2046) Additional functions in generic model
* Security improvements:
  * [#2067](https://github.com/TouK/nussknacker/pull/2067) Blocking dangerous methods in SpEL in runtime
  * [#1966](https://github.com/TouK/nussknacker/pull/1966) Disable dynamic property access by default
  * [#1909](https://github.com/TouK/nussknacker/pull/1909) Static method validation
  * [#1922](https://github.com/TouK/nussknacker/pull/1922) Block method invocation on `Unknown`
* [#2095](https://github.com/TouK/nussknacker/pull/2095) Remove business view
* [#2110](https://github.com/TouK/nussknacker/pull/2110) Remove node grouping
* [#2098](https://github.com/TouK/nussknacker/pull/2098) Correct timestamps for tests of Kafka sources
* [#2108](https://github.com/TouK/nussknacker/pull/2108) Enhanced class extraction settings, fewer unnecessary methods        
* [#2191](https://github.com/TouK/nussknacker/pull/2191) KafkaAvroSink performance fix
* UI enhancements:              
  * [#1706](https://github.com/TouK/nussknacker/pull/1706) New window manager, consistent behaviour, many improvements, 
    e.g. modals can be expanded to full screen, fix display of fragments in FF
  * [#2184](https://github.com/TouK/nussknacker/pull/2184), [#2101](https://github.com/TouK/nussknacker/pull/2101) Fix undo breaking UI in certain circumstances
  * [#2181](https://github.com/TouK/nussknacker/pull/2181), [#1975](https://github.com/TouK/nussknacker/pull/1975) Remove spurious 'unsaved changes' after opening aggregation nodes
  * [#2202](https://github.com/TouK/nussknacker/pull/2202) Correct hashes of FE assets
  * [#2097](https://github.com/TouK/nussknacker/pull/2097), [#2178](https://github.com/TouK/nussknacker/pull/2178) Pasting nodes in correct places
  * [#2003](https://github.com/TouK/nussknacker/pull/2003) Counts dialog fixes: timezone handling, datepicker allows editing from keyboard
  * [#2111](https://github.com/TouK/nussknacker/pull/2111) Correct graph display after opening fragment
  * [#2087](https://github.com/TouK/nussknacker/pull/2087) Pan and zoom animation
  * [#2081](https://github.com/TouK/nussknacker/pull/2081) Fix switch behaviour after changing condition
  * [#2071](https://github.com/TouK/nussknacker/pull/2071) Fix pasting cell on multiple edges
  * [#1978](https://github.com/TouK/nussknacker/pull/1978) Removed unclear node details panel
  
0.4.0 (12 Aug 2021)
------------------------
* More precise TypeInformation generation
    * [#1338](https://github.com/TouK/nussknacker/pull/1338) Defining TypeInformation based on TypingResult
    * [#1343](https://github.com/TouK/nussknacker/pull/1343) Aggregators compute stored types    
    * [#1343](https://github.com/TouK/nussknacker/pull/1359) Improvements in variable output validation 
    * [#1360](https://github.com/TouK/nussknacker/pull/1360) Service query can use global variables
    * [#1375](https://github.com/TouK/nussknacker/pull/1375) Opt-in for new TypeInformation detection for inter operator serialization
* [#1361](https://github.com/TouK/nussknacker/pull/1361) Lazy vars removal
* [#1363](https://github.com/TouK/nussknacker/pull/1363) Open/close only services that are actually used in process
* [#1367](https://github.com/TouK/nussknacker/pull/1367) Custom actions - first, experimental version
* Migration of CI to github actions
    * [#1368](https://github.com/TouK/nussknacker/pull/1368) Publish docker images/jars via GH actions (experimental)
    * [#1381](https://github.com/TouK/nussknacker/pull/1381) Use GH Actions for coverage
    * [#1383](https://github.com/TouK/nussknacker/pull/1383) Switch github badges
* [#1382](https://github.com/TouK/nussknacker/pull/1382) First E2E FE tests                                                             
* [#1373](https://github.com/TouK/nussknacker/pull/1373) Ability to load custom model config programmatically
* [#1406](https://github.com/TouK/nussknacker/pull/1406) Eager services - ability to create service object using static parameters
* [#962](https://gihub.com/TouK/nussknacker/pull/962) New ways of querying InfluxDB for counts, integration tests, no default database name in code
* [#1428](https://github.com/TouK/nussknacker/pull/1428) Kafka SchemaRegistry source/sink can use JSON payloads. In this PR we assume one schema registry contains either json or avro payloads but not both.                                         
* [#1445](https://github.com/TouK/nussknacker/pull/1445) Small refactor of RecordFormatter, correct handling different formatting in kafka-json in test data generation
* [#1433](https://github.com/TouK/nussknacker/pull/1433) Pass DeploymentData to process, including deploymentId and possible additional info                
* [#1458](https://github.com/TouK/nussknacker/pull/1458) `PeriodicProcessListener` allows custom handling of `PeriodicProcess` events                
* [#1466](https://github.com/TouK/nussknacker/pull/1466) `ProcessManager` API allows to return ExternalDeploymentId immediately from deploy         
* [#1405](https://github.com/TouK/nussknacker/pull/1405) 'KafkaAvroSinkFactoryWithEditor' for more user-friendly Avro message definition. 
* [#1514](https://github.com/TouK/nussknacker/pull/1514) Expose DeploymentData in Flink UI via `NkGlobalParameters`
* [#1510](https://github.com/TouK/nussknacker/pull/1510) `FlinkSource` API allows to create stream of `Context` (FlinkSource API and test support API refactoring).
* [#1497](https://github.com/TouK/nussknacker/pull/1497) Initial support for multiple (named) schedules in `PeriodicProcessManager`
* [#1499](https://github.com/TouK/nussknacker/pull/1499) ClassTag is provided in params in avro key-value deserialization schema factory: `KafkaAvroKeyValueDeserializationSchemaFactory`
* [#1533](https://github.com/TouK/nussknacker/pull/1533) Fix: Update process with same json
* [#1546](https://github.com/TouK/nussknacker/pull/1546) Unions (e.g after split) are possible in standalone mode. Also, it's possible to define transformers which operate on all results (e.g. for sorting recommendations)
* [#1547](https://github.com/TouK/nussknacker/pull/1547) Publish first version of BOM including dependencyOverrides
* [#1543](https://github.com/TouK/nussknacker/pull/1543) `ComponentProvider` API enables adding new extensions without changing e.g. `ProcessConfigCreator`
* [#1471](https://github.com/TouK/nussknacker/pull/1471) Initial version of session window aggregate added (API may change in the future).
* [#1631](https://github.com/TouK/nussknacker/pull/1631) Ability to use multiple config files with `nussknacker.config.location` system property
* [#1512](https://github.com/TouK/nussknacker/pull/1512) `KafkaSourceFactory` is replaced with source that provides additional #inputMeta variable with event's metadata.
* [#1663](https://github.com/TouK/nussknacker/pull/1663) Flink restart strategies and exception consumers can now be configured.
* [#1728](https://github.com/TouK/nussknacker/pull/1728) Replace schemaRegistryClient and recordFormatter in `SchemaRegistryProvider` with their factories.
* [#1651](https://github.com/TouK/nussknacker/pull/1651) `KafkaAvroSourceFactory` provides additional #inputMeta variable with event's metadata.
* [#1756](https://github.com/TouK/nussknacker/pull/1756) `TypingResultAwareTypeInformationDetection` can be used to serialize aggregates more efficiently
* [#1772](https://github.com/TouK/nussknacker/pull/1772) Fix for Spel validation when we try use not existing method reference
* [#1741](https://github.com/TouK/nussknacker/pull/1741) KafkaExceptionConsumer can be configured to send errors to Kafka
* [#1809](https://github.com/TouK/nussknacker/pull/1809) Performance optimization for aggregates: do not update state if added element is neutral for current state
* [#1886](https://github.com/TouK/nussknacker/pull/1886) Performance optimization for aggregates: do not save context in state. Added `#AGG` utility for easier switching from simple aggregating functions like `'Sum'` to more complex `#AGG.map()`
* [#1820](https://github.com/TouK/nussknacker/pull/1820) Added missing support for some logical types (LocalDate, LocalTime, UUID) in json encoding
* [#1799](https://github.com/TouK/nussknacker/pull/1799) ConfluentAvroToJsonFormatter produces and reads test data in valid json format with full kafka metadata and schema ids.
* [#1839](https://github.com/TouK/nussknacker/pull/1839) Set up `explicitUidInStatefulOperators` model's flag to `true` by default.
* [#1357](https://github.com/TouK/nussknacker/pull/1357) Add run mode to nodes to be able to determine if we are inside e.g. test process run.
  Run mode is can be declared as a dependency in generic node transformations. Nodes created via `@MethodToInvoke` can declare `RunMode` as an implicit parameter.
  `RunMode` is also available in `FlinkCustomNodeContext`.
* Various naming changes:
  * [#1917](https://github.com/TouK/nussknacker/pull/1917) configuration of `engineConfig` to `deploymentConfig`                           
  * [#1911](https://github.com/TouK/nussknacker/pull/1911) Rename `process` to `scenario`, `subprocess` to `fragment` in messages at backend and some test cases names                                                        
  * [#1921](https://github.com/TouK/nussknacker/pull/1921) `ProcessManager` to `DeploymentManager`                           
  * [#1927](https://github.com/TouK/nussknacker/pull/1927) Rename `outer-join` to `single-side-join`
* Performance fixes:
    * [#1330](https://github.com/TouK/nussknacker/pull/1330) Multiple times parsing expressions in map/product LazyParameter
    * [#1331](https://github.com/TouK/nussknacker/pull/1331) LoggingListener caches loggers
    * [#1334](https://github.com/TouK/nussknacker/pull/1334) Type promotion cache
    * [#1335](https://github.com/TouK/nussknacker/pull/1335) Omitting zeros for sum aggregate to avoid unnecessary buckets 
    * [#1336](https://github.com/TouK/nussknacker/pull/1336) Aggregation metrics
* [#1321](https://github.com/TouK/nussknacker/pull/1321) Exception handler accessible via custom node context, avro record encoding errors reported by exception handler

0.3.0 (17 Nov 2020)
------------------------
* [#1298](https://github.com/TouK/nussknacker/pull/1298) Feature flag `avroKryoGenericRecordSchemaIdSerialization` for avro kryo serialization optimization (default = false)
* [#1315](https://github.com/TouK/nussknacker/pull/1315) Spring bumped 5.1.4 -> 5.1.19
* [#1312](https://github.com/TouK/nussknacker/pull/1312) Ficus bumped 1.4.1 -> 1.4.7
* [#1288](https://github.com/TouK/nussknacker/pull/1288) Namespaces can be configured for ObjectNaming
* [#1261](https://github.com/TouK/nussknacker/pull/1261) Fix: Access to `map.missingKey` caused `Property or field cannot be found on object`
* [#1244](https://github.com/TouK/nussknacker/pull/1244) Ability to define `variablesToHide` in `Parameter`
* [#1165](https://github.com/TouK/nussknacker/pull/1165) Typed global variables
* [#1128](https://github.com/TouK/nussknacker/pull/1128) Union-memo transformer
* [#1054](https://github.com/TouK/nussknacker/pull/1054) Tabbed dark process list
* Configuration improvements (library upgrade, conventions): 
  [#1151](https://github.com/TouK/nussknacker/pull/1151), 
  [#1166](https://github.com/TouK/nussknacker/pull/1166) 
* [#873](https://github.com/TouK/nussknacker/pull/873), [#1044](https://github.com/TouK/nussknacker/pull/1044) Flink upgrade (to 1.11) 
* More graceful handling of Flink compatibility issues (in particular, ```FlinkCompatibilityProvider`` trait introduced, also
ProcessManager implementations are separated from UI to allow easier changes in deployments):
  [#1150](https://github.com/TouK/nussknacker/pull/1150),
  [#1218](https://github.com/TouK/nussknacker/pull/1218) 
* [#1183](https://github.com/TouK/nussknacker/pull/1183) New back to process button on metrics 
* [#1188](https://github.com/TouK/nussknacker/pull/1188) Fix env label and provide nussknacker logo
* [#249](https://github.com/TouK/nussknacker/pull/1201) Inferred expression type in node modal
* [#1255](https://github.com/TouK/nussknacker/pull/1255) Moved displaying `Metrics tab` to `customTabs`
* [#1257](https://github.com/TouK/nussknacker/pull/1257) Improvements: Flink test util package
* [#1287](https://github.com/TouK/nussknacker/pull/1287) OAuth2: add accessTokenRequestContentType parameter
* [#1290](https://github.com/TouK/nussknacker/pull/1290) Own kryo serializers can be provided through SPI
* [#1303](https://github.com/TouK/nussknacker/pull/1303) TypedObjectTypingResult can have additional info (e.g. Schema for GenericRecord)

0.2.2 (03 Sep 2020)
-----------------------
* [#1175](https://github.com/TouK/nussknacker/pull/1175) Fix for: BestEffortAvroEncoder haven't produced record with logical types for missing field with default values
* [#1173](https://github.com/TouK/nussknacker/pull/1173) Fix for: Avro source wasn't be able to read record with schema with invalid defaults

0.2.1 (31 Aug 2020)
-----------------------
* [#1127](https://github.com/TouK/nussknacker/pull/1127) Fix too small count values
* [#1133](https://github.com/TouK/nussknacker/pull/1133) Improvements: More flexible TestReporter instancies implementation 
* [#1131](https://github.com/TouK/nussknacker/pull/1131) Fix: Disable "deploy" & "metrics" buttons for subprocess  
* [#1148](https://github.com/TouK/nussknacker/pull/1148) Fix FE regexp for match node id

0.2.0 (07 Aug 2020)
------------------------
* [#1099](https://github.com/TouK/nussknacker/pull/1099) New outer-join node
* [#1024](https://github.com/TouK/nussknacker/pull/1024) Added default async interpretation value configured by `asyncExecutionConfig.defaultUseAsyncInterpretation` (false if missing).
* [#879](https://github.com/TouK/nussknacker/pull/879) Metrics can now use Flink variables for better reporting, it's recommended to use InfluxDB native protocol instead of legacy Graphite protocol to send metrics to InfluxDB.
* [#940](https://github.com/TouK/nussknacker/pull/940) More detailed node errors 
* [#949](https://github.com/TouK/nussknacker/pull/949) JVM options can be configured via JDK_JAVA_OPTIONS env variable (in docker and standalone distribution) 
* [#954](https://github.com/TouK/nussknacker/pull/954) Correct handling of types in empty inline lists 
* [#944](https://github.com/TouK/nussknacker/pull/903) System cache mechanism
* [#704](https://github.com/TouK/nussknacker/pull/704) Preloaded creator panel node icons
* [#943](https://github.com/TouK/nussknacker/pull/943) Literal min / max validators
* [#976](https://github.com/TouK/nussknacker/pull/976) Fixed save button & groups expand for businessView
* [#973](https://github.com/TouK/nussknacker/pull/973) Textarea editor
* [#987](https://github.com/TouK/nussknacker/pull/987) Optimized graph rendering time, fixed minor bugs (expand group icon, view center & fit after layout).
* Introduction to KafkaAvro API: 
    [#871](https://github.com/TouK/nussknacker/pull/871), 
    [#881](https://github.com/TouK/nussknacker/pull/881), 
    [#903](https://github.com/TouK/nussknacker/pull/903),
    [#981](https://github.com/TouK/nussknacker/pull/981), 
    [#989](https://github.com/TouK/nussknacker/pull/989), 
    [#998](https://github.com/TouK/nussknacker/pull/998), 
    [#1007](https://github.com/TouK/nussknacker/pull/1007), 
    [#1014](https://github.com/TouK/nussknacker/pull/1014),
    [#1041](https://github.com/TouK/nussknacker/pull/1041),
* Performance improvements in interpreter: [#1008](https://github.com/TouK/nussknacker/pull/1008),
 [#1013](https://github.com/TouK/nussknacker/pull/1013). The second one also removes Future[] from expression evaluation 
* Dynamic parameters: filter validation, GenericNodeTransformation introduction (for CustomNodes, Sources, Sinks) - also handling dynamic parameters on UI: 
    [#978](https://github.com/TouK/nussknacker/pull/978), 
    [#996](https://github.com/TouK/nussknacker/pull/996), 
    [#1001](https://github.com/TouK/nussknacker/pull/1001),
    [#1011](https://github.com/TouK/nussknacker/pull/1011)
* [#988](https://github.com/TouK/nussknacker/pull/988) Json editor
* [#1066](https://github.com/TouK/nussknacker/pull/1066) Duration and period editors fixes
* [#1126](https://github.com/TouK/nussknacker/pull/1126) New nodes: periodic source, delay and dead-end

0.1.2 (15 May 2020)
------------------------
* [#965](https://github.com/TouK/nussknacker/pull/965) Added new, 'aggregate-tumbling' node.
* [#957](https://github.com/TouK/nussknacker/pull/957) Custom node `aggregate` has now additional aggregation function `Sum`.
 Also was changed parameter from `windowLengthInSeconds` to `windowLength` with human friendly duration input.

0.1.1 (06 May 2020)
------------
* Branch parameters now can be eager (computed during process compilation)
* More restrictive type checking in SpEL - mainly added verification of types of method's paramaters
* Added support for Kafka consumer group strategies - setted up by `kafka.consumerGroupNamingStrategy` configuraton option
* Bugfixes for joins

0.1.0 (30 Apr 2020)
-------------
* Added support for explicitly setting uids in operators - turned on by `explicitUidInStatefulOperators` model's flag.
By default setted up to false.
* Old way of configuring Flink and model (via `flinkConfig` and `processConfig`) is removed. `processTypes` 
configuration should be used from now on.
* Change of additional properties configuration

0.0.12 (26 Oct 2019)
--------------------
* Cross builds with Scala 2.11 and 2.12
* First version of join nodes
* OAuth2 authentication capabilities
* Migration of Argonaut to Circe
* Preliminary version of dictionaries in expressions
* Major upgrade of frontend libraries (React, Redux, etc)
* Various usability improvements

0.0.11 (1 Apr 2019)
---------

0.0.10 (13 Nov 2018)
---------

0.0.9 (13 Jul 2018)
---------

0.0.8 (7 May 2018)
---------
- expressions code syntax highlighting
- source/sink params as expressions
- multiline expression suggestions
- method signature and documentation in code suggestions
- inject new node after dragging on edge
- Query services tab in UI
- subprocess disabling
- display http request-response for query service tab
- flink kafka 0.11 connector
- dynamic source return type
- SQL can be used as expression language
- Processes page rendering optimized
- suggestions for projections/selections in spel
- upgrade to flink 1.4.2
- upgrade to scala 2.11.12
- Make sinks disableable

0.0.7 (22 Dec 2017)
---------
- global imports in expressions
- deployment standalone on multiple nodes
- typed SpEL expressions - first iteration
- can post process standalone results
- support for java services
- handling get requests in standalone mode
- metric fixes for standalone
- compare with other env
- split in request/response mode by expression
- ProcessConfigCreator Java API support added
- extendable authentication
- comparing environments - first part, can compare processes
- subprocess versions
- process migrations + some refactoring
- async execution with toggle
- better exception for errors in service invocations
- nussknacker java api
- spring version bump because of SPR-9194

0.0.6 (9 Aug 2017)
---------
First open source version :)
