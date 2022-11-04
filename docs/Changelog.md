
# Changelog

1.6.1 (Not released yet)
------------------------
* [#3647](https://github.com/TouK/nussknacker/pull/3647) Fix for serving OpenApi definition and SwaggerUI for deployed RequestResponse scenarios in embedded mode
* [#3657](https://github.com/TouK/nussknacker/pull/3657) Fix for json-schema additionalProperties validation
* [#3672](https://github.com/TouK/nussknacker/pull/3672) Fix contextId assignment for the output of ForEachTransformer (flink)
* [#3657](https://github.com/TouK/nussknacker/pull/3657) Fix: do not show extra scrollbar on scenario screen when panel too large
* [#3679](https://github.com/TouK/nussknacker/pull/3679) Fix: validate multiple same fragments used in a row in legacy scenario jsons (without `outputVariableNames` field in `SubprocessRef`)

1.6.0 (18 Oct 2022)
------------------------
* [#3382](https://github.com/TouK/nussknacker/pull/3382) Security fix: Http cookie created by NU when using OAuth2 is now secure.
* [#3385](https://github.com/TouK/nussknacker/pull/3385) Security fix: add http headers `'X-Content-Type-Options':'nosniff'` and `'Referrer-Policy':'no-referrer'`.
* [#3370](https://github.com/TouK/nussknacker/pull/3370) Feature: scenario node category verification on validation
* [#3390](https://github.com/TouK/nussknacker/pull/3390) Request-Response mode available for k8s deployment
* [#3392](https://github.com/TouK/nussknacker/pull/3392) Validate scenario before deploy
* [#3436](https://github.com/TouK/nussknacker/pull/3436) Added types with value to results of operators
* [#3406](https://github.com/TouK/nussknacker/pull/3406) Scalatest 3.0.8 -> 3.2.10, Scalacheck 1.14.0 -> 1.15.0
* [#3401](https://github.com/TouK/nussknacker/pull/3401) Request-Response mode publishes OpenApi specification for its services
* [#3427](https://github.com/TouK/nussknacker/pull/3427) Added components/common/extra,components/lite/extra,.. directories for purpose of easier components adding
* [#3437](https://github.com/TouK/nussknacker/pull/3437) Switch RR typing to SwaggerBasedJsonSchemaTypeDefinitionExtractor
* [#3451](https://github.com/TouK/nussknacker/pull/3451) SwaggerEnrichers as well as RequestResponse support now primitive schemas
* [#3473](https://github.com/TouK/nussknacker/pull/3473) JsonRequestResponseSinkFactory provides also 'raw editor'
* [#3441](https://github.com/TouK/nussknacker/pull/3441) Updated Flink 1.14.5 -> 1.15.2
* [#3483](https://github.com/TouK/nussknacker/pull/3483) Fix for: errors were flickering in newly used dynamic components - 
  after entering to node edition they were disappearing. Now this transient situation is replaced by well-prepared form
  with whole necessary parameters.
* [#3493](https://github.com/TouK/nussknacker/pull/3493), [#3582](https://github.com/TouK/nussknacker/pull/3582) Methods added to to `DeploymentManagerProvider`:
  * `additionalPropertiesConfig`, which allows to configure additional scenario properties programmatically.
  * `additionalValidators`, which allows to define DeploymentManager-specific validators.
* [#3505](https://github.com/TouK/nussknacker/pull/3505) Bump node version to 16.5.1
* [#3506](https://github.com/TouK/nussknacker/pull/3506) Fix date formatting to use client timezone                    
* [#3542](https://github.com/TouK/nussknacker/pull/3542) Feature: properties validation and properties additional info 
  (similar to `NodeAdditionalInfo`)
* [#3545](https://github.com/TouK/nussknacker/pull/3545) Testkit refactor: unification of flinkBased TestScenarioRunner and kafkaLiteBased, easier usage of kafkaLiteBased
* [#3440](https://github.com/TouK/nussknacker/pull/3440) Feature: allow to define fragment outputs
* [#3580](https://github.com/TouK/nussknacker/pull/3580) SwaggerEnrichers support relative service urls and handle situation when 
  only definition url is provided (without services inside definition and without rootUrl)
* [#3584](https://github.com/TouK/nussknacker/pull/3584) ReqRes Feature: secure RR scenario service/ingress
* [#3598](https://github.com/TouK/nussknacker/pull/3598) Introducing configuration for controlling anonymous usage reporting by FE  
* [#3608](https://github.com/TouK/nussknacker/pull/3608) Use `ZonedDateTime` for `date-time` JsonSchema format
* [#3619](https://github.com/TouK/nussknacker/pull/3619) Patch `KafkaMetricWrapper.java` until Flink 1.15.3 is released. Read [more](https://issues.apache.org/jira/browse/FLINK-28488) about this bug. 
* [#3574](https://github.com/TouK/nussknacker/pull/3574) Feature: instance logo can be shown next to Nu logo, by convention it has
  to be available at path `<nu host>/assets/img/instance-logo.svg`
* [#3524](https://github.com/TouK/nussknacker/pull/3524) Change base docker image to eclipse temurin due to openjdk deprecation.
* [#3632](https://github.com/TouK/nussknacker/pull/3632) Fix presenting validation errors for properties.

1.5.0 (16 Aug 2022)
------------------------
* [#3099](https://github.com/TouK/nussknacker/pull/3099) Added validation for input nodes names in UnionMemo
* [#2992](https://github.com/TouK/nussknacker/pull/2992) Moved DeploymentComment validation to backend. Deploy with invalid comment now returns error with validation information, which is shown below input like in case of node parameters.
* [#3113](https://github.com/TouK/nussknacker/pull/3113) Moved last panel tab Services from Admin tab. Removed Admin tab. 
* [#3121](https://github.com/TouK/nussknacker/pull/3121) Components and Component usages filters are more like those on Scenarios. Scenario status and editor is now visible on Component usages. Some performance issues fixed. Minor visual changes.   
* [#3136](https://github.com/TouK/nussknacker/pull/3136) Improvements: Lite Kafka testkit
* [#3178](https://github.com/TouK/nussknacker/pull/3178) Improvements: more complex test scenario runner result
* [#3134](https://github.com/TouK/nussknacker/pull/3134) Metric counters (e.g. nodeCount) are initialized eagerly to minimize problems with initial count computations.
* [#3162](https://github.com/TouK/nussknacker/pull/3162) OAuth2 access token can be optionally set in cookie (useful e.g. for Grafana proxy authentication)             
* [#3165](https://github.com/TouK/nussknacker/pull/3165) Added configuration `enableConfigEndpoint` which controls whether expose config over http (GET /api/app/config/). Default value is false.
* [#3169](https://github.com/TouK/nussknacker/pull/3169) API endpoint `/api/app/healthCheck` returning short json answer with "OK" status is now not secured - you can use it without authentication
* [#3075](https://github.com/TouK/nussknacker/pull/3075) Added full outer join
* [#3183](https://github.com/TouK/nussknacker/pull/3183) Attachments table has proper column format (migration is automatic, doesn't need any manual actions)
* [#3189](https://github.com/TouK/nussknacker/pull/3189) Pass accessToken to iframes
* [#3192](https://github.com/TouK/nussknacker/pull/3192) Improvements: db enrichers measuring
* [#3198](https://github.com/TouK/nussknacker/pull/3198) Fix: request response metrics
* [#3149](https://github.com/TouK/nussknacker/pull/3149) Changed end bracket for SpEL in SQL to `}#`
* [#3191](https://github.com/TouK/nussknacker/pull/3191) Fix: wrong value shown when removing row in MapVariable
* [#3227](https://github.com/TouK/nussknacker/pull/3227) Allow TAB navigation from expression editors
* [#3208](https://github.com/TouK/nussknacker/pull/3208) Fix: set maxAge in seconds in set-cookie header
* [#3209](https://github.com/TouK/nussknacker/pull/3209) ConfigMap for K8 runtime has been split into two config maps (to separate logback conf) and one secret (with model config - which often contains confidential data)
* [#3187](https://github.com/TouK/nussknacker/pull/3187) [#3224](https://github.com/TouK/nussknacker/pull/3224) Switch component replaced by Choice component.
  Moved choice/filter edges conditions configuration to form visible in node window, added few enhancements: ordered of switch edges, only false edge for filter component. 
  "Default" choice edge type, exprVal and expression are now deprecated and disabled in new usages.
* [#3187](https://github.com/TouK/nussknacker/pull/3187) Fix: duplicated union edges.
* [#3210](https://github.com/TouK/nussknacker/pull/3210) Expose UI metrics and scenario lite metrics via Prometheus
* [#3045](https://github.com/TouK/nussknacker/pull/3045) json2avro bump 0.2.11 -> 0.2.15 + fix default values wasn't converted to logical types
* [#3223](https://github.com/TouK/nussknacker/pull/3223) Fix for encoding/decoding JWT & OIDC tokens - correct handling fields representing epoch time (e.g. `exp` - which represents token expiration time). Also, CachingOAuth2Service was migrated to use sync cache instead of async (evicting data in async cache can be tricky - `expireAfterWriteFn` is not applied to not completed futures). Since, it was only usage of `DefaultAsyncCache` - it has been removed from the codebase.
* [#3239](https://github.com/TouK/nussknacker/pull/3239) Added jul-to-slf4j to be sure that all logs going via logback
* [#3238](https://github.com/TouK/nussknacker/pull/3238) K8 runtime's logback conf can be stored in single ConfigMap for all runtime pods
* [#3201](https://github.com/TouK/nussknacker/pull/3201) Added literal types
* [#3240](https://github.com/TouK/nussknacker/pull/3240) Error topic created by default if not exists
* [#3245](https://github.com/TouK/nussknacker/pull/3245) [#3265](https://github.com/TouK/nussknacker/pull/3265)
  [#3288](https://github.com/TouK/nussknacker/pull/3288) [#3295](https://github.com/TouK/nussknacker/pull/3295) [#3297](https://github.com/TouK/nussknacker/pull/3297)
  [#3299](https://github.com/TouK/nussknacker/pull/3299) [#3309](https://github.com/TouK/nussknacker/pull/3309) [#3316](https://github.com/TouK/nussknacker/pull/3316)
  [#3322](https://github.com/TouK/nussknacker/pull/3322) [#3337](https://github.com/TouK/nussknacker/pull/3337) [#3287](https://github.com/TouK/nussknacker/pull/3287)
  Universal kafka source/sink, handling multiple scenarios like: avro message for avro schema, json message for json schema. Legacy, low level kafka components can be turned on by new lowLevelComponentsEnabled flag 
  * [#3317](https://github.com/TouK/nussknacker/pull/3317) Support json schema in universal source
  * [#3332](https://github.com/TouK/nussknacker/pull/3332) Config option to handle json payload with avro schema
  * [#3354](https://github.com/TouK/nussknacker/pull/3354) Universal source optimization - if message without schemaId, using cache when getting one
  * [#3346](https://github.com/TouK/nussknacker/pull/3346) UniversalKafkaSink provides also 'raw editor'
  * [#3345](https://github.com/TouK/nussknacker/pull/3345) Swagger 2.2.1, OpenAPI 3.1, jsonSchema typing and deserialization same as in openapi components
  
* [#3249](https://github.com/TouK/nussknacker/pull/3249) Confluent 5.5->7.2, avro 1.9->1.11 bump
* [#3250](https://github.com/TouK/nussknacker/pull/3250) [#3302](https://github.com/TouK/nussknacker/pull/3302) Kafka 2.4 -> 2.8, Flink 1.14.4 -> 1.14.5
* [#3270](https://github.com/TouK/nussknacker/pull/3270) Added type representing null
* [#3263](https://github.com/TouK/nussknacker/pull/3263) Batch periodic scenarios carry processing type to distinguish scenarios with different categories.
* [#3269](https://github.com/TouK/nussknacker/pull/3269) Fix populating cache in CachingOAuth2Service. It is fully synchronous now.
* [#3264](https://github.com/TouK/nussknacker/pull/3264) Added support for generic functions
* [#3253](https://github.com/TouK/nussknacker/pull/3253) Separate validation step during scenario deployment
* [#3328](https://github.com/TouK/nussknacker/pull/3328) Schema type aware serialization of `NkSerializableParsedSchema`
* [#3071](https://github.com/TouK/nussknacker/pull/3071) [3379](https://github.com/TouK/nussknacker/pull/3379) More strict avro schema validation: include optional fields validation, 
  handling some invalid cases like putting long to int field, strict union types validation, reduced number of validation modes to lax | strict.
* [#3289](https://github.com/TouK/nussknacker/pull/3289) Handle asynchronous deployment and status checks better
* [#3071](https://github.com/TouK/nussknacker/pull/3334) Improvements: Allow to import file with different id
* [#3412](https://github.com/TouK/nussknacker/pull/3412) Corrected filtering disallowed types in methods
* [#3363](https://github.com/TouK/nussknacker/pull/3363) Kafka consumer no longer set `auto.offset.reset` to `earliest` by default. Instead, Kafka client will use default Kafka value which is `latest`
* [#3371](https://github.com/TouK/nussknacker/pull/3371) Fix for: Indexing on arrays wasn't possible
* [#3376](https://github.com/TouK/nussknacker/pull/3376) (Flink) Handling kafka source deserialization errors by exceptionHandler (https://nussknacker.io/documentation/docs/installation_configuration_guide/model/Flink#configuring-exception-handling) 

1.4.0 (14 Jun 2022)
------------------------
* [#2983](https://github.com/TouK/nussknacker/pull/2983) Extract Permission to extensions-api
* [#3010](https://github.com/TouK/nussknacker/pull/3010) Feature: Docker Java Debug Option
* [#3003](https://github.com/TouK/nussknacker/pull/3003) Streaming-lite runtime aware of k8s resource quotas
* [#3028](https://github.com/TouK/nussknacker/pull/3028) Force synchronous interpretation for scenario parts that does not contain any services
  (enrichers, processors). Disable the feature with flag `globalParameters.forceSyncInterpretationForSyncScenarioPart: false`.
* [#3006](https://github.com/TouK/nussknacker/pull/3006) Fixed passing RESTARTING status to GUI (applies to both Flink and K8 engines)
* [#3029](https://github.com/TouK/nussknacker/pull/3029) Added `kafka.schemaRegistryCacheConfig` (was hardcoded before)
* [#3047](https://github.com/TouK/nussknacker/pull/3047) Remove deprecated Admin panel tabs that are replaced with Components tab: 
  Search Components and Unused Components (together with API endpoints: /processesComponents and /unusedComponents)
* [#3049](https://github.com/TouK/nussknacker/pull/3049) Added `collector` component to lite base components
* [#3065](https://github.com/TouK/nussknacker/pull/3065) OIDC: Passing jwt audience in request to /authorize
* [#3066](https://github.com/TouK/nussknacker/pull/3066) Fix for OAuth2 authentication: Don't redirect when 'invalid_request' error is passed to avoid redirection loop
* [#3067](https://github.com/TouK/nussknacker/pull/3067) OIDC: More precise error messages during JWT validation
* [#3068](https://github.com/TouK/nussknacker/pull/3068) OIDC: Support for JWT encoded using symmetric public key
* [#3063](https://github.com/TouK/nussknacker/pull/3063) [#3067](https://github.com/TouK/nussknacker/pull/3067) [#3070](https://github.com/TouK/nussknacker/pull/3070) Add integration with [JmxExporter Agent](https://github.com/prometheus/jmx_exporter).
* [#3077](https://github.com/TouK/nussknacker/pull/3077) Change scenarios tab to use new UI by default
* [#3084](https://github.com/TouK/nussknacker/pull/3084) Change `for-each` from `SingleElementComponent` to `LiteCustomComponent`
* [#3114](https://github.com/TouK/nussknacker/pull/3114) Add `flush` method to `WithSharedKafkaProducer`
* [#3034](https://github.com/TouK/nussknacker/pull/3034) Fixed sorting on new scenarios list
* [#3330](https://github.com/TouK/nussknacker/pull/3330) ConfluentUniversalKafkaDeserializer - deserialize using latest schema for topic if no headers or magic-byte/schemaId/payload

1.3.0 (22 Apr 2022)
------------------------
* [#2967](https://github.com/TouK/nussknacker/pull/2967) Add json-utils module and move there json-utils from `liteRequestResponseComponents`.
* [#2955](https://github.com/TouK/nussknacker/pull/2955) Add Json schema sink/source (with editor) for request/response. Move inputSchema to properties.
* [#2841](https://github.com/TouK/nussknacker/pull/2841) Some performance improvements - reduced number of serialization round-trips for scenario jsons  
* [#2741](https://github.com/TouK/nussknacker/pull/2741) [#2841](https://github.com/TouK/nussknacker/pull/2841) Remove custom scenario (custom process)
* [#2773](https://github.com/TouK/nussknacker/pull/2773) Using VersionId / ProcessId / ProcessName instead of Long or String
* [#2830](https://github.com/TouK/nussknacker/pull/2830) `RunMode` is renamed to `ComponanteUseCase` and `Normal` value is split into: `EngineRuntime`, `Validation`, `ServiceQuery`, `TestDataGeneration`. `RunMode.Test` becomes `ComponanteUseCase.TestRuntime`
* [#2825](https://github.com/TouK/nussknacker/pull/2825), [#2868](https://github.com/TouK/nussknacker/pull/2868), [#2907](https://github.com/TouK/nussknacker/pull/2907) API refactorings:
  * Division of API by usage: `nussknacker-components-api`, 
  `nussknacker-scenario-api`, `nussknacker-extensions-api`
  * API cleanup, some classes moved to `utils` or `interpreter`, 
    untangling dependencies, see [migration guide](MigrationGuide.md) for the details
* [#2886](https://github.com/TouK/nussknacker/pull/2886) Add explicit serialVersionUID for classes registered by `Serializers.registerSerializers`.
* [#2887](https://github.com/TouK/nussknacker/pull/2887) Request-Response engine in embedded mode                                                 
* [#2890](https://github.com/TouK/nussknacker/pull/2890) Fixed displaying configured labels for node details fields.
* [#2920](https://github.com/TouK/nussknacker/pull/2920) Close periodic engine actors. Reverse processing type reload - close and then reload.
* [#2941](https://github.com/TouK/nussknacker/pull/2941) Update Flink to 1.14.4
* [#2957](https://github.com/TouK/nussknacker/pull/2957) Add `executionConfig.rescheduleOnFailure` flag to control whether failed deployment should be rescheduled for a next run.
* [#2972](https://github.com/TouK/nussknacker/pull/2972) Add Url type to tabs configuration

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
