# Migration guide

To see the biggest differences please consult the [changelog](Changelog.md).

## In version 1.17.0 (Not released yet)

### Code API changes

* [#6248](https://github.com/TouK/nussknacker/pull/6248) Removed implicit conversion from string to SpeL
  expression (`pl.touk.nussknacker.engine.spel.Implicits`). The conversion should be replaced by
  `pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion.spel`.
* [6282](https://github.com/TouK/nussknacker/pull/6184) If you relied on the default value of the `topicsExistenceValidationConfig.enabled`
  setting, you must now be aware that topics will be validated by default (Kafka's `auto.create.topics.enable` setting
  is only considered in case of Sinks). Create proper topics manually if needed.
* Component's API changes
  * [#6418](https://github.com/TouK/nussknacker/pull/6418) Improvement: Pass implicit nodeId to `EagerServiceWithStaticParameters.returnType`
    Now method `returnType` from `EagerServiceWithStaticParameters` requires implicit nodeId param
  * [#6462](https://github.com/TouK/nussknacker/pull/6462) `CustomStreamTransformer.canHaveManyInputs` field was
    removed. You don't need to implement any other method in replacement, just remove this method.
  * [#6418](https://github.com/TouK/nussknacker/pull/6418) Improvement: Pass implicit nodeId to `EagerServiceWithStaticParameters.returnType`
      * Now method `returnType` from `EagerServiceWithStaticParameters` requires implicit nodeId param
  * [#6340](https://github.com/TouK/nussknacker/pull/6340) `TestRecordParser` trait used in `SourceTestSupport` trait
    changed to work on lists instead of single records - its `parse` method now takes `List[TestRecord]` instead of a
    single `TestRecord` and returns a list of results instead of a single result.
  * [#6520](https://github.com/TouK/nussknacker/pull/6520) `ExplicitTypeInformationSource` trait was removed - now
    `TypeInformation` produced by `SourceFunction` passed to `StreamExecutionEnvironment.addSource` is detected based
    on `TypingResult` (thanks to `GenericTypeInformationDetection`)
    * `BlockingQueueSource.create` takes `ClassTag` implicit parameter instead of `TypeInformation`
    * `EmitWatermarkAfterEachElementCollectionSource.create` takes `ClassTag` implicit parameter instead of `TypeInformation`
    * `CollectionSource`'s `TypeInformation` implicit parameter was removed
    * `EmptySource`'s `TypeInformation` implicit parameter was removed
  * [#6545](https://github.com/TouK/nussknacker/pull/6545) `FlinkSink.prepareTestValue` was replaced by `prepareTestValueFunction` -
    a non-parameter method returning a function. Thanks to that, `FlinkSink` is not serialized during test data preparation.
* `TypingResult` API changes
  * [#6436](https://github.com/TouK/nussknacker/pull/6436) Changes to `TypingResult` of SpEL expressions that are maps or lists:
    * `TypedObjectTypingResult.valueOpt` now returns a `java.util.Map` instead of `scala.collection.immutable.Map`
      * NOTE: selection (`.?`) or operations from the `#COLLECTIONS` helper cause the map to lose track of its keys/values, reverting its `fields` to an empty Map
    * SpEL list expression are now typed as `TypedObjectWithValue`, with the `underlying` `TypedClass` equal to the `TypedClass` before this change, and with `value` equal to a `java.util.List` of the elements' values.
      * NOTE: selection (`.?`), projection (`.!`) or operations from the `#COLLECTIONS` helper cause the list to lose track of its values, reverting it to a value-less `TypedClass` like before the change
  * [#6566](https://github.com/TouK/nussknacker/pull/6566) `TypedObjectTypingResult.fields` are backed by `ListMap` for correct `RowTypeInfo`'s fields order purpose.
    If [#5457](https://github.com/TouK/nussknacker/pull/5457) migrations were applied, it should be a transparent change
    * Removed deprecated  `TypedObjectTypingResult.apply` methods - should be used `Typed.record` factory method
    * `Typed.record` factory method takes `Iterable` instead of `Map`
  * [#6570](https://github.com/TouK/nussknacker/pull/6570) `TypingResult.canBeSubclassOf` generic parameter checking related changes. 
    Generic parameters of `Typed[java.util.Map[X, Y]]`, `Typed[java.util.List[X]]`, `Typed[Array[X]]` were checked as they were either covariant or contravariant.
    Now they are checked more strictly - depending on collection characteristic.
    * `Key` parameters of `Typed[java.util.Map[Key, Value]]` is treated as invariant
    * `Value` parameters of `Typed[java.util.Map[Key, Value]]` is treated as covariant
    * `Element` parameters of `Typed[java.util.List[Element]]` is treated as covariant
    * `Element` parameters of `Typed[Array[Element]]` is treated as covariant
* [#6503](https://github.com/TouK/nussknacker/pull/6503) `FlinkTestScenarioRunner` cleanups
  * `runWithDataAndTimestampAssigner` method was removed. Instead, `timestampAssigner` was added as an optional parameter into `runWithData`
  * new `runWithDataWithType` was added allowing to test using other types than classes e.g. records
* [#6567](https://github.com/TouK/nussknacker/pull/6567) Removed ability to set Flink's [execution mode](https://ci.apache.org/projects/flink/flink-docs-stable/docs/dev/datastream/execution_mode) 
  in sources: `TableSource`, `CollectionSource` and in `FlinkTestScenarioRunner.runWithData` method. Now you can
  configure it under `modelConfig.executionMode` or for test purposes through `FlinkTestScenarioRunnerBuilder.withExecutionMode` method.
* [#6610](https://github.com/TouK/nussknacker/pull/6610) Add flink node context as parameter to BasicFlinkSink.
  Now one can use `FlinkCustomNodeContext` in order to build sink in `BasicFlinkSink#toFlinkFunction` method.

### REST API changes

* [#6437](https://github.com/TouK/nussknacker/pull/6437) Removed deprecated operation to create a scenario:
  POST `/api/processes/{name}/{category}`. POST `/api/processes` should be used instead.
* [#6213](https://github.com/TouK/nussknacker/pull/6213) Improvement: Load resource config only in test context
  * `WithConfig` from `test-utils` modules behaviour changes: now it only parses given config, 
    without resolving reference configs, system env variables etc.

### Configuration changes
* [#6635](https://github.com/TouK/nussknacker/pull/6635) `globalParameters.useTypingResultTypeInformation` parameter was removed.
  Now we always use TypingResultTypeInformation
* [#6797](https://github.com/TouK/nussknacker/pull/6797) `AVRO_USE_STRING_FOR_STRING_TYPE` environment variable
  is not supported anymore - we always use String for String type in Avro. If you didn't set up this
  environment variable, no action is needed


## In version 1.16.3

### Code API changes
* [#6527](https://github.com/TouK/nussknacker/pull/6527) Changes to `TypingResult` of SpEL expressions that are maps or lists:
    * `TypedObjectTypingResult.valueOpt` now returns a `java.util.Map` instead of `scala.collection.immutable.Map`
        * NOTE: selection (`.?`) or operations from the `#COLLECTIONS` helper cause the map to lose track of its keys/values, reverting its `fields` to an empty Map
    * SpEL list expression are now typed as `TypedObjectWithValue`, with the `underlying` `TypedClass` equal to the `TypedClass` before this change, and with `value` equal to a `java.util.List` of the elements' values.
        * NOTE: selection (`.?`), projection (`.!`) or operations from the `#COLLECTIONS` helper cause the list to lose track of its values, reverting it to a value-less `TypedClass` like before the change

## In version 1.16.0

### Code API changes

* [#6184](https://github.com/TouK/nussknacker/pull/6184) Removed `Remote[]` string part from forwarded username for scenario creation and updates. 
  `processes` and `process_versions` tables won't store username with this part anymore in `createdBy` and `modifiedBy` columns.
* [#6053](https://github.com/TouK/nussknacker/pull/6053) Added impersonation mechanism:
    * `OverrideUsername` permission was renamed as `Impersonate` and is now used as a global permission.
    * `AuthManager` is now responsible for authentication and authorization. `AuthenticationResources` handles only plugin specific
      authentication now. This leads to following changes
      in `AuthenticationResources` API:
        * `authenticate()` returns `AuthenticationDirective[AuthenticatedUser]` and not `Directive1[AuthenticatedUser]`
        * `authenticate(authCredentials)` receives `PassedAuthCredentials` parameter type instead of `AuthCredentials`
          as anonymous access is no longer part of `AuthenticationResources` logic
        * `authenticationMethod()` returns `EndpointInput[Option[PassedAuthCredentials]]` instead of `EndpointInput[AuthCredentials]`.
          The `Option[PassedAuthCredentials]` should hold the value that will be passed to the mentioned `authenticate(authCredentials)`.
        * `AuthenticationResources` extends `AnonymousAccessSupport` trait:
          * `AnonymousAccessSupport` has one method `getAnonymousRole()` which returns anonymous role name. If you do not want to have
            an anonymous access mechanism for your authentication method you can extend your `AuthenticationResources`
            implementation with `NoAnonymousAccessSupport` trait.
        * `AuthenticationResources` has a field `impersonationSupport` of type `ImpersonationSupport`:
          * `ImpersonationSupport` is a trait stating whether authentication method supports impersonation.
            If you don't want impersonation support you can assign `NoImpersonationSupport` object to it.
            If you wish to have it - assign `ImpersonationSupported` abstract class to it and
            implement `getImpersonatedUserData(impersonatedUserIdentity)` method which returns required
            user's data for the impersonation by user's `identity`.
    * `AnonymousAccess` extending `AuthCredentials` was renamed to `NoCredentialsProvided`.
      It does not represent anonymous access to the designer anymore but simply represents passing no credentials.
    * `AuthenticationConfiguration` has one additional Boolean property `isAdminImpersonationPossible` which defines whether admin users can be impersonated by users with the `Impersonate` permission.
      The property is set to `false` by default for `BasicAuthenticationConfiguration`, `OAuth2Configuration` and `DummyAuthenticationConfiguration`.
* [#6087](https://github.com/TouK/nussknacker/pull/6087) [#6155](https://github.com/TouK/nussknacker/pull/6155) `DeploymentManager` API changes:
  * `DMRunDeploymentCommand.savepointPath` was replaced by `updateStrategy: DeploymentUpdateStrategy`
    * In places where `savepointPath = None` was passed, the `DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint)` should be passed
    * In places where `savepointPath = Some(path)` was passed, the `DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(StateRestoringStrategy.RestoreStateFromCustomSavepoint(path))` should be passed
  * `DMValidateScenarioCommand.updateStrategy` was added
    * In every place should the `DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint)` should be passed
  * `deploymentSynchronisationSupport` field was added for purpose of synchronisation of statuses. If synchronisation mechanism is not used in context of given DM, 
    you should return `NoDeploymentSynchronisationSupport` object. The synchronisation mechanism is used by `/api/deployments/{deploymentId}/status` endpoint. Other endpoints don't use it.
* [#6249](https://github.com/TouK/nussknacker/pull/6249) `TopicName` trait was introduced and is used in context of specialized topic name (for kafka sources and sinks). Moreover, `UnspecializedTopicName` case class was added and is used in places when the specialization is unknown/not needed. 

### Configuration changes

* [#6082](https://github.com/TouK/nussknacker/pull/6082) Default Influx database was changed from `esp` to `nussknacker_metrics`

### Other changes

## In version 1.15.0

### Code API changes

* [#5609](https://github.com/TouK/nussknacker/pull/5609) [#5795](https://github.com/TouK/nussknacker/pull/5795) [#5837](https://github.com/TouK/nussknacker/pull/5837) [#5798](https://github.com/TouK/nussknacker/pull/5798) Refactoring around DeploymentManager's actions:
  * Custom Actions
    * `CustomAction`, `CustomActionParameter` and `CustomActionResult` moved from `extension-api` to `deployment-manager-api` module
    * `CustomActionResult.req` was removed
    * `CustomAction` was renamed to `CustomActionDefinition`
    * `CustomActionRequest` (from the `extension-api`) was renamed to `CustomActionCommand`
    * `CustomActionRequest` has additional comment parameter (like deploy and cancel actions)
  * Other "action" methods - all methods operating on a scenario (or its deployment) were replaced by case classes and
    one method handling them all: `processCommand(command)`:
    * `validate` - `DMValidateScenarioCommand`
    * `deploy` - `DMRunDeploymentCommand`
    * `cancel` with `deploymentId` argument - `DMCancelDeploymentCommand`
    * `cancel` without `deploymentId` argument - `DMCancelScenarioCommand`
    * `stop` with `deploymentId` argument - `DMStopDeploymentCommand`
    * `stop` without `deploymentId` argument - `DMStopScenarioCommand`
    * `savepoint` - `DMMakeScenarioSavepointCommand`
    * `test` - `DMTestScenarioCommand`
  * "Action type" was renamed to "action name". Loosened the restriction on the name of the action:
    * `ProcessActionType` (enum with fixed values) is replaced with `ScenarioActionName`,  
    * in `ProcessAction` attribute `actionType` renamed to `actionName`
    * in table `process_actions` column `action_type` is renamed to `action_name`
  * `DeploymentManagerDependencies.deploymentService` was splitted into `deployedScenariosProvider` and `actionService`
  * Events renamed: 
    * `OnDeployActionSuccess` renamed to `OnActionSuccess`
    * `OnDeployActionFailed` renamed to `OnActionFailed`
* [#5762](https://github.com/TouK/nussknacker/pull/5762) for the Flink-based TestRunner scenario builder you should replace the last component that was `testResultService` with `testResultSink` 
* [#5783](https://github.com/TouK/nussknacker/pull/5783) Return type of `allowedProcessingMode` method in `Component` trait has been changed to `AllowedProcessingModes` type which is one of:
  * `AllowedProcessingModes.All` in case of all processing modes allowed
  * `AllowedProcessingModes.SetOf(nonEmptySetOfAllowedProcessingModes)` in case only set of processing modes is allowed
* [#5757](https://github.com/TouK/nussknacker/pull/5757) Refactored API around `FlinkSource`
  * Added `StandardFlinkSource` with more granular additional traits replacing the need for `FlinkIntermediateRawSource`
  * Removed `BasicFlinkSource` and `FlinkIntermediateRawSource`. Sources extending these traits should now extend 
    `StandardFlinkSource`. For reference on how to migrate, see changes in `FlinkKafkaSource` or `CollectionSource`
  * Renamed `FlinkSource`'s `sourceStream` method to `contextStream`
  * Removed `EmptySourceFunction`
* [#5757](https://github.com/TouK/nussknacker/pull/5757) Added support for bounded sources and Flink runtime mode in 
  Flink tests
  * `CollectionSource` now takes Flink's `Boundedness` with default `Unbounded` and `RuntimeExecutionMode` with default 
    `None` as a parameters. It's encouraged to set the `Boundedness` to bounded if applicable
  * `Boundedness` and `RuntimeExecutionMode` is also possible to set in `FlinkTestScenarioRunner` in new overloading 
    `runWithData` method

### Configuration changes

* [#5744](https://github.com/TouK/nussknacker/pull/5744) Extracted unbounded stream specific components into separate
  module:
    * Components `periodic`, `union-memo`, `previousValue`, aggregates, joins and `delay` from `base` were moved into
      `base-unbounded` module. They are now built as `flinkBaseUnbounded.jar` under
      `work/components/flink/flinkBaseUnbounded.jar`.
    * Configuration of tumbling windows aggregate offset is changed at the ComponentProvider level:
      `components.base.aggregateWindowsConfig.tumblingWindowsOffset` should now be set
      as `components.baseUnbounded.aggregateWindowsConfig.tumblingWindowsOffset`
    * If you previously specified base component jar explicitly in `modelConfig.classPath`
      as `components/flink/flinkBase.jar` and want to retain the unbounded specific components you need to add
      `components/flink/flinkBaseUnbounded.jar` explicitly.
    * [#5887](https://github.com/TouK/nussknacker/pull/5887) When using a custom DesignerConfig, ensure that long text elements like 'generate file' are positioned in the last row to prevent excessive spacing between elements.

### Other changes

* [#5574](https://github.com/TouK/nussknacker/pull/5574) Removed the support for the pluggable expression languages: `ExpressionConfig.languages` removed
* [#5724](https://github.com/TouK/nussknacker/pull/5724) Improvements: Run Designer locally
  * Introduce `JAVA_DEBUG_PORT` to run the Designer locally with remote debugging capability
  * Removed `SCALA_VERSION`, please use `NUSSKNACKER_SCALA_VERSION` instead of it
* [#5824](https://github.com/TouK/nussknacker/pull/5824) Decision Table parameters rename: 
  * "Basic Decision Table" -> "Decision Table"
  * "Expression" -> "Match condition"
* [#5881](https://github.com/TouK/nussknacker/pull/5881) `nussknacker-interpreter` module was renamed to `nussknacker-scenario-compiler`
* [#5875](https://github.com/TouK/nussknacker/pull/5875) Added configurable idle timeout to Flink Kafka source with the
  default value of 3 minutes. You can configure this timeout in Kafka component config at `idleTimeout.duration` 
  or disable it at `idleTimeout.enabled`. You can learn about idleness
  in [Flink general docs](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/dev/datastream/event-time/generating_watermarks/#dealing-with-idle-sources)
  and [Kafka connector-specific docs](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/kafka/#idleness)
* [#5875](https://github.com/TouK/nussknacker/pull/5875) Removed `useNamingStrategyForConsumerGroupId` feature flag
  allowing for disabling namespaced Kafka consumer groups
* [#5848](https://github.com/TouK/nussknacker/pull/5848): Introduced a new method for handling colors, aimed at simplifying customization. Now, all colors are centrally stored in a single location. Refer to [README.md](https://github.com/TouK/nussknacker/blob/staging/designer/client/README.md#theme-colors-customization) for details on theme colors customization.
* [#5914](https://github.com/TouK/nussknacker/pull/5914) Removed dev-specific configuration files `dev-application.conf` 
  and `dev-tables-definition.sql` from public distribution artifacts

## In version 1.14.0

### Code API changes
* [#5271](https://github.com/TouK/nussknacker/pull/5271) Changed `AdditionalUIConfigProvider.getAllForProcessingType` API to be more in line with FragmentParameter
  * `SingleComponentConfigWithoutId` renamed to `ComponentAdditionalConfig`
  * field `params: Map[String, ParameterConfig]` changed to `parameterConfigs: Map[String, ParameterAdditionalUIConfig]`
  * `ParameterAdditionalUIConfig` is handled analogously to `FragmentParameter` (expect for `valueCompileTimeValidation`, which isn't yet handled)
    *  `ParameterConfig.defaultValue` -> `ParameterAdditionalUIConfig.initialValue`
    *  `ParameterConfig.hintText` -> `ParameterAdditionalUIConfig.hintText`
    *  most of the capabilities of `ParameterConfig.editor` and `ParameterConfig.validators` are covered by `ParameterAdditionalUIConfig.valueEditor` and `ParameterAdditionalUIConfig.valueCompileTimeValidation`
* [#5285](https://github.com/TouK/nussknacker/pull/5285) Changes around scenario id/name fields:
  * `CanonicalProcess.id` of type `String` was replaced by `name` field of type `ProcessName` 
  * `CanonicalProcess.withProcessId` was renamed to `withProcessName` 
  * `ScenarioWithDetails.id` was removed (it had the same value as `name`)
  * `ScenarioWithDetails.processId` changed the type to `Option[ProcessId]` and will have always `None` value
  * `ComponentUsagesInScenario.id` was removed (it had the same value as `name`)
  * `ComponentUsagesInScenario.processId` was removed
  * `ListenerScenarioWithDetails.id` was removed (it had the same value as `name`)
  * `ValidatedDisplayableProcess.id` of type `String` was replaced by `name` field of type `ProcessName`
  * `DisplayableProcess.id` of type `String` was replaced by `name` field of type `ProcessName`, `processName` field is removed
  * deprecated `AsyncExecutionContextPreparer.prepareExecutionContext` was removed
  * `AsyncExecutionContextPreparer.prepare` now takes `ProcessName` instead of `String`
* [#5288](https://github.com/TouK/nussknacker/pull/5288) [#5474](https://github.com/TouK/nussknacker/pull/5474) RemoteEnvironment / ModelMigration changes:
  * `ProcessMigration.failOnNewValidationError` was removed - it wasn't used anywhere anymore
  * `RemoteEnvironment.testMigration` result types changes
    * `shouldFailOnNewErrors` field was removed - it wasn't used anywhere anymore
    * `converted` field was replaced by the `processName` field which was the only information that was used
  * `RemoteEnvironment.migrate` takes `ScenarioParameters` instead of `category`
* [#5361](https://github.com/TouK/nussknacker/pull/5361) `Parameter` has new, optional `labelOpt` field which allows
  to specify label presented to the user without changing identifier used in scenario graph json (`Parameteter.name`)
* [#5356](https://github.com/TouK/nussknacker/pull/5356) Changes in AdditionalUIConfigProvider.getAllForProcessingType now require model reload to take effect.
* [#5393](https://github.com/TouK/nussknacker/pull/5393) [#5444](https://github.com/TouK/nussknacker/pull/5444)
  * Changes around metadata removal from the REST API requests and responses:
    * `DisplayableProcess` was renamed to `ScenarioGraph`
    * `ScenarioGraph` fields that were removed: `name`,  `processingType`, `category` - all these fields already were in `ScenarioWithDetails`
    * `ProcessProperties` field removed: `isFragment` - this field already was in `ScenarioWithDetails`
    * `ScenarioWithDetails` field `json.validationResult` was moved into the top level of `ScenarioWithDetails`
    * `ScenarioWithDetails` field `json` was renamed into `scenarioGraph` and changed the type into `ScenarioGraph`
    * `ValidatedDisplayableProcess` was renamed to `ScenarioGraphWithValidationResult`
    * `ScenarioGraphWithValidationResult` all scenario graph fields were replaced by one `scenarioGraph: DisplayableProcess` field
  * Migration mechanisms (`RemoteEnvironment` and `TestModelMigrations`) use `ScenarioWithDetailsForMigrations` instead of `ScenarioWithDetails`
* [#5424](https://github.com/TouK/nussknacker/pull/5424) Naming cleanup around `ComponentId`/`ComponentInfo`
  * `ComponentInfo` was renamed to `ComponentId`
  * `ComponentId` was renamed to `DesignerWideComponentId`
  * new `ComponentId` is serialized in json to string in format `$componentType-$componentName` instead of separate fields (`name` and `type`)
  * `NodeComponentInfo.componentInfo` was renamed to `componentId`
* [#5438](https://github.com/TouK/nussknacker/pull/5438) Removed sealed trait `CustomActionError`, now `CustomActionResult` is always used
* [#5465](https://github.com/TouK/nussknacker/pull/5465) [#5457](https://github.com/TouK/nussknacker/pull/5457) Typed related changes
  * `CommonSupertypeFinder` shouldn't be created directly anymore - `CommonSupertypeFinder.*` predefined variables should be used instead,
    in most cases just (`CommonSupertypeFinder.Default`)
  * `TypedObjectTypingResult.apply` removed legacy factory method taking `List[(String, TypingResult)]` - should be used variant with `Map` 
  * `TypedObjectTypingResult.apply` removed legacy factory method taking `TypedObjectDefinition` - should be used variant with `Map` 
  * `TypedObjectTypingResult.apply` is deprecated - should be used `Typed.record(...)` instead. It will be removed in further releases
  * `TypedObjectDefinition` was removed 
  * `Typed.empty` was removed, `TypedUnion` now handles only >= 2 types
    * `Typed.apply(vararg...)` was replaced by `Typed.apply(NonEmptyList)` and `Typed.apply(firstType, secondType, restOfTypesVaraarg...)`
      If you have a list of types and you are not sure how to translate it to `TypingResult` you can try to use `Typed.fromIterableOrUnknownIfEmpty`
      but it is not recommended - see docs next to it.
    * `TypedUnion`is not a case class anymore, but is still serializable - If it was used in a Flink state, state will be probably not compatible
  * [#5517](https://github.com/TouK/nussknacker/pull/5517) Legacy `OnFinished` listener-api event was removed
  * [#5474](https://github.com/TouK/nussknacker/pull/5474) `Component` class now need to specify `allowedProcessingModes`. 
    Most of the implementations (`CustomStreamTransformer`, `Service`, `SinkFactory`) has default wildcard (`None`).
    For `SourceFactory` you need to specify which `ProcessingMode` this source support. You have predefined traits:
    `UnboundedStreamComponent`, `BoundedStreamComponent`, `RequestResponseComponent`, `AllProcessingModesComponent`
    that can be mixed into the component
  * [#5474](https://github.com/TouK/nussknacker/pull/5474) Changes around new scenario metadata (aka "parameters"):
    * `ScenarioWithDetails`: added `processingMode` and `engineSetupName` fields
  * [#5522](https://github.com/TouK/nussknacker/pull/5522), [#5521](https://github.com/TouK/nussknacker/pull/5521), [#5519](https://github.com/TouK/nussknacker/pull/5519) `DeploymentManager` API related changes:
    * In the `DeploymentManager`:
      * `DeploymentManager.getProcessState(ProcessIdWithName, Option[ProcessAction])`
        become final. You should implement `resolve` method instead. It does the same, only `List[StatusDetails]` are already determined.
      * Method `DeploymentManager.getProcessStates` signature was changed and now requires an implicit `freshnessPolicy: DataFreshnessPolicy`
      * Trait `AlwaysFreshProcessState` and method `getFreshProcessStates` were removed, instead of it please use `getProcessStates` with `DataFreshnessPolicy.Fresh` policy
      * Managers `FlinkStreamingRestManager` and `FlinkRestManager` require new parameter: `scenarioStateCacheTTL: Option[FiniteDuration]`
    * In the `DeploymentManagerProvider`:
      * New methods were added: `defaultEngineSetupName` and `engineSetupIdentity`. They have default implementations, you should consider to replace them by your own
      * New, overloaded `createDeploymentManager` was added. In the new one most of the parameters were bundled into `DeploymentManagerDependencies` class
        which allows to easier pass these dependencies to delegates. Also, this method returns `ValidateNel[String, DeploymentManager]`.
        You can return errors that will be visible to users e.g. invalid configuration etc. The old one is deleted.
      * Method `createDeploymentManager` signature was changed and now requires new parameter: `scenarioStateCacheTTL: Option[FiniteDuration]`
  * [#5526](https://github.com/TouK/nussknacker/pull/5526) Refactored namespaces:
    * Removed `ObjectNaming` SPI
    * Removed logging when using naming strategy
    * Replaced `ObjectNaming` with single `NamingStrategy` which prepares a name with a prefix from `namespace` key from
      `ModelConfig` or returns the original name if the value is not configured
  * [#5535](https://github.com/TouK/nussknacker/pull/5535) `ProcessingTypeConfig.classpath` contains now raw, `String` entries instead of `URL`.
    The `String` to `URL` converting logic is now inside `ModelClassLoader.apply`
* [#5505](https://github.com/TouK/nussknacker/pull/5505) anonymous access functionality for Tapir-based API 
  * `AuthenticationResources` & `AnonymousAccess` traits were changed to be able to introduce anonymous access feature
  * `AuthCredentials` class was changed too 
* [#5373](https://github.com/TouK/nussknacker/pull/5373)[#5694](https://github.com/TouK/nussknacker/pull/5694) changes related to `Component`s and `LazyParameter`s:
  * `LazyParameter` can be evaluated on request thanks to its `evaluate` method 
  * `Params` data class was introduced as a replacement for runtime parameters values defined as `Map[String, Any]`. `Params` data class, in its extraction methods, assumes that a parameter with the given name exists in the underlying Map.
  * `TypedExpression` was removed from `BaseDefinedParameter` hierarchy in favour of `TypingResult` 
  * `TypedExpression` doesn't depend on `ExpressionTypingInfo` anymore 
  * `ServiceInvoker` refactoring (parameters map was removed, a context is passed to its method)
  * `ProcessListener` interface changed slightly
  * `ParameterWithExtractor` util was replaced with `ParameterDeclaration`.
  * classes renaming:
    * `LazyParameterInterpreter` to `LazyParameterInterpreter`
    * `GenericNodeTransformation` to `DynamicComponent`
    * `SingleInputGenericNodeTransformation` to `SingleInputDynamicComponent`
    * `JoinGenericNodeTransformation` to `JoinDynamicComponent`
    * `JavaGenericTransformation` to `JavaDynamicComponent`
    * `JavaGenericSingleTransformation` to `JavaSingleInputDynamicComponent`
    * `JavaGenericJoinTransformation` to `JavaJoinDynamicComponent`
    * `JavaSourceFactoryGenericTransformation` to `JavaSourceFactoryDynamicComponent`
    * `GenericContextTransformationWrapper` to `DynamicComponentWrapper`
    * `SingleGenericContextTransformationWrapper` to `SingleInputDynamicComponentWrapper`
    * `SourceFactoryGenericContextTransformationWrapper` to `SourceFactoryDynamicComponentWrapper`
    * `JoinGenericContextTransformationWrapper` to `JoinDynamicComponentWrapper`
  * type `NodeTransformationDefinition` (inside `DynamicComponent`) renamed to `ContextTransformationDefinition`
* [#5641](https://github.com/TouK/nussknacker/pull/5641) `PeriodicProcessDeployment`/`DeploymentWithJarData`/`PeriodicProcess` now takes type parameter `CanonicalProcess` or `Unit` to point out whether it contains scenario json.
* [#5656](https://github.com/TouK/nussknacker/pull/5656) `pl.touk.nussknacker.engine.api.expression.Expression#language` method returns `Language` trait instead of `String`
* [#5707](https://github.com/TouK/nussknacker/pull/5707) `ParameterName` data class was introduced. It replaces `String` in whole places where it's used as a parameter name
* [#5754](https://github.com/TouK/nussknacker/pull/5754) Fix for broken encoding mechanism in tests from file with Avro format, revert [0d9b600][https://github.com/TouK/nussknacker/commit/0d9b600]
    * Classes `ResultsCollectingListener`, `TestResults`, `ExpressionInvocationResult`, `ExternalInvocationResult` depend on `T`
    * Classes `TestResults.nodeResults` uses `ResultContext` instead of `Context`
    * Classes `TestResults.exceptions` uses `ExceptionResult` instead of `NuExceptionInfo`
    * Added `variableEncoder` to `ResultsCollectingListenerHolder.registerRun`

### REST API changes
* [#5280](https://github.com/TouK/nussknacker/pull/5280)[#5368](https://github.com/TouK/nussknacker/pull/5368) Changes in the definition API:
  * `/api/processDefinitionData/componentIds` endpoint is removed
  * `/api/processDefinitionData/*` response changes:
    * `services`, `sourceFactories`, `sinkFactories`, `customStreamTransformers` and `fragmentInputs` maps fields were replaced by
      one `components` map with key in format `$componentType-$componentName` and moved into top level of response
    * `typesInformation` field was renamed into `classes`, moved into top level of response 
      and nested `clazzName` inside each element was extracted
    * `componentsConfig` field was removed - now all information about components are available in the `components` field
    * `nodeId` field inside `edgesForNodes` was renamed into `componentId` in the flat `$componentType-$componentName` format
    * `defaultAsyncInterpretation` field was removed
* [#5285](https://github.com/TouK/nussknacker/pull/5285) Changes around scenario id/name fields:
  * `/api/process(Details)/**` endpoints:
    * `id` fields was removed (it had the same value as `name`)
    * `processId` fields return always `null`
    * `.json.id` fields was renamed to `.json.name`
  * `/api/components/*/usages` endpoint:
    * `id` fields was removed (it had the same value as `name`)
    * `processId` fields was removed
  * `/api/processes/**/activity/attachments` - `processId` fields was removed
  * `/api/processes/**/activity/comments` - `processId` fields was removed
  * GET `processes/$name/$version/activity/attachments` - `$version` segment is removed now
* [#5393](https://github.com/TouK/nussknacker/pull/5393) Changes around metadata removal from the REST API requests and responses:
  * `/api/processValidation` was changed to `/api/processValidation/$scenarioName` and changed request type
  * `/api/testInfo/*` was changed to `/api/testInfo/$scenarioName/*` and changed request format regarding code API changes
  * `/api/processManagement/generateAndTest/$samples` was changed to `/api/processManagement/generateAndTest/$scenarioName/$samples`
  * `/api/processesExport/*` was changed to `/api/processesExport/$scenarioName/*` and changed response format regarding code API changes
  * `/api/processes/import/$scenarioName` was changed response into `{"scenarioGraph": {...}, "validationResult": {...}`
  * GET `/api/processes/*` and `/api/processesDetails/*` changed response format regarding code API changes
  * PUT `/api/processes/$scenarioName` was changed request field from `process` to `scenarioGraph`
  * `/api/adminProcessManagement/testWithParameters/$scenarioName` was changed request field from `displayableProcess` to `scenarioGraph`
* [#5424](https://github.com/TouK/nussknacker/pull/5424) Naming cleanup around `ComponentId`/`ComponentInfo`
  * Endpoints returning test results (`/api/processManagement/test*`) return `nodeId` instead of `nodeComponentInfo` now
  * `/processDefinitionData/*` response: field `type` was replaced by `componentId` inside the  path `.componentGroups[].components[]`
* [#5462](https://github.com/TouK/nussknacker/pull/5462) `/api/processes/category/*` endpoint was removed
* [#5474](https://github.com/TouK/nussknacker/pull/5474) POST `/api/processes/$scenarioName/$category?isFragment=$isFragment` resource become deprecated.
  It will be replaced by POST `/processes` with fields: `name`, `isFragment`, `forwardedUserName`, `category`, `processingMode`, `engineSetupName`.
  Three last fields are optional. Please switch to the new API because in version 1.5, old API will be removed.
* POST `/api/nodes/$scenarioName/validation` response for object in `validationErrors` array can have `details` of the error

### Configuration changes
* [#5297](https://github.com/TouK/nussknacker/pull/5297) `componentsUiConfig` key handling change:
  * `$processingType-$componentType-$componentName` format was replaced by `$componentType-$componentName` format
* [#5323](https://github.com/TouK/nussknacker/pull/5323) Support for [the legacy categories configuration format](https://nussknacker.io/documentation/docs/1.12/installation_configuration_guide/DesignerConfiguration/#scenario-type-categories) was removed.
  In the new format, you should specify `category` field inside each scenario type.
* [#5419](https://github.com/TouK/nussknacker/pull/5419) Support for system properties was removed from model configuration
  (they aren't resolved and added to merged configuration)
* [#5474](https://github.com/TouK/nussknacker/pull/5474) You have to ensure that in every scenarioType model's `classPath`, in every
  jar are only components with not colliding processing modes. Also at least one component has defined processing mode other 
  than wildcard.
  On the other hand starting from this version, you can use the same category for many scenarioTypes. You only have to ensure that they 
  have components with other processing modes or other deployment configuration.
* [#5558](https://github.com/TouK/nussknacker/pull/5558) The `processToolbarConfig` toolbar with `type: "process-info-panel"` no longer accepts the `buttons` property. It only display scenario information now. However, a new toolbar with `type: "process-actions-panel"` has been introduced, which does accept the `buttons` property and renders actions similar to the old `type: "process-info-panel"`.

### Helm chart changes
* [#5515](https://github.com/TouK/nussknacker/pull/5515) [#5474](https://github.com/TouK/nussknacker/pull/5474) Helm chart now has two preconfigured scenario types (`streaming` and `request-response`) instead of one (`default`).
  Because of that, scenario created using previous version of helm chart will have invalid configuration in the database.
  To fix that, you have to manually connect to the database and execute sql statement:
  ```sql
    UPDATE processes SET processing_type = 'given-scenario-type' where processing_type = 'default';
  ```

### Other changes
* [#4287](https://github.com/TouK/nussknacker/pull/4287) Cats Effect 3 bump
  Be careful with IO monad mode, we provide an experimental way to create IORuntime for the cat's engine.
* [#5432](https://github.com/TouK/nussknacker/pull/5432) Kafka client, Confluent Schema Registry Client and Avro bump
* [#5447](https://github.com/TouK/nussknacker/pull/5447) JDK downgraded from 17 to 11 in lite runner image for scala 2.13 
* [#5465](https://github.com/TouK/nussknacker/pull/5465) Removed `strictTypeChecking` option and `SupertypeClassResolutionStrategy.Union` used behind it
* [#5517](https://github.com/TouK/nussknacker/pull/5517) Removed legacy mechanism marking scenario finished based on the fact that the last action was deploy and job was finished. 
  The new mechanism leverage deployment id which was introduced in [#4462](https://github.com/TouK/nussknacker/pull/4462) in 1.11 version.
* [#5474](https://github.com/TouK/nussknacker/pull/5474) The mechanism allowing migration between two environments uses by default the new,
  scenario creating API. In case when the secondary environment is in the version < 1.14, you should switch `secondaryEnvironment.useLegacyCreateScenarioApi` flag to on.
* [#5526](https://github.com/TouK/nussknacker/pull/5526) Added namespacing of Kafka consumer group id in both engines.
  If you have namespaces configured, the consumer group id will be prefixed with `namespace` key from model config -
  in that case a consumer group migration may be necessary for example to retain consumer offsets. For gradual
  migration, this behaviour can be disabled by setting `useNamingStrategyInConsumerGroups = false` in `KafkaConfig`.
  Note that the `useNamingStrategyInConsumerGroups` flag is intended to be removed in the future.

## In version 1.13.1 (Not released yet)

### Code API changes
* [#5447](https://github.com/TouK/nussknacker/pull/5447) JDK downgraded from 17 to 11 in lite runner image for scala 2.13

## In version 1.13.0 

### Code API changes
* [#4988](https://github.com/TouK/nussknacker/pull/4988) Method definition `def authenticationMethod(): Auth[AuthCredentials, _]` was changed to `def authenticationMethod(): EndpointInput[AuthCredentials]`
* [#4860](https://github.com/TouK/nussknacker/pull/4860) DeploymentManagerProvider implementations have to implement the method `def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig]` instead of `def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig]`
* [#4919](https://github.com/TouK/nussknacker/pull/4919) Improvement: Support for handling runtime exceptions at FlinkTestScenarioRunner:
  * `TestProcess.exceptions` type changed from `List[ExceptionResult[T]]` to `List[NuExceptionInfo[_ <: Throwable]]`
* [#4912](https://github.com/TouK/nussknacker/pull/4912) Changes in scenario details:
  * `pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails[_]` and `pl.touk.nussknacker.restmodel.processdetails.BasicProcess`
    used in rest resources were merged into `pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails`
  * `pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails[_]`
    used in `pl.touk.nussknacker.ui.listener.services.PullProcessRepository` were moved into `listener-api` and renamed into
    `pl.touk.nussknacker.ui.listener.ListenerScenarioWithDetails`
  * `pl.touk.nussknacker.restmodel.processdetails.ProcessDetails` and `pl.touk.nussknacker.restmodel.processdetails.ValidatedProcessDetails`
    type aliases are not available anymore - you should probably use `ScenarioWithDetails` in these places
  * `pl.touk.nussknacker.restmodel.processdetails.ProcessVersion` was moved into `pl.touk.nussknacker.engine.api.process.ScenarioVersion`
  * `pl.touk.nussknacker.restmodel.processdetails.StateActionsTypes` was moved into `ProcessActionType.StateActionsTypes`
* [#4959](https://github.com/TouK/nussknacker/pull/4959) `listener-api` module become decoupled from `restmodel` module. 
  Some classes were moved to `extensions-api` module to make it possible:
  * `pl.touk.nussknacker.restmodel.displayedgraph` package was renamed to `pl.touk.nussknacker.engine.api.displayedgraph`
  * `pl.touk.nussknacker.restmodel.displayedgraph.ValidatedDisplayableProcess` was moved to `pl.touk.nussknacker.restmodel.validation` package
  * `pl.touk.nussknacker.restmodel.process.ProcessingType` was moved to `pl.touk.nussknacker.engine.api.process` package
  * `pl.touk.nussknacker.restmodel.scenariodetails.ScenarioVersion` was moved to `pl.touk.nussknacker.engine.api.process` package
* [#4745](https://github.com/TouK/nussknacker/pull/4745) Added method `ScenarioBuilder` to create fragments with specified input node id instead of taking a default 
  from fragment id
* [#4745](https://github.com/TouK/nussknacker/pull/4745) Add more errors for scenario and node id validation and change names, messages of existing ones
* [#4928](https://github.com/TouK/nussknacker/pull/4928) [#5028](https://github.com/TouK/nussknacker/pull/5028) `Validator.isValid` method 
  now takes `expression: Expression, value: Option[Any]` instead of `value: String` which was not really value, but expression.
  Straight-forward migration is to change method definition and now use `expression.expression` instead of `value` if your validator depends on raw expression. 
  If validator was doing quasi-evaluation, for example trimming `'` to get string, you can just take `value` and cast it to desired class.
  * `LiteralNumberValidator` is removed, to achieve same result use `CompileTimeEvaluableValueValidator` with parameter of `Number` type,
  * `LiteralIntegerValidator` is considered deprecated and will be removed in the future, to achieve same result use `CompileTimeEvaluableValueValidator` with parameter of `Integer` type,
  * `LiteralRegExpParameterValidator` is renamed to `RegExpParameterValidator`
  * annotation `pl.touk.nussknacker.engine.api.validation.Literal` was renamed to `pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue`
* [#5079](https://github.com/TouK/nussknacker/pull/5079) `AuthCredentials` is moved to `pl.touk.nussknacker.security` in `extensions-api`
* [#5103](https://github.com/TouK/nussknacker/pull/5103) 
  * Values of `ExpressionConfig.globalImports` and `ExpressionConfig.dictionaries` aren't wrapped with `WithCategories` anymore
  * `WithCategories.apply` with `categories` varrag variant is replaced by version with head `category` and tail `categories` varrag
    Previous version was commonly wrongly used as an "object without categories specified" but in fact it was "object with empty categories list"
    which means that object should be never visible. To create "object without categories specified" use, `WithCategories.anyCategory`.
    If you want to pass just a list of categories, use `WithCategories(value, Some(list), SingleComponentConfig.zero)`
* [#5171](https://github.com/TouK/nussknacker/pull/5171) Changes around `ComponentType` values changes:
  * In `ComponentType` values: 
    * Built-in component's artificial component types (`Filter`, `Split`, `Switch`, `Variable`, `MapVariable`)  were replaced by `BuiltIn` type
    * `Processor` and `Enricher` component types were replaced by `Service`
    * `Fragments` was replaced by `Fragment`
    * `CustomNode` was replaced by `CustomComponent`
  * In `ComponentInfo`: Order of parameters swapped + names of them changed `componentType` -> `type`, `componentName` -> `name`
* [#5209](https://github.com/TouK/nussknacker/pull/5209) Now `TestScenarioRunner` doesn't load components from `ComponentProvider`
  automatically. Instead, it loads some predefined set of components. Rest of them you need to pass components using `withExtraComponents` method.
  Components loaded automatically:
  * `TestScenarioRunner.liteBased` - from `base` provider
  * `TestScenarioRunner.kafkaLiteBased` - from `base` and `kafka` providers
  * `TestScenarioRunner.requestResponseBased` - from `base` and `requestResponse` providers
  * `TestScenarioRunner.flinkBased` - from `base` provider
  `TestScenarioRunner` now also uses global variables from default model
* [#4956](https://github.com/TouK/nussknacker/pull/4956) Refactor: Cleanup TestResults
  * Changed signature `DeploymentManager.test` method, and removed `variableEncoder` param
  * Classes `TestResults`, `ExpressionInvocationResult`, `ExternalInvocationResult` don't depend on `T`
  * Classes `NodeResult` is removed. Instead, `Context` is used directly
  * Removed `variableEncoder` from `ResultsCollectingListenerHolder.registerRun`
  * Removed `ResultContext`, please use `Context` instead of it
* [#5240](https://github.com/TouK/nussknacker/pull/5240) Simpler result types in `TestScenarioRunner`
  * `RunResult` and `RunUnitResult` has no generic parameter anymore
  * `RunResult` and its descendants has no `success` method anymore - for `RunListResult` should be used `successes` instead

### REST API changes
* [#4745](https://github.com/TouK/nussknacker/pull/4745) Change `api/properties/*/validation` endpoint request type
  * Replace `processProperties` with `additionalFields`
  * Add `id` field for scenario or fragment id
* [#5039](https://github.com/TouK/nussknacker/pull/5039)[#5052](https://github.com/TouK/nussknacker/pull/5052) Changes in endpoints 
  * `api/parameters/*/suggestions` request
    * `variables` is renamed to `variableTypes` and it should have only local variables now
  * `api/processes/**` response
    * `.json.validationResult.nodeResults.variableTypes` doesn't contain global variables types anymore
  * `api/processDefinitionData/*` response
    * `.processDefinition.globalVariables` is removed
  * `api/parameters/*/validate` request
    * `scenarioName` is removed
    * `processProperties` is removed

### Configuration changes
* [#4860](https://github.com/TouK/nussknacker/pull/4860) In file-based configuration, the field `scenarioTypes.<scenarioType>.additionalPropertiesConfig` is renamed to `scenarioTypes.<scenarioType>.scenarioPropertiesConfig`
* [#5077](https://github.com/TouK/nussknacker/pull/5077) In SQL enricher configuration, `connectionProperties` was changed to `dataSourceProperties`

### Other changes
* [#4901](https://github.com/TouK/nussknacker/pull/4901) Improvements TestScenarioRunner:
  * Changes at `FlinkProcessRegistrar.register` passing `resultCollector` instead of `testRunId`
* [#5033](https://github.com/TouK/nussknacker/pull/5033) Scala 2.13 was updated to 2.13.12, you may update your `flink-scala-2.13` to 1.1.1
  (it's not required, new version is binary-compatible)
* [#5059](https://github.com/TouK/nussknacker/pull/5059) [#5100](https://github.com/TouK/nussknacker/pull/5100) Categories configuration doesn't allow configuring multiple categories for the same scenario type. 
  If you have such a case, you have to extract another scenario types and assign each category to each scenario type.
  Because of this change configuration of categories was also removed from Components configuration
* [#4953](https://github.com/TouK/nussknacker/pull/4953) Stricter validation in base components:
  * Boolean expressions in `Switch` and `Filter` nodes are required not null values
  * Variable values in `MapVariable`, `FragmentOutput` and `Variable` are mandatory
  * Field names in `MapVariable`, `FragmentOutput` are required to be unique
* [#4698](https://github.com/TouK/nussknacker/pull/4698) Due to change in program argument encoding all scheduled batch
  scenarios handled by periodic DM must be cancelled before upgrade

## In version 1.12.6

### Other changes
* [#5447](https://github.com/TouK/nussknacker/pull/5447) JDK downgraded from 17 to 11 in lite runner image for scala 2.13

## In version 1.12.x

### Code API changes
* [#4574](https://github.com/TouK/nussknacker/pull/4574) Improvements: at `KafkaClient` and `RichKafkaConsumer` in kafka-test-utils
  * `RichKafkaConsumer.consumeWithJson` needs json decoder
  * removed `RichKafkaConsumer.consumeWithConsumerRecord`, use `RichKafkaConsumer.consumeWithJson` instead of it 
  * `RichKafkaConsumer.defaultSecondsToWait` renamed to `RichKafkaConsumer.DefaultSecondsToWait`
  * `KafkaClient.sendMessage` accepts generic content with json encoder
* [#4583](https://github.com/TouK/nussknacker/pull/4583) `DeploymentManager` has new variants of method `cancel` and `stop`
  taking `DeployomentId` next to `ProcessName`. They will be used with batch processing mechanism (periodic DM) so it is necessary
  to implement it only if your DM will be wrapped by `PeriodicDeploymentManager`
* [#4685]((https://github.com/TouK/nussknacker/pull/4685)) In `AuthenticationResources` trait it was added two new
  methods that have to be implemented in the child classes: `def authenticationMethod(): Auth[AuthCredentials, _]` and
  `def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]]`. The first one tells what
  authentication method will be used (it's for Tapir-based API purposes) and the latter one is the authentication
  action itself. The `def authenticate(): Directive1[AuthenticatedUser]` should be treated as deprecated. It's 
  used in the NU APIs which are still Akka HTTP-based. When we get rid of Akka HTTP, it will be removed.
* [#4762](https://github.com/TouK/nussknacker/pull/4762) Rename `RegExpParameterValidator` to `LiteralRegExpParameterValidator`

### REST API changes
* [#4697](https://github.com/TouK/nussknacker/pull/4697) Change `api/parameters/*/validate` and `api/parameters/*/suggestions` endpoints.
  * Use `processingType` instead of `processName`
  * Add `scenarioName` parameter to `ParametersValidationRequest` used in `api/parameters/*/validate`
* [#4602](https://github.com/TouK/nussknacker/pull/4602) Cleaning subprocess usages after NU 1.11 release
  * Removed isSubprocess endpoint param, use isFragment from now on.
  * Removed backward compatibility for subprocess fields.

### Other changes
* [#4492](https://github.com/TouK/nussknacker/pull/4492) Allow testing fragments using ad-hoc testing method.
  By default, NU enables that feature but if you have some custom `processToolbarConfig` settings then
  You would like to remove `hidden: { fragment: true }` flag for `type: "test-with-form"`, `type: "test-counts"` 
  and `type: "test-hide"` inside `processToolbarConfig -> "test-panel"`.

## In version 1.11.0

### Code API changes
* [#4295](https://github.com/TouK/nussknacker/pull/4295) `DeploymentManager.getProcessState(ProcessName)`
  method now returns `List[StatusDetails]` instead of `Option[StatusDetails]`. If you are a consumer of this API and want
  to have the same behavior as previously, you can use `InconsistentStateDetector.extractAtMostOneStatus` method for that.
  Notice, that in the future visibility of this method may be changed into private.
* [#4462](https://github.com/TouK/nussknacker/pull/4462) `StatusDetails.deploymentId` field changed type into`Option[DeploymentId]`.
  It contains, internal designer's deployment id. It is used to assign deployment on runtime side with deployment action 
  on designer side in periodic DM for purpose of correct status synchronization. If you want to make it filled, 
  you should pass the correct value in `DeploymentData.deploymentId`. Current value of `deploymentId: Option[ExternalDeploymentId]`
  was moved into `externalDeploymentId` field. `ProcessAction` has a new field - `id`. 
  `ProcessStateDefinitionManager.processState` variant of methods with multiple parameters was removed - you should
  use `ProcessStateDefinitionManager.processState(StatusDetails)` variant instead.
* [#4353](https://github.com/TouK/nussknacker/pull/4353) Removed isCancelled/isDeployed flags based on `ProcessAction`, `ProcessAction.action` renamed to actionType. Trait `Process` is removed.
* [#4484](https://github.com/TouK/nussknacker/pull/4484) `ProcessIdWithName` moved from package `pl.touk.nussknacker.restmodel.process` to `pl.touk.nussknacker.engine.api.process`
  `DeploymentManager.getProcessState(ProcessName, Option[ProcessAction])` method now takes `ProcessIdWithName` as an argument instead of `ProcessName`. 
  The same with `PostprocessingProcessStatus.postprocess`.

### REST API changes
* [#4454](https://github.com/TouK/nussknacker/pull/4454) Rename 'subprocess' to 'fragment' along with all endpoints (with backward compatibility).
  * `isSubprocess` query parameter is renamed to `isFragment`. `isSubprocess` will be removed in 1.12.0
* [#4462](https://github.com/TouK/nussknacker/pull/4462) Process state API returns `externalDeploymentId` instead of `deploymentId`.

### Other changes
* [#4514](https://github.com/TouK/nussknacker/pull/4514) `AkkaHttpBackend` in designer is replaced by `AsyncHttpClientFutureBackend`.
  To use custom http client configuration use `ahc.properties` file and make sure it is available in the classpath.

## In version 1.10.0

### Code API changes
* [#4352](https://github.com/TouK/nussknacker/pull/4352) `TypedObjectTypingResult#fields` are no longer ordered, fields will be sorted for presentation (see `TypedObjectTypingResult#display`)
* [#4294](https://github.com/TouK/nussknacker/pull/4294) `HttpRemoteEnvironmentConfig` allows you to pass flag `passUsernameInMigration` - (default true).
  When set to true, migration attaches username in the form of `Remote[userName]` while migrating to secondary environment. To use the old migration endpoint, set to false.
* [#4278](https://github.com/TouK/nussknacker/pull/4278) Now expression compiler and code suggestions mechanism are reusing the same
  types extracted based on model. Before the change types in compiler were lazily extracted. Because of this change, some expressions
  can stop to compile. You may need to add `WithExplicitTypesToExtract` to some of yours `SourceFactory` implementations.
  See extending classes for examples on how to implement it.
* [#4290](https://github.com/TouK/nussknacker/pull/4290) Renamed predicates used in `ClassExtractionSettings`:
  * `ClassMemberPatternPredicate` renamed to `MemberNamePatternPredicate`
  * `AllMethodNamesPredicate` renamed to AllMembersPredicate
* [#4299](https://github.com/TouK/nussknacker/pull/4299), [#4300](https://github.com/TouK/nussknacker/pull/4300)
  `StateStatus` is identified by its name. `ProcessState` serialization uses this name as serialized state value.  
  Sealed trait `StateStatus` is unsealed, all members are replaced by corresponding `SimpleStateStatus` state definitions,
  custom statuses are defined within each `ProcessStateDefinitionManager`.
  `ProcessAction` is moved from restmodel to extensions-api, package engine.api.deployment.
* [#4339](https://github.com/TouK/nussknacker/pull/4339) Improvements: Don't fetch state for archived/unarchived scenario, return computed based on last state action
  At BaseProcessDetails we provide lastStateAction field which can have an influence on the presented state of the scenario. 
  We currently use it to distinguish between cancel / not_deployed and to detect inconsistent states between the designer and engine
* [#4302](https://github.com/TouK/nussknacker/pull/4302) State inconsistency detection was moved from designer to DeploymentManager.
  `DeploymentManager.getProcessState` for internal purposes returns `Option[StatusDetails]` which is based on job status from deployment manager (instead of `Option[ProcessState]` which contains UI info).
  There is separate `getProcessState` that returns `ProcessState` which is a status from engine resolved via `InconsistentStateDetector` and formatted with UI-related details.
  `PeriodicProcessEvent` uses `StatusDetails` instead of `ProcessState`.
  Constants defined in `ProblemStateStatus` are renamed to match UpperCamelCase formatting.
* [#4350](https://github.com/TouK/nussknacker/pull/4350) `StateStatus.isDuringDeploy`, `StateStatus.isFinished`, `StateStatus.isFailed`, `StateStatus.isRunning`, 
  `ProcessState.isDeployed` methods were removed. Instead, you should compare status with specific status.
* [#4357](https://github.com/TouK/nussknacker/pull/4357) Changed structure of `MetaData` in `CanonicalProcess` - `TypeSpecificData` automatically migrated to `ProcessAdditionalFields`
  - Example MetaData structure before migration:
  ```json
  {
    "id": "scenarioName",
    "typeSpecificData": {
      "parallelism": 1,
      "spillStateToDisk": true,
      "checkpointIntervalInSeconds": null,
      "type": "StreamMetaData"
    },
    "additionalFields": {
      "description": null,
      "properties": {
        "someCustomProperty": "someCustomValue"
      }
    }
  }
  ```
  - Example MetaData structure after migration:
  ```json
  {
    "id": "scenarioName",
    "additionalFields": {
      "description": null,
      "properties": {
        "parallelism" : "1",
        "spillStateToDisk" : "true",
        "useAsyncInterpretation" : "",
        "checkpointIntervalInSeconds" : "",
        "someCustomProperty": "someCustomValue"
      },
      "metaDataType": "StreamMetaData"
    }
  }
  ```
  
### Configuration changes
* [#4283](https://github.com/TouK/nussknacker/pull/4283) For OIDC provider, `accessTokenIsJwt` config property is introduced, with default values `false`.
  Please mind, that previous Nussknacker versions assumed its value is true if `authentication.audience` was defined.
* [#4357](https://github.com/TouK/nussknacker/pull/4357) `TypeSpecificData` properties are now be configured in `DeploymentManagerProvider`:
  - Main configuration is done through `additionalPropertiesConfig` like other additional properties
  - Initial values overriding defaults from the main configuration can be set in `metaDataInitializer`

### Other changes
* [#4305](https://github.com/TouK/nussknacker/pull/4305) `scala-compiler` and `scala-reflect` are now included in `flink-scala`,
  so you can simplify your deployment by removing them and updating to new
  ([`flink-scala` JAR](https://repo1.maven.org/maven2/pl/touk/flink-scala-2-13_2.13/1.1.0/flink-scala-2-13_2.13-1.1.0-assembly.jar))
  (this doesn't introduce any functional changes)

### REST API changes
* [#4350](https://github.com/TouK/nussknacker/pull/4350) `delete` action is available only for archived scenarios. Before the change it was checked that scenario is not running

## In version 1.9.0

### Code API changes
* [#4030](https://github.com/TouK/nussknacker/pull/4030) Changes for purpose of local testing of designer with other urls than on engine side
  * `ProcessingTypeConfig.modelConfig` now contains `ConfigWithUnresolvedVersion` instead of `Config`. Old `Config` value is in `ConfigWithUnresolvedVersion.resolved`
  * `ModelConfigLoader.resolveInputConfigDuringExecution` takes `ConfigWithUnresolvedVersion` instead of `Config`. Use `ConfigWithUnresolvedVersion.apply`
    for easy transition between those classes
* [#3997](https://github.com/TouK/nussknacker/pull/3997) Removal of obsolete `subprocessVersions`. It affects `MetaData`, `ProcessMetaDataBuilder` and `DisplayableProcess` properties. 
* [#4122](https://github.com/TouK/nussknacker/pull/4122), [#4132](https://github.com/TouK/nussknacker/pull/4132), [#4179](https://github.com/TouK/nussknacker/pull/4179), [#4189](https://github.com/TouK/nussknacker/pull/4189)
  * Use `ProcessStateDefinitionManager.stateDefinitions` to describe states: 1) their default properties 2) how the states are presented in filter-by-status options.  
    (see an example of basic definitions in `SimpleProcessStateDefinitionManager` and `SimpleStateStatus`).
  * State defaults and allowed actions are moved to `SimpleStateStatus`, `FlinkStateStatus`, `PeriodicStateStatus`, `EmbeddedStateStatus` and `K8sStateStatus`
    from corresponding state-definition-managers (see example `FlinkProcessStateDefinitionManager`).
  * Type `CustomStateStatus.name` renamed to `StatusName`
  * `ProcessResources` exposes new endpoint `/api/procecesses/statusDefinitions`
  * Within the base set of statuses used in Embedded, Flink, K8 and Periodic mode (`SimpleStateStatus`), statuses `Failing`, `Failed`, `Error`, `Warning`, `FailedToGet` and `MulipleJobsRunning`
    are replaced by one `ProblemStateStatus` which is parametrized by specific message. `ProblemStateStatus` provides several builder methods, one for each corresponding removed state.
    Those builders allow to preserve the exact moments when each state appears in the scenario lifecycle.
  * Displayed tooltip and description of `ProblemStateStatus` have the same value.
  * Removed `SimpleStateStatus.Unknown`
  * Removed status `FailedStateStatus`. Use `ProblemStateStatus` instead.
  * Status configuration for icon, tooltip and description is obligatory.
* [#4104](https://github.com/TouK/nussknacker/pull/4104) `DeploymentManager.findJobStatus` was renamed to `getProcessState`. New `DataFreshnessPolicy`
  parameter was added. Returned type was changed to `WithDataFreshnessStatus[T]` where `T` is the previous value and `cached: Boolean` is additional
  information that should be provided.
  If you provide `DeploymentManager` which communicate remotely with some service, and you want to use standard build-in caching for `ProcessState`,
  wrap your `DeploymentManager` using `CachingProcessStateDeploymentManager.wrapWithCachingIfNeeded` in your `DeploymentManagerProvider`.
  Thanks to that, caching will be handled as expected, and your `DeploymentManager` just should extend `AlwaysFreshProcessState`
  which provide the same interface as the previous one, with only method name changed.
  Especially, when you use 'PeriodicDeploymentManagerProvider', `delegate` should already return `DeploymentManager` wrapped by caching mechanism.
* [#4131](https://github.com/TouK/nussknacker/pull/4131) `Parameter.defaultValue` now holds `Option[Expression]` instead of `Option[String]`. You have to wrap a `String` with `Expression.spel()`
* [#4224](https://github.com/TouK/nussknacker/pull/4224) If you're using Flink with Nussknacker built with Scala 2.13, add this 
  [jar](https://repo1.maven.org/maven2/pl/touk/flink-scala-2-13_2.13/1.0.0/flink-scala-2-13_2.13-1.0.0-assembly.jar) in your Flink installation to `lib` dir. It's our implementation of `org.apache.flink.runtime.types.FlinkScalaKryoInstantiator` 
  (sources are [here](https://github.com/TouK/flink-scala-2.13)) which is needed to properly (de)serialize Flink state when using scala 2.13. 
  Hopefully, it's temporary solution, until Flink becomes really scala-free and gets rid of this `FlinkScalaKryoInstantiator` class or allows to have it in the job code (not Flink libs).
* [#4190](https://github.com/TouK/nussknacker/pull/4190) - introduced possibility to configure offset in `FlinkComponentsProvider` (`components.base.aggregateWindowsConfig.tumblingWindowsOffset`, by default 0) for aggregates with tumbling windows. You might want to set it up, 
  especially when you want your daily windows to be aligned according to your timezone if it's not UTC. See example in Flink [docs](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/operators/windows/#tumbling-windows)

### Other changes

* [#4122](https://github.com/TouK/nussknacker/pull/4122), [#4132](https://github.com/TouK/nussknacker/pull/4132) Changes in state definitions:
  * In `ProcessStateDefinitionManager` default behaviour of methods `statusTooltip`, `statusDescription` and `statusIcon` is to return default properties defined via `stateDefinitions`.
    It is not necessary to override those methods when all definitions have fixed default properties.
  * To introduce custom status properties, extensions to basic definitions, use `OverridingProcessStateDefinitionManager`.
  * `OverridingProcessStateDefinitionManager` allows to specify delegate (previously only `SimpleProcessStateDefinitionManager` was available) and custom state definitions.
  * Parameter `delegate` in `OverridingProcessStateDefinitionManager` has no default value, it should be provided explicitly.
  * There is additional validation when all processing types are reloaded from configuration: check if all processing types state definitions configuration is correct.
    (see comment in `ProcessStateDefinitionService`)
* [#3997](https://github.com/TouK/nussknacker/pull/3997) Due to removal of deprecated field `subprocessVersions` migration of scenarios from and to remote environment, for 
  Nussknacker version 1.9.0+ and older will not be possible. Use export and import as a workaround while working between older and newer version.  


### Other changes
* [#3675](https://github.com/TouK/nussknacker/pull/3675) Improvements: Normalize kafka components params name
  * Renamed kafka topic param name from `topic` to `Topic`
  * Renamed kafka value param name from `value` to `Value`

## In version 1.8.0

### Scenario authoring changes
* [#3924](https://github.com/TouK/nussknacker/pull/3924) 
  * Fixup: `{}` is now interpreted as "allow everything schema", not as "object schema". Objects schemas have to have declared `"type": "object"`.
  * Unknown is now allowed on sinks in both validation modes if output schema is "everything allowed schema".

### Code API changes
* [#3924](https://github.com/TouK/nussknacker/pull/3924) - changes to `SwaggerTyped` hierarchy
  * `SwaggerMap(valuesType)` -> `SwaggerObject(Map.empty, additionalProperties = AdditionalPropertiesEnabled(valuesType))`
  * `AdditionalPropertiesSwaggerTyped` -> `AdditionalPropertiesEnabled`
  * `AdditionalPropertiesWithoutType` -> `AdditionalPropertiesEnabled(SwaggerAny)`
  * `SwaggerRecursiveSchema/SwaggerUnknownFallback` -> `SwaggerAny`

### Other changes
* [#3835](https://github.com/TouK/nussknacker/pull/3835) Removed Signals and QueryableState. This change affects:
  * Configuration
  * Components and DeploymentManager API
  * REST API
* [#3823](https://github.com/TouK/nussknacker/pull/3823), [#3836](https://github.com/TouK/nussknacker/pull/3836), [#3843](https://github.com/TouK/nussknacker/pull/3843) -
  scenarios with multiple sources can be tested from file
  * `TestDataGenerator#generateTestData` returns JSON test records instead of raw bytes. Test records are serialized to a file by designer
    Test record can optionally contain timestamp which is used to sort records generated by many sources  
  * `TestDataParser` was replaced with `TestRecordParser` that turns a single JSON test record into a source record
  * `TestData.newLineSeparated` helper was removed. Scenario test records have to be created explicitly. Each scenario test record has assigned source
  * `DeploymentManager#test` takes `ScenarioTestData` instead of `TestData`
  * Designer configuration `testDataSettings.testDataMaxBytes` renamed to `testDataMaxLength`
* [#3916](https://github.com/TouK/nussknacker/pull/3916) Designer configuration `environmentAlert.cssClass` renamed to `environmentAlert.color`
* [#3922](https://github.com/TouK/nussknacker/pull/3922) Bumps: jwks: 0.19.0 -> 0.21.3, jackson: 2.11.3 -> 2.13.4
* [#3929](https://github.com/TouK/nussknacker/pull/3929) From now, `SchemaId` value class is used in every place 
  where schema id was represented as an Int. For conversion between `SchemaId` and `Int` use `SchemaId.fromInt` and `SchemaId.asInt`.
  Use `ConfluentUtils.toSchemaWithMetadata` instead of `SchemaWithMetadata.apply` for conversion between Confluent's `SchemaMetadata` and ours `SchemaWithMetadata`.
* [#3948](https://github.com/TouK/nussknacker/pull/3948) Now, we are less dependent from Confluent schema registry.
  To make it possible, some kafka universal/avro components refactors were done. Most important changes in public API:
  * ConfluentSchemaBasedSerdeProvider.universal was replaced by UniversalSchemaBasedSerdeProvider.create
  
  Some other, internal changes:
  * Non-confluent classes renamed and moved to desired packages
  * Extracted new class: SchemaIdFromMessageExtractor to make Confluent logic explicit and moved to top level
  * Extracted SchemaValidator to make Confluent logic explicit and be able to compose
  * Some renames: ConsumerRecordUtils -> KafkaRecordUtils
  * RecordDeserializer -> AvroRecordDeserializer (also inheritance replaced by composition)
  * (De)SerializerFactory - easier abstractions
  * ConfluentSchemaRegistryFactory is not necessary now - removed

## In version 1.7.0 

### Scenario authoring changes
* [#3701](https://github.com/TouK/nussknacker/pull/3701) Right now access in SpEL to not existing field on TypedMap won't throw exception, just will return `null`
* [#3727](https://github.com/TouK/nussknacker/pull/3727) Improvements: Change RR Sink validation way:
  * Added param `Value validation mode` at RR response component
  * We no longer support `nullable` param from Everit schema. Nullable schema are supported by union with null e.g. `["null", "string"]

### Configuration changes
* [#3768](https://github.com/TouK/nussknacker/pull/3768) `request-response-embedded` and `streaming-lite-embedded` 
  DeploymentManager types where replaced by one `lite-embedded` DeploymentManager type with two modes: `streaming` and `request-response`
  like it is done in `lite-k8s` case

### Code API changes
* [#3560](https://github.com/TouK/nussknacker/pull/3560), [#3595](https://github.com/TouK/nussknacker/pull/3595) 
   Remove dependency on `flink-scala`. In particular: 
  * Switched from using `scala.DataStream` to `datastream.DataStream`. Some tools exclusive to scala datastreams are available in `engine.flink.api.datastream`
  * Scala based `TypeInformation` derivation is no longer used, for remaining cases `flink-scala-utils` module is provided (probably will be removed in the future)
* [#3680](https://github.com/TouK/nussknacker/pull/3680) `SubprocessRef::outputVariableNames` type is changed from `Option[Map[String,String]]` with default None, to `Map[String,String]` with default `Map.empty`
* [#3692](https://github.com/TouK/nussknacker/pull/3692) Rename `mockedResult` to  `externalInvocation` in test results collectors.
* [#3606](https://github.com/TouK/nussknacker/pull/3606) Removed nussknacker-request-response-app. As a replacement you can use:
  * nussknacker-request-response-app in version <= 1.6
  * Lite K8s engine with request-response processing mode
  * `lite-embedded` Deployment Manager with request-response processing mode
* [#3610](https://github.com/TouK/nussknacker/pull/3610) Removed deprecated code. For details see changes in pull request.
* [#3607](https://github.com/TouK/nussknacker/pull/3607) Request-response jsonSchema based encoder:
  * ValidationMode moved to package `pl.touk.nussknacker.engine.api.validation` in `nussknacker-components-api`
  * BestEffortJsonSchemaEncoder moved to package `pl.touk.nussknacker.engine.json.encode` in `nussknacker-json-utils`
* [#3738](https://github.com/TouK/nussknacker/pull/3738) Kafka client libraries upgraded to 3.2.3. If using older Flink version,
  make sure to use 2.8.x client libraries. For Flink versions 1.15.0-1.15.2 include also [fixed KafkaMetricWrapper](https://github.com/TouK/nussknacker/pull/3738/files#diff-36f2a26ed5f3b58f5a8e758d4368e42d44413b5b74207df4cd65594c676682f9) 
* [#3668](https://github.com/TouK/nussknacker/pull/3668) Method `runWithRequests` of `RequestResponseTestScenarioRunner` (returned by `TestScenarioRunner.requestResponseBased()`)
  now returns `ValidatedNel` with scenario compilation errors instead of throwing exception in that case 

### REST API changes

* [#3576](https://github.com/TouK/nussknacker/pull/3576) `/processes` endpoint without query parameters returns all scenarios - the previous behaviour was to return only unarchived ones.
  To fetch only unarchived scenarios `isArchived=false` query parameter has to be passed.

### Other changes
* [#3824](https://github.com/TouK/nussknacker/pull/3824) Due to data serialization fix, Flink scenarios using Kafka sources with schemas may be incompatible and may need to be restarted with clean state. 

## In version 1.6.0
* [#3440](https://github.com/TouK/nussknacker/pull/3440) Feature: allow to define fragment's outputs
  * Right now using fragments in scenario is changed. We have to provide each outputName for outputs defined in fragment.

### Scenario authoring changes
* [#3370](https://github.com/TouK/nussknacker/pull/3370) Feature: scenario node category verification on validation
  From now import scenario with nodes from other categories than scenario category will be not allowed.
* [#3436](https://github.com/TouK/nussknacker/pull/3436) Division by zero will cause validation error. Tests that rely on `1/0` to generate exceptions should have it changed to code like `1/{0, 1}[0]`
* [#3473](https://github.com/TouK/nussknacker/pull/3473) JsonRequestResponseSinkFactory provides also 'raw editor', to turn on 'raw editor' add `SinkRawEditorParamName -> "true"`
* [#3608](https://github.com/TouK/nussknacker/pull/3608) Use `ZonedDateTime` for `date-time` JsonSchema format, `OffsetTime` for `time` format. 

### Code API changes
* [#3406](https://github.com/TouK/nussknacker/pull/3406) Migration from Scalatest 3.0.8 to Scalatest 3.2.10 - if necessary, see the Scalatest migration guides, https://www.scalatest.org/release_notes/3.1.0 and https://www.scalatest.org/release_notes/3.2.0
* [#3431](https://github.com/TouK/nussknacker/pull/3431) Renamed 
  `helper-utils` to `default-helpers`, separated `MathUtils` from
  `components-utils` to `math-utils`, removed dependencies from `helper-utils`
* [#3420](https://github.com/TouK/nussknacker/pull/3420) `DeploymentManagerProvider.typeSpecificInitialData` takes deploymentConfig `Config` now
* [#3493](https://github.com/TouK/nussknacker/pull/3493), [#3582](https://github.com/TouK/nussknacker/pull/3582) Added methods `DeploymentManagerProvider.additionalPropertiesConfig`, `DeploymentManagerProvider.additionalValidators`
* [#3506](https://github.com/TouK/nussknacker/pull/3506) Changed `LocalDateTime` to `Instant` in `OnDeployActionSuccess` in `listener-api`
* [#3513](https://github.com/TouK/nussknacker/pull/3513) Replace `EspProcess` with `CanonicalProcess` in all parts of the API except for the compiler.
* [#3545](https://github.com/TouK/nussknacker/pull/3545) `TestScenarioRunner.flinkBased` should be used instead of `NuTestScenarioRunner.flinkBased`. Before this, you need to `import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._` 
* [#3386](https://github.com/TouK/nussknacker/pull/3386) Changed `CustomProcessValidator` `validate` method.
  It now receives `CanonicalProcess` instead of `DisplayableProcess` and returns `ValidatedNel[ProcessCompilationError, Unit]`
  instead of `ValidationResult`. Moved `CustomProcessValidator` from module `nussknacker-restmodel` in package `validation` to `nussknacker-extensions-api`. 
* [#3586](https://github.com/TouK/nussknacker/pull/3586) Module `nussknacker-ui` was renamed to `nussknacker-designer`, `ui.conf` was renamed to `designer.conf`, `defaultUiConfing.conf` renamed to `defaultDesignerConfig.conf`


### REST API changes                   
* [#3506](https://github.com/TouK/nussknacker/pull/3506) Dates returned by REST API (createdAt, modifiedAt, createDate) are now returned in Zulu time, with timezone indication. This affects e.g. `/api/procecesses`, `/api/processes/{scenarioId}`, `/api/processes/{scenarioId}/activity` 
* [#3542](https://github.com/TouK/nussknacker/pull/3542) Node additional info path renamed from `nodes/{scenarioId}/additionalData` to `nodes/{scenarioId}/additionalInfo`

### Scenario API changes
* [#3471](https://github.com/TouK/nussknacker/pull/3471), [#3553](https://github.com/TouK/nussknacker/pull/3553) `RequestResponseMetaData(path)` is changed to `RequestResponseMetaData(slug)`.
  `V1_033__RequestResponseUrlToSlug` migration is ready for that, the change also applies to Scenario DSL.
* [#3513](https://github.com/TouK/nussknacker/pull/3513) Scenario DSL returns `CanonicalProcess` instead of `EspProcess`. 
* [#3630](https://github.com/TouK/nussknacker/pull/3630) `SubprocessOutput` changed to `SubprocessUsageOutput`, changes in `OutputVar` definition               

### Configuration changes
* [#3425](https://github.com/TouK/nussknacker/pull/3425) Deployment Manager for `request-response-embedded` configuration parameters changed:
  * `interface` -> `http.interface`
  * `port` -> `http.port`
  * `definitionMetadata` -> `request-response.definitionMetadata`
* [#3502](https://github.com/TouK/nussknacker/pull/3502) Refactor of `KafkaProperties`: `kafkaAddress` property has been deprecated. Please provide `kafkaProperties."bootstrap.servers"` instead

### Other changes
* [#3441](https://github.com/TouK/nussknacker/pull/3441) Updated Flink 1.14.5 -> 1.15.2. Some Flink artefacts no longer have Scala version. Test using Flink may need to disable checkpointing or reduce time between checkpoints to prevent timeouts or long waits for tasks to finish.
                                   
## In version 1.5.0

### Configuration changes

* [#2992](https://github.com/TouK/nussknacker/pull/2992) deploySettings changed to deploymentCommentSettings, now when specified require you to also specify field validationPattern, specifying exampleComment is optional.
* commentSettings fields modified. matchExpression changed to substitutionPattern, link changed to substitutionLink.
* [#3165](https://github.com/TouK/nussknacker/pull/3165) Config is not exposed over http (GET /api/app/config/) by default. To enable it set configuration `enableConfigEndpoint` to `true`.
* [#3223](https://github.com/TouK/nussknacker/pull/3223) OAuth2 configuration `defaultTokenExpirationTime` changed to `defaultTokenExpirationDuration`
* [#3263](https://github.com/TouK/nussknacker/pull/3263) Batch periodic scenarios carry processing type to distinguish scenarios with different categories.
  For existing scenarios processing type is migrated to `default`. Set `deploymentManager.processingType` to `default`
  or update periodic scenarios table with actual processing type value - ideally it should be same value as the periodic engine key in `scenarioTypes`.

### Code API changes

* [#2992](https://github.com/TouK/nussknacker/pull/2992) OnDeployActionSuccess in ProcessChangeEvent now requires instance of Option[Comment] instead of Option[String] as parameter with deploymentComment information. Added abstract class Comment in listener-api.
* [#3136](https://github.com/TouK/nussknacker/pull/3136) Improvements: Lite Kafka testkit
  * `ConfluentUtils.serializeRecordToBytesArray` replaced by `ConfluentUtils.serializeDataToBytesArray`
  * `ConfluentUtils.deserializeSchemaIdAndRecord` replaced by `ConfluentUtils.deserializeSchemaIdAndData`
* [#3178](https://github.com/TouK/nussknacker/pull/3178) Improvements: more complex test scenario runner result:
  * Right now each method from `TestScenarioRunner` should return `ValidatedNel[ProcessCompilationError, RunResult[R]]` where:
    * Invalid is representation of process compilation errors
    * Valid is representation of positive and negative scenario running result
* [#3255](https://github.com/TouK/nussknacker/pull/3255) `TestReporter` util class is safer to use in parallel tests, methods require passing scenario name
* [#3265](https://github.com/TouK/nussknacker/pull/3265) [#3288](https://github.com/TouK/nussknacker/pull/3288) [#3297](https://github.com/TouK/nussknacker/pull/3297) [#3299](https://github.com/TouK/nussknacker/pull/3299)[#3309](https://github.com/TouK/nussknacker/pull/3309) 
  [#3316](https://github.com/TouK/nussknacker/pull/3316) [#3322](https://github.com/TouK/nussknacker/pull/3322) [#3328](https://github.com/TouK/nussknacker/pull/3328) [#3330](https://github.com/TouK/nussknacker/pull/3330) Changes related with UniversalKafkaSource/Sink:
  * `RuntimeSchemaData` is generic - parametrized by `ParsedSchema` (AvroSchema and JsonSchema is supported).
  * `NkSerializableAvroSchema` renamed to `NkSerializableParsedSchema`
  * `SchemaWithMetadata` wraps `ParsedSchema` instead of Avro `Schema`.
  * `SchemaRegistryProvider` refactoring:
    * rename `SchemaRegistryProvider` to `SchemaBasedSerdeProvider`
    * decouple `SchemaRegistryClientFactory` from `SchemaBasedSerdeProvider`
  * `KafkaAvroKeyValueDeserializationSchemaFactory` renamed to `KafkaSchemaBasedKeyValueDeserializationSchemaFactory`
  * `KafkaAvroValueSerializationSchemaFactory` renamed to `KafkaSchemaBasedValueSerializationSchemaFactory`
  * `KafkaAvroKeyValueSerializationSchemaFactory` renamed to `KafkaSchemaBasedKeyValueSerializationSchemaFactory`
* [#3253](https://github.com/TouK/nussknacker/pull/3253) `DeploymentManager` has separate `validate` method, which should perform initial scenario validation and return reasonably quickly (while deploy can e.g. make Flink savepoint etc.)
* [#3313](https://github.com/TouK/nussknacker/pull/3313) Generic types handling changes:
  * `Typed.typedClass(Class[_], List[TypingResult])` is not available anymore. You should use more explicit `Typed.genericTypeClass` instead
  * We check count of generic parameters in `Typed.genericTypeClass` - wrong number will cause throwing exception now
  * We populate generic parameters by correct number of `Unknown` in non-generic aware versions of `Typed` factory methods like `Typed.apply` or `Typed.typedClass`
* [#3071](https://github.com/TouK/nussknacker/pull/3071) More strict Avro schema validation:
  * `ValidationMode.allowOptional` was removed, instead of it please use `ValidationMode.lax`
  * `ValidationMode.allowRedundantAndOptional` was removed, instead of it please use `ValidationMode.lax`
  * Changes of `ValidationMode`, fields: `acceptUnfilledOptional` and `acceptRedundant` were removed
* [#3376](https://github.com/TouK/nussknacker/pull/3376) `FlinkKafkaSource.flinkSourceFunction`, `FlinkKafkaSource.createFlinkSource` and `DelayedFlinkKafkaConsumer.apply` takes additional argument, `FlinkCustomNodeContext` now
* [#3272](https://github.com/TouK/nussknacker/pull/3272) `KafkaZookeeperServer` renamed to `EmbeddedKafkaServer`, `zooKeeperServer` field changed type to `Option` and is hidden now.
* [#3365](https://github.com/TouK/nussknacker/pull/3365) Numerous renames:
  * module `nussknacker-avro-components-utils` -> `nussknacker-schemed-kafka-components-utils`
  * module `nussknacker-flink-avro-components-utils` -> `nussknacker-flink-schemed-kafka-components-utils`
  * package `pl.touk.nussknacker.engine.avro` -> `pl.touk.nussknacker.engine.schemedkafka`
  * object `KafkaAvroBaseComponentTransformer` -> `KafkaUniversalComponentTransformer`
* [#3412](https://github.com/TouK/nussknacker/pull/3412) More strict filtering method types. Methods with parameters or result like `Collection[IllegalType]` are no longer available in SpEl.
* [#3542](https://github.com/TouK/nussknacker/pull/3542) Numerous renames:
  * trait `NodeAdditionalInfo` -> `AdditionalInfo`,
  * class `MarkdownNodeAdditionalInfo` -> `MarkdownAdditionalInfo`
  * trait `NodeAdditionalInfoProvider` -> `AdditionalInfoProvider` - the SPI provider's configuration files must be renamed from `pl.touk.nussknacker.engine.additionalInfo.NodeAdditionalInfoProvider` to `pl.touk.nussknacker.engine.additionalInfo.AdditionalInfoProvider`
  * method `AdditionalInfoProvider.additionalInfo` renamed to `nodeAdditionalInfo` and new method added `propertiesAdditionalInfo`

### REST API changes

* [#3169](https://github.com/TouK/nussknacker/pull/3169) API endpoint `/api/app/healthCheck` returning short JSON answer with "OK" status is now not secured - before change it required to be an authenticated user with "read" permission.

### Scenario authoring changes

* [#3187](https://github.com/TouK/nussknacker/pull/3187) [#3224](https://github.com/TouK/nussknacker/pull/3224) Choice component replaces Switch component. "Default" choice edge type, exprVal and expression are now deprecated. 
  For existing usages, you don't need to change anything. For new usages, if you want extract value e.g. to simplify choice conditions, you need to define new local variable before choice using variable component.
  "Default" choice edge type can be replaced by adding "true" condition at the end of list of conditions

### Breaking changes
* [#3328](https://github.com/TouK/nussknacker/pull/3328) Due to addition of support for different schema type (AvroSchema and JsonSchema for now) serialization format of `NkSerializableParsedSchema` has changed. Flink state compatibility of scenarios which use Avro sources or sinks has been broken.
* [#3365](https://github.com/TouK/nussknacker/pull/3365) Due to renames (see section `Code API changes`) Flink state compatibility of scenarios which use Avro sources or sinks has been broken.

### Other changes

* [#3249](https://github.com/TouK/nussknacker/pull/3249)[#3250](https://github.com/TouK/nussknacker/pull/3250) Some kafka related libraries were bumped: Confluent 5.5->7.2, avro 1.9->1.11, kafka 2.4 -> 3.2. 
  It may have influence on your custom components if you depend on `kafka-components-utils` or `avro-components-utils` module
* [#3376](https://github.com/TouK/nussknacker/pull/3376) Behavior of Flink's Kafka deserialization errors handling was changed - now instead of job failure, invalid message is omitted and configured `exceptionHandler` mechanism is used.

## In version 1.4.0
                 
### Configuration changes

* `security.rolesClaim` changed to `security.rolesClaims`, type changed to list of strings 
* `kafka.schemaRegistryCacheConfig` configuration entry was added - it was hardcoded before. 
  Default value of `kafka.schemaRegistryCacheConfig.availableSchemasExpirationTime` was changed from 1 minute to 10 seconds which will cause more often schema cache invalidation
* [#3031](https://github.com/TouK/nussknacker/pull/3031) Attachments are now stored in database (see more in section `Other changes`). `attachmentsPath` was removed. Optional config `attachments.maxSizeInBytes` was introduced with default value of 10mb 

### Code API changes

* [#2983](https://github.com/TouK/nussknacker/pull/2983) Extract Permission to extensions-api
  * Moved `pl.touk.nussknacker.ui.security.api.Permission` (security module) to `pl.touk.nussknacker.security.Permission` (extension-api module)
* [#3040](https://github.com/TouK/nussknacker/pull/3040) Deprecated `pl.touk.nussknacker.engine.api.ProcessListener.sinkInvoked` method. Switch to more general `endEncountered` method.
* [#3076](https://github.com/TouK/nussknacker/pull/3076) new implicit parameter `componentUseCase: ComponentUseCase` was added to `invoke()` method of all services extending `EagerServiceWithStaticParameters`  

### Other changes
* [#3031](https://github.com/TouK/nussknacker/pull/3031) Attachments are now stored in database. As this feature was rarely used, automatic migration of attachments from disk to db is not provided. To stay consistent db table `process_attachments` had to be truncated.
### Breaking changes
* [#3029](https://github.com/TouK/nussknacker/pull/3029) `KafkaConfig` has new field `schemaRegistryCacheConfig: SchemaRegistryCacheConfig`. Flink state compatibility has been broken.
* [#3116](https://github.com/TouK/nussknacker/pull/3116) Refactor `SchemaRegistryClientFactory` so it takes dedicated config object instead of KafkaConfig. This change minimizes chance of future Flink state compatibility break. `SchemaIdBasedAvroGenericRecordSerializer` is serialized in Flink state, so we provide it now with as little dependencies as necessary. Flink state compatibility has been broken again.
* [#3363](https://github.com/TouK/nussknacker/pull/3363) Kafka consumer no longer set `auto.offset.reset` to `earliest` by default. For default configuration files, you can use `KAFKA_AUTO_OFFSET_RESET` env variable to easily change this setting.               

## In version 1.3.0

### Code API changes

* [#2741](https://github.com/TouK/nussknacker/pull/2741) [#2841](https://github.com/TouK/nussknacker/pull/2841) Remove custom scenario provides some changes on API:
  * Replace ProcessDeploymentData by CanonicalProcess (as VO)
  * Replace scenario jsonString by CanonicalProcess at DeploymentManager, ProcessConfigEnricherInputData
* [#2773](https://github.com/TouK/nussknacker/pull/2773) Using VersionId / ProcessId / ProcessName instead of Long or String:
  * `PullProcessRepository` API was changed, right now we use VersionId instead of Long
* [#2830](https://github.com/TouK/nussknacker/pull/2830) `RunMode` is renamed to `ComponanteUseCase` and `Normal` value is split into: EngineRuntime, Validation, ServiceQuery, TestDataGeneration. `RunMode.Test` becomes `ComponanteUseCase.TestRuntime`
* [#2825](https://github.com/TouK/nussknacker/pull/2825), [#2868](https://github.com/TouK/nussknacker/pull/2868) [#2912](https://github.com/TouK/nussknacker/pull/2912) API modules changes:
  * Extracted new modules:
    * `nussknacker-scenario-api` with all scenario API parts from `api` and `interpreter`
    * `nussknacker-components-api` (and `nussknacker-lite-components-api`, `nussknacker-flink-components-api` etc.), which
      contain API for creating components
    * `nussknacker-common-api` - base value classes shared between `scenario-api` and `components-api` like `NodeId`, `Metadata` etc.
    * `nussknacker-extensions-api` - API of extensions other than components
  * Because of that, some changes in code were also introduced:
    * `NodeId` moved from `pl.touk.nussknacker.engine.api.context.ProcessCompilationError` to `pl.touk.nussknacker.engine.api`
    * `NodeExpressionId`, `DefaultExpressionId` and `branchParameterExpressionId` moved 
      from `pl.touk.nussknacker.engine.api.context.ProcessCompilationError` to `pl.touk.nussknacker.engine.graph.expression`
    * `JobData` no longer contains `DeploymentData`, which is not accessible for components anymore
    * `DisplayJson`, `WithJobData`, `MultiMap` moved to `utils`
    * Some methods from API classes (e.g. `Parameter.validate`) and classes (`InterpretationResult`) moved to interpreter
    * `DeploymentManagerProvider.createDeploymentManager` takes now `BaseModelData` as an argument instead of `ModelData`. If you want to use this data to invoke scenario, you should
      cast it to invokable representation via: `import ModelData._; modelData.asInvokableModelData`
* [#2878](https://github.com/TouK/nussknacker/pull/2878) [2898](https://github.com/TouK/nussknacker/pull/2898) [#2924](https://github.com/TouK/nussknacker/pull/2924) Cleaning up of `-utils` modules
  * Extracted internal classes, not intended to be used in extensions to nussknacker-internal-utils module
  * Extracted component classes, not used directly by runtime/designer to nussknacker-components-utils module
  * Extracted kafka component classes, not used directly by lite-kafka-runtime/kafka-test-utils to nussknacker-kafka-components-utils
  * Moved some classes that are in fact part of API to -api modules (e.g. `ToJsonEncoder`) 
  * Module renames:
    * nussknacker-avro-util to nussknacker-avro-components-utils
    * nussknacker-flink-avro-util to nussknacker-flink-avro-components-utils
    * nussknacker-flink-kafka-util to nussknacker-flink-kafka-components-utils
    * nussknacker-flink-util to nussknacker-flink-components-utils
    * nussknacker-request-response-util to nussknacker-request-response-components-utils
    * nussknacker-model-util to nussknacker-helpers-utils
  * Minor changes in code:
    * Use `val docsConfig = new DocsConfig(config); import docsConfig._` instead of `implicit val docsConfig = (...); import DocsConfig._`
    * Some components specific methods are not available from `KafkaUtils`. Instead, they are available from `KafkaComponentsUtils`
    * `ToJsonEncoder.encoder` takes `Any => Json` function instead of `BestEffortJsonEncoder` as a parameter
* [#2907](https://github.com/TouK/nussknacker/pull/2907) Hide some details of metrics to `utils-internal` 
   (`InstantRateMeter`, `InstantRateMeterWithCount`), use method added to `MetricsProviderForScenario`                        
* [#2916](https://github.com/TouK/nussknacker/pull/2916) Changes in `ProcessState` API.
  * Six similar methods creating `ProcessState` based on `StateStatus` and some other details merged to one.
    * Methods removed:
      * Two variants of `ProcessState.apply` taking `ProcessStateDefinitionManager` as a parameter
      * `SimpleProcessState.apply`
      * Two variants of `ProcessStatus.simple`
      * `ProcessStatus.createState` taking `ProcessStateDefinitionManager` as a parameter
    * Method added: `ProcessStateDefinitionManager.processState` with some default parameter values
  * `ProcessStatus` class is removed at all. All methods returning `ProcessState` by it moved to `SimpleProcessStateDefinitionManager` 
    and removed `previousState: Option[ProcessState]` from it. If you want to keep previous state's deployment details and only
    change "status details" just use `processState.withStatusDetails` method
  * `ProcessState`, `CustomAction` and its dependencies moved from `nussknacker-deployment-manager-api` to `nussknacker-scenario-deployment-api`, 
    `restmodel` module not depend on `deployment-manager-api` anymore
  * [#2969](https://github.com/TouK/nussknacker/pull/2969) Action `ProcessActionType.Deploy` is now available by default for scenarios in `SimpleStateStatus.DuringDeploy` state.
    Mind this if you depend on `OverridingProcessStateDefinitionManager` or `SimpleProcessStateDefinitionManager`, and specifically on theirs `statusActions` method.
    As an exception, implementation for Flink `FlinkProcessStateDefinitionManager` stays the same as before (only `ProcessActionType.Cancel` is possible in this state), but this may be unified in the future.

### Other changes

* [#2886](https://github.com/TouK/nussknacker/pull/2886) This change can break previous flink snapshot compatibility.
  Restoring state from previous snapshot asserts that restored serializer UID matches current serializer UID.
  This change ensures that in further release deployments UIDs persisted within snapshots are not re-generated in runtime.
* [#2950](https://github.com/TouK/nussknacker/pull/2950) Remove `MATH` helper, use `NUMERIC` methods (they work better with some number types conversions)  

## In version 1.2.0

### Configuration changes

* [#2483](https://github.com/TouK/nussknacker/pull/2483) `COUNTS_URL` environment variable is not `INFLUXDB_URL`, without `query` path part.
* [#2493](https://github.com/TouK/nussknacker/pull/2493) kafka configuration should be moved to components provider configuration - look at `components.kafka` in dev-application.conf for example
* [#2624](https://github.com/TouK/nussknacker/pull/2624) Default name for `process` tag is now `scenario`. This affects metrics and count functionalities. 
  Please update you Flink/Telegraf setup accordingly (see [nussknacker-quickstart](https://github.com/TouK/nussknacker-quickstart/tree/main/telegraf) for details). 
  If you still want to use `process` tag (e.g. you have a lot of dashboards), please set `countsSettings.metricsConfig.scenarioTag` setting to `process`
  Also, dashboard links format changed, see [documentation](https://docs.nussknacker.io/documentation/docs/installation_configuration_guide/DesignerConfiguration#metric-dashboard) for the details.
* [#2645](https://github.com/TouK/nussknacker/pull/2645) Default models: `genericModel.jar`, `liteModel.jar`. 
  were merged to `defaultModel.jar`, `managementSample.jar` was renamed to `devModel.jar`. 
  If you use `defaultModel.jar` it's important to include `flinkExecutor.jar` explicitly on model classpath.                         

### Scenario authoring changes

* [#2564](https://github.com/TouK/nussknacker/pull/2564/files) Flink union now takes only 'Output expression' parameters for branches (previously 'value' parameter), output variable must be of the same type,
  if you want to distinguish source branch in output variable please use map variable, example in Basic Nodes docs.
  
### Other changes

* [#2554](https://github.com/TouK/nussknacker/pull/2554) Maven artifact `nussknacker-kafka-flink-util` become `nussknacker-flink-kafka-util` and `nussknacker-avro-flink-util` become `nussknacker-flink-avro-util`.
  General naming convention is `nussknacker-$runtimeType-$moduleName`. Components inside distribution changed layout to `components(/$runtimeType)/componentName.jar` e.g. `components/flink/kafka.jar` or `components/openapi.jar`
  `KafkaSource` become `FlinkKafkaSource`, `ConsumerRecordBasedKafkaSource` become `FlinkConsumerRecordBasedKafkaSource`, `KafkaSink` become `FlinkKafkaSink`, `KafkaAvroSink` become `FlinkKafkaAvroSink`
* [#2535](https://github.com/TouK/nussknacker/pull/2535), [#2625](https://github.com/TouK/nussknacker/pull/2625), [#2645](https://github.com/TouK/nussknacker/pull/2645)  Rename `standalone` to `request-response`: 
  * Renamed modules and artifacts
  * `StandaloneMetaData` is now `RequestResponseMetaData`
  * Move `request-response` modules to `base` dir. 
  * `standalone` in package names changed to `requestresponse`
  * `Standalone` in class/variable names changed to `RequestResponse`
  * `DeploymentManager/Service` uses dedicated format of status DTO, instead of the ones from `deployment-manager-api`
  * Removed old, deprecated `jarPath` settings, in favour of `classPath` used in other places
  * Extracted `nussknacker-lite-request-response-components` module
* [#2582](https://github.com/TouK/nussknacker/pull/2582) `KafkaUtils.toProducerProperties` setup only basic properties now (`bootstrap.servers` and serializers) - before the change it
  was setting options which are not always good choice (for transactional producers wasn't)
* [#2600](https://github.com/TouK/nussknacker/pull/2600) `ScenarioInterpreter`, `ScenarioInterpreterWithLifecycle` now takes additional
  generic parameter: `Input`. `ScenarioInterpreter.invoke` takes `ScenarioInputBatch` which now contains list of `SourceId -> Input` instead of
  `SourceId -> Context`. Logic of `Context` preparation should be done in `LiteSource` instead of before `ScenarioInterpreter.invoke`. invocation
  It means that `LiteSource` also takes this parameter and have a new method `createTransformation`.
* [#2635](https://github.com/TouK/nussknacker/pull/2635) `ContextInitializer.initContext` now takes `ContextIdGenerator` instead of `nodeId` and returns just a function
  with strategy of context initialization instead of serializable function with `Lifecycle`. To use it with Flink engine, use `FlinkContextInitializingFunction` wrapper.
* [#2649](https://github.com/TouK/nussknacker/pull/2649) `DeploymentManagerProvider` takes new `ProcessingTypeDeploymentService` class as an implicit parameter
* [#2564](https://github.com/TouK/nussknacker/pull/2564/files) 'UnionParametersMigration' available to migrate parameter name from 'value' to 'Output expression' - please turn it on you are using 'union' like component
* [#2645](https://github.com/TouK/nussknacker/pull/2645) Simplify structure of available models (implementations of `ProcessConfigCreator`). `defaultModel.jar` and components 
  should be used instead of custom implementations of `ProcessConfigCreator`, the only exception is when one wants to customize `ExpressionConfig`. Also, `nussknacker-flink-engine` module became `nussknacker-flink-executor`.
* [#2651](https://github.com/TouK/nussknacker/pull/2651) `ValidationContext.clearVariables` now clears also parent reference. Important when invoked inside fragments.
* [#2673](https://github.com/TouK/nussknacker/pull/2673) `KafkaZookeeperUtils` renamed to `KafkaTestUtils`, it doesn't depend on ZooKeeper anymore.        
* [#2686](https://github.com/TouK/nussknacker/pull/2686) `ServiceWithStaticParameters` renamed to `EagerServiceWithStaticParameters`.
* [#2695](https://github.com/TouK/nussknacker/pull/2695) `nodeId` replaced with `NodeComponentInfo` in `NuExceptionInfo`. Simple wrapper class which holds the same `nodeId` and also `componentInfo`.
  Migration is straightforward, just put `nodeId` into the new case class:
  * `NuExceptionInfo(None, exception, context)` => stays the same
  * `NuExceptionInfo(Some(nodeId), exception, context)` => `NuExceptionInfo(Some(NodeComponentInfo(nodeId, None)), exception, context)`
    * if an exception is thrown inside the component, additional information can be provided:
      * for base component (like `filter` or `split`): `NodeComponentInfo.forBaseNode("nodeId", ComponentType.Filter)`
      * for other: `NodeComponentInfo("nodeId", ComponentInfo("kafka-avro-source", ComponentType.Source))`
  * The same migration has to be applied to `ExceptionHandler.handling()` method.
* [#2824](https://github.com/TouK/nussknacker/pull/2824) 'ProcessSplitterMigration' available to migrate node name from 'split' to 'for-each' (see [#2781](https://github.com/TouK/nussknacker/pull/2781))- please turn it on if you are using 'split' component

## In version 1.1.0
:::info
Summary:
- A lot of internal refactoring was made to separate code/API specific for Flink.
  If your deployment has custom components pay special attention to:
  - `Lifecycle` management
  - Kafka components
  - Differences in artifacts and packages
- Some of the core dependencies: cats, cats-effect and circe were upgraded. It affects mainly code, but it may 
  also have impact on state compatibility and performance. 
- Default Flink version was bumped do 1.14 - see https://github.com/TouK/nussknacker-flink-compatibility on how to run Nu on older Flink versions.
- Execution of SpEL expressions is now checked more strictly, due to security considerations. These checks can be overridden with custom `ExpressionConfig`. 
:::
:::info
- Apart from that:
  - minor configuration naming changes
  - removal of a few of minor, not documented features (e.g. SQL Variable)
:::
 
* [#2208](https://github.com/TouK/nussknacker/pull/2208) Upgrade, cats, cats-effects, circe. An important nuisance: we didn't upgrade sttp, so we cannot depend on `"com.softwaremill.sttp.client" %% "circe"`. Instead,
  the code is [copied](https://github.com/TouK/nussknacker/blob/staging/utils/httpUtils/src/main/scala/sttp/client/circe/SttpCirceApi.scala). Make sure you don't include sttp-circe integration as transitive dependency, but use class from http-utils instead.
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
* [#2296](https://github.com/TouK/nussknacker/pull/2296) Scenarios & Fragments have separate TypeSpecificData implementations. Also, we remove `isSubprocess` field from process JSON, and respectively from MetaData constructor. See corresponding db migration `V1_031__FragmentSpecificData.scala`
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
  val oldSchema = new EspDeserializationSchema[SampleValue](bytes => io.circe.parser.decode[SampleValue](new String(bytes)).toOption.get)
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
  - ConfluentAvroToJsonFormatter produces test data in valid JSON format, does not use Separator
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
* [#922](https://github.com/TouK/nussknacker/pull/922) HealthCheck API has new structure, naming and JSON responses:
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

Be aware that we are using Avro 1.9.2 instead of default Flink's 1.8.2 (for Java time logical types conversions purpose).

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
