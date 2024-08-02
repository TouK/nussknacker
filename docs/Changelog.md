# Changelog

1.17.0 (Not released yet)
-------------------------
* [#6398](https://github.com/TouK/nussknacker/pull/6398) Added possibility to define hint texts for scenario properties in config.
* [#6282](https://github.com/TouK/nussknacker/pull/6184) From now on, the existence of Kafka topics used in Sources and/or 
  Sinks will always be validated. (`topicsExistenceValidationConfig.enabled` default was changed from `false` to `true`)
* [#6384](https://github.com/TouK/nussknacker/pull/6384) Value of [Realm](https://datatracker.ietf.org/doc/html/rfc2617#section-1.2) 
  can be customized using env `AUTHENTICATION_REALM` (its default value "nussknacker" remains un changed)
* [#6363](https://github.com/TouK/nussknacker/pull/6363) Improvement on SpEL suggestions mechanism, now we are able to 
  provide suggestions even if the whole expression does not evaluate to proper SpEL expression. 
* [#6388](https://github.com/TouK/nussknacker/pull/6388) Fix issue with suggestion expression mode and any value with suggestion in fragmentInput component, now supporting SpEL expressions.
* [#6418](https://github.com/TouK/nussknacker/pull/6418) Improvement: Pass implicit nodeId to `EagerServiceWithStaticParameters.returnType`
* [#6333](https://github.com/TouK/nussknacker/pull/6333) Test data generation: more meaningful error message when no data to generate
* [#6386](https://github.com/TouK/nussknacker/pull/6386) Security fix for situation where array constructor could be 
  used to execute unallowed expressions by exploiting the lack of full validation inside array dimensions. 
  * Array constructor expressions are now illegal.
* [#6432](https://github.com/TouK/nussknacker/pull/6432) The default `topicsExistenceValidationConfig.validatorConfig.adminClientTimeout` 
  increased from `500ms` to `10s` to avoid scenario starting problems
* [#6217](https://github.com/TouK/nussknacker/pull/6217) Improvement: Make flink-metrics-dropwizard as provided dependency at flink-components-utils
* [#6353](https://github.com/TouK/nussknacker/pull/6353) Performance improvement: simple types such as numbers, boolean, string, date types
  and arrays are serialized/deserialized more optimal in aggregates
* [#6437](https://github.com/TouK/nussknacker/pull/6437) Removed deprecated operation to create a scenario:
  POST `/api/processes/{name}/{category}`. POST `/api/processes` should be used instead.
* [#6415](https://github.com/TouK/nussknacker/pull/6415) Added "Component group" field to fragment properties, which allows selection of the group of components in the Creator Panel in which the fragment will be available
* [#6462](https://github.com/TouK/nussknacker/pull/6462) Improvement of Component's API: `canHaveManyInputs` property is now 
  determined automatically, developer doesn't need to provide it by his/her own
* [#6445](https://github.com/TouK/nussknacker/pull/6445) [#6499](https://github.com/TouK/nussknacker/pull/6499) Add support to seconds in a duration editor
* [#6436](https://github.com/TouK/nussknacker/pull/6436) Typed SpEL list expressions will now infer their compile-time known values, instead of only the supertype of its elements. These values can be used in custom components or validators.
    * NOTE: selection (`.?`), projection (`.!`) or operations from the `#COLLECTIONS` helper cause the typed list to lose its elements' values
* [#6445](https://github.com/TouK/nussknacker/pull/6445) [#6499](https://github.com/TouK/nussknacker/pull/6499) Add support to seconds in a duration editor
* Batch processing mode related improvements:
  * [#6353](https://github.com/TouK/nussknacker/pull/6353) [#6467](https://github.com/TouK/nussknacker/pull/6467) Added `join` component
  * [#6503](https://github.com/TouK/nussknacker/pull/6503) Records are produced by Table Source as `Row`s instead of `Map`s. 
    Thanks to that, more scenario constructions work correctly with table components such as `join`.
  * [#6195](https://github.com/TouK/nussknacker/pull/6195) [#6340](https://github.com/TouK/nussknacker/pull/6340) [#6506](https://github.com/TouK/nussknacker/pull/6506) Added test data generation and testing for Table Source:
    * Added test data generation with 2 modes:
      * Live (default) - creates records by pulling data from the currently configured data source
      * Random - creates randomized records
    * Data generation mode can be configured through the `testDataGenerationMode` in the table components configuration 
      with `"live"` or `"random"` setting
    * The test data can be generated into a file through the `generate file` button
    * Added ability to run tests on data from file or generated on the spot (the `generated` button)
  * [#6518](https://github.com/TouK/nussknacker/pull/6518) Added user friendly editor for Table Sink output value
    * The editor can be easily switched backed to 'raw' version similarly to the current Kafka Sink
  * [#6545](https://github.com/TouK/nussknacker/pull/6545) Virtual columns are properly handled in Table Sink

1.16.2 (18 July 2024)
-------------------------
* [#6388](https://github.com/TouK/nussknacker/pull/6388) Fix issue with suggestion expression mode and any value with suggestion in fragmentInput component, now supporting SpEL expressions.
* [#6398](https://github.com/TouK/nussknacker/pull/6398) Added possibility to define hint texts for scenario properties in config.

1.16.1 (16 July 2024)
-------------------------
* [#6382](https://github.com/TouK/nussknacker/pull/6382) Avoid timeout on model reload by stopping DeploymentActor and RescheduleFinishedActor non-gracefully. Instead, retry until success while creating new actors.

1.16.0 (11 July 2024)
-------------------------

* [#6184](https://github.com/TouK/nussknacker/pull/6184) Removed `Remote[]` string part from forwarded username for scenario creation and updates.
* [#6053](https://github.com/TouK/nussknacker/pull/6053) Added impersonation mechanism support in Nu API for BasicAuth security module.
* [#6008](https://github.com/TouK/nussknacker/pull/6008) Add embedded QuestDB as database for FE statistics.
* [#5982](https://github.com/TouK/nussknacker/pull/5982) [#6155](https://github.com/TouK/nussknacker/pull/6155) [#6172](https://github.com/TouK/nussknacker/pull/6172) [#6221](https://github.com/TouK/nussknacker/pull/6221) Batch processing mode related improvements:
    * Deployments API returns correct status of deployment instead of returning always the last deployment's status
    * Deployments API returns more information about status of a deployment: problem description and status modification time
    * Status of a deployment is cached on the Designer side - in case of retention of finished job on Flink, status is still returned as FINISHED
    * Overlapping deployment metrics/counts workaround: Ensure that only one deployment is performed for each scenario at a time
    * Blocking of deployment of a fragment
    * Blocking of deployment of an archived scenario
* [#6121](https://github.com/TouK/nussknacker/pull/6121) Add functionality to reorder columns within the table editor.
* [#6136](https://github.com/TouK/nussknacker/pull/6136) Add possibility to configure kafka exactly-once delivery for flink.
* [#6185](https://github.com/TouK/nussknacker/pull/6185) Improvement: Make UniversalKafkaSinkFactory available at BoundedStream
* [#6208](https://github.com/TouK/nussknacker/pull/6208) Fix issue with node window positioning after closing a full-screen window node.
* [#6225](https://github.com/TouK/nussknacker/pull/6225) Resolved an issue with fragment input parameters where the initial value was defined and the input mode changed from any value to a fixed list.
* [#6245](https://github.com/TouK/nussknacker/pull/6245) Parameter validations defined in AdditionalUIConfigProvider now properly impact dynamic components.
* [#6264](https://github.com/TouK/nussknacker/pull/6264) Fix for DatabaseLookupEnricher mixing fields values when it is connected to ignite db
* [#6270](https://github.com/TouK/nussknacker/pull/6270) Resolved an issue with comparing remote versions
* [#6337](https://github.com/TouK/nussknacker/pull/6337) Fixes memory leak in test mechanism introduced in 1.13 version ([#4901](https://github.com/TouK/nussknacker/pull/4901))
* [#6322](https://github.com/TouK/nussknacker/pull/6322) Fix search nodes: usage of ctrl-f was limited to nodes search only.

1.15.4 (10 July 2024)
-------------------------
* [#6319](https://github.com/TouK/nussknacker/pull/6319) Fix migration between environments.
* [#6322](https://github.com/TouK/nussknacker/pull/6322) Fix search nodes: usage of ctrl-f was limited to nodes search only.

1.15.3 (24 June 2024)
-------------------------
* [#6191](https://github.com/TouK/nussknacker/pull/6191) Fixes caching of Flink's jobs config. Was cached empty config in some cases.
* [#6225](https://github.com/TouK/nussknacker/pull/6225) Resolved an issue with fragment input parameters where the initial value was defined and the input mode changed from any value to a fixed list.
* [#6230](https://github.com/TouK/nussknacker/pull/6230) Avoid potential race condition by preventing the marking of freshly deployed jobs as finished when synchronizing deployment states.
* [#6204](https://github.com/TouK/nussknacker/pull/6204) [#6055](https://github.com/TouK/nussknacker/pull/6055) Fixup to lifecycle of ExecutionContext used in Asynchronous IO Mode which could lead to RejectedExecutionException after scenario restart on Flink.

1.15.2 (7 June 2024)
-------------------------
* [#6134](https://github.com/TouK/nussknacker/pull/6134) Fixes in determining `lastStateActionData` and `lastDeployedActionData` for Scenario.
  * Deployed version of scenario is now shown properly even if other actions followed deploy.
  * Scenario state is now not impacted by actions that don't actually change it.

1.15.1 (5 June 2024)
-------------------------
* [#6126](https://github.com/TouK/nussknacker/pull/6126) Fix statistics configuration.
* [#6127](https://github.com/TouK/nussknacker/pull/6127) Ad-hoc tests available in scenarios without `flink-dropwizard-metrics-deps` in classPath

1.15.0 (17 May 2024)
-------------------------
* [#5620](https://github.com/TouK/nussknacker/pull/5620) Nodes Api OpenApi-based documentation (e.g. `https://demo.nussknacker.io/api/docs`)
* [#5760](https://github.com/TouK/nussknacker/pull/5760) [#5599](https://github.com/TouK/nussknacker/pull/5599) [#3922](https://github.com/TouK/nussknacker/pull/5901) Libraries bump:
  * Cats 2.9.0 -> 2.10.0
  * Cats Effect: 3.5.2 -> 3.5.4
  * Flink: 1.17.2 -> 1.18.1
  * Kafka client: 3.6.1 -> 3.6.2
  * Netty 4.1.93 -> 4.1.109
  * openapi-circe-yaml: 0.6.0 -> 0.7.4
  * Tapir: 1.7.4 -> 1.9.11
* [#5438](https://github.com/TouK/nussknacker/pull/5438) [#5495](https://github.com/TouK/nussknacker/pull/5495) Improvement in DeploymentManager API:
    * Alignment in the api of primary (deploy/cancel) actions and the experimental api of custom actions.
* [#5798](https://github.com/TouK/nussknacker/pull/5798) Improvements in DeploymentService:
    * Custom actions are handled in the same way as deploy and cancel: action and comment are registered in NU database
* [#5783](https://github.com/TouK/nussknacker/pull/5783) Added information about component's allowed processing modes to Component API
* [#5831](https://github.com/TouK/nussknacker/pull/5831) Fragment input parameters presets support
* [#5780](https://github.com/TouK/nussknacker/pull/5780) Fixed Scala case classes serialization when a class has additional fields in its body
* [#5848](https://github.com/TouK/nussknacker/pull/5848) Change colors across an entire user interface
* [#5853](https://github.com/TouK/nussknacker/pull/5853) Add processing mode information and filtering to the components table
* [#5843](https://github.com/TouK/nussknacker/pull/5843) Added new endpoint to provide statistics URL
* [#5873](https://github.com/TouK/nussknacker/pull/5873) Handle list of the anonymous statistic URLs, and send them in the interval
* [#5889](https://github.com/TouK/nussknacker/pull/5889) Decision Table component parameters validation improvement
* [#5898](https://github.com/TouK/nussknacker/pull/5898) Fixed Nu runtime memory leak
* [#5887](https://github.com/TouK/nussknacker/pull/5887) Fix toolbar icon styles when multiline text
* [#5779](https://github.com/TouK/nussknacker/pull/5779) Add API versioning for migration between environments
* [#6016](https://github.com/TouK/nussknacker/pull/6016) Add RANDOM helper
* [#5595](https://github.com/TouK/nussknacker/pull/5618) [#5618](https://github.com/TouK/nussknacker/pull/5595) [#5627](https://github.com/TouK/nussknacker/pull/5627) 
  [#5734](https://github.com/TouK/nussknacker/pull/5734) [#5653](https://github.com/TouK/nussknacker/pull/5653) [#5744](https://github.com/TouK/nussknacker/pull/5744) 
  [#5757](https://github.com/TouK/nussknacker/pull/5757) [#5777](https://github.com/TouK/nussknacker/pull/5777) [#5825](https://github.com/TouK/nussknacker/pull/5825)
  [#5896](https://github.com/TouK/nussknacker/pull/5896) Experimental Batch processing mode support
* [#6031](https://github.com/TouK/nussknacker/pull/6031) Fixed duplicated components on the Components tab
* [#6010](https://github.com/TouK/nussknacker/pull/6010) Fix ad hoc tests with schemas containing nested fields
* [#6044](https://github.com/TouK/nussknacker/pull/6044) Fixed json refs handling: If first parsed record was an empty record, every subsequent record was treated as an empty record 
* [#5813](https://github.com/TouK/nussknacker/pull/5813) Fixed "More than one processing type ..." error during scenario creation when user has limited access to categories.

1.14.0 (21 Mar 2024)
-------------------------
* [#4287](https://github.com/TouK/nussknacker/pull/4287) [#5257](https://github.com/TouK/nussknacker/pull/5257) [#5432](https://github.com/TouK/nussknacker/pull/5432) [#5552](https://github.com/TouK/nussknacker/pull/5552) [#5645](https://github.com/TouK/nussknacker/pull/5645) Libraries bump:
  * Flink: 1.16.2 -> 1.17.2
  * Cats Effect: 2.5.5 -> 3.5.2
  * Kafka client: 3.3.2 -> 3.6.1
  * Confluent Schema Registry client: 7.3.2 -> 7.5.1
  * Avro: 1.11.1 -> 1.11.3
  * Skuber: 3.0.6 -> 3.2
* [#5253](https://github.com/TouK/nussknacker/pull/5253) Removed the option to configure fragments via config. Due to the recent expansion of FragmentParameter, the option has become largely redundant. Removed to decrease unnecessary complexity.
* [#5271](https://github.com/TouK/nussknacker/pull/5271) Changed `AdditionalUIConfigProvider.getAllForProcessingType` API to be more in line with FragmentParameter
* [#5278](https://github.com/TouK/nussknacker/pull/5278) Recreate assembled model JAR for Flink if it got removed (e.g. by systemd-tmpfiles)
* [#5280](https://github.com/TouK/nussknacker/pull/5280) Security improvement: Checking if user has access rights to fragment's Category for fragments served by definitions API
* [#5303](https://github.com/TouK/nussknacker/pull/5303) Added `skipNodeResults` parameter to API endpoints that return scenario validation results
* [#5323](https://github.com/TouK/nussknacker/pull/5323) Removed support for [the legacy categories configuration format](https://nussknacker.io/documentation/docs/1.12/installation_configuration_guide/DesignerConfiguration/#scenario-type-categories)
* [#5266](https://github.com/TouK/nussknacker/pull/5266) Security improvement: removed accessing class internals of records in expressions
* [#5361](https://github.com/TouK/nussknacker/pull/5361) Parameter's label can be specified not only via configuration, but also inside Component's implementation now
* [#5368](https://github.com/TouK/nussknacker/pull/5368) A hidden features allowing to change `icon` and `docsUrl` inside properties modal by using `componentsUiConfig.$proprties` configuration option, was turned off
* [#5356](https://github.com/TouK/nussknacker/pull/5356) Pushed configs provided by AdditionalUIConfigProvider deeper into domain (at the stage of component definition extraction), allowing it to impact validation. However, changes now require model reload.
* [#5413](https://github.com/TouK/nussknacker/pull/5413) Generic types (Records, Lists and other collections) now uses generic parameters in their method's return type e.g. `{foo: 1, bar: 2}.get('foo')` returns `Integer` instead of `Unknown`
* [#5413](https://github.com/TouK/nussknacker/pull/5413) Avro Records now has additional `get(String)` method allowing to access fields dynamically
* [#5419](https://github.com/TouK/nussknacker/pull/5419) Remove system properties from merged model config
* [#5363](https://github.com/TouK/nussknacker/pull/5363) Improvements and fixes related to scenario level errors:
  * Fixed bug where scenario level error related to node flashed when opening a node
  * Fixed highlighting of fragment nodes causing errors
  * Display fragment level validation errors when editing fragment
  * Improved error messages
* [#5364](https://github.com/TouK/nussknacker/pull/5364) Fixed wrong expression suggestions and validation errors in disabled nodes
* [#5447](https://github.com/TouK/nussknacker/pull/5447) Fixed `java.lang.reflect.InaccessibleObjectException: Unable to make public java.lang.Object` exception by downgrade of JRE from 17 to 11 in lite runner image for scala 2.13
* [#5465](https://github.com/TouK/nussknacker/pull/5465) Fix: Sometimes `Bad expression type, expected: X found X` error was reported when comparing matching Records.
  It happened for records that had fields with mixed different types of fields e.g. simple classes with nested records
* [#5465](https://github.com/TouK/nussknacker/pull/5465) Fix: Ternary operator (`expression ? x : y`) returned sometimes `EmptyUnion` type which couldn't be passed anywhere.
* [#5465](https://github.com/TouK/nussknacker/pull/5465) Fix: Wasn't possible to compare a Record with a Map.
* [#5457](https://github.com/TouK/nussknacker/pull/5457) Fix: Array types wasn't serialized correctly which caused deserialization error during node validation.
* [#5475](https://github.com/TouK/nussknacker/pull/5475) SpEL expressions checking improvement: The equals operator used with two Lists with different element types is reported as an error
* [#5389](https://github.com/TouK/nussknacker/pull/5389) Scenario activity API OpenAPI-based documentation (e.g. `https://demo.nussknacker.io/api/docs`)
* [#5509](https://github.com/TouK/nussknacker/pull/5509) Security improvement: 
  * Added authorization check for listing activities and downloading attachments in scenario activity API
  * Fixed the ability to download an unrelated attachment from a given scenario
* [#5522](https://github.com/TouK/nussknacker/pull/5522), [#5519](https://github.com/TouK/nussknacker/pull/5519) Scenario status caching more often
* [#5505](https://github.com/TouK/nussknacker/pull/5505) [#5710](https://github.com/TouK/nussknacker/pull/5710) Fix: anonymous user handling regression
* [#5371](https://github.com/TouK/nussknacker/pull/5371) Added new parameter editor type: DictParameterEditor.
* [#5373](https://github.com/TouK/nussknacker/pull/5373) API changes related to components development
* [#5566](https://github.com/TouK/nussknacker/pull/5566) [#5550](https://github.com/TouK/nussknacker/pull/5537) 
  [#5515](https://github.com/TouK/nussknacker/pull/5515) [#5474](https://github.com/TouK/nussknacker/pull/5474) Processing mode and engine available in the GUI.
  ⚠️ for the helm chart deployment, you need to manually migrate scenarios in db to one of possible processing types: either `streaming` or `request-response`. See [MigrationGuide](MigrationGuide.md) for details 
* [#5566](https://github.com/TouK/nussknacker/pull/5566) `DEFAULT_SCENARIO_TYPE` environment variable is not supported anymore 
* [#5272](https://github.com/TouK/nussknacker/pull/5272) [#5145](https://github.com/TouK/nussknacker/pull/5145) Added: Decision Table component
* [#5641](https://github.com/TouK/nussknacker/pull/5641) Fix: fetching/parsing batch periodic json only when needed (stop parsing during status check)
* [#5656](https://github.com/TouK/nussknacker/pull/5656) Added: Decision Table component - detailed validation
* [#5657](https://github.com/TouK/nussknacker/pull/5657) Improved heuristic for eventhub to Azure's schema name mapping.
* [#5754](https://github.com/TouK/nussknacker/pull/5754) Fix for broken encoding mechanism in tests from file with Avro format, revert [0d9b600][https://github.com/TouK/nussknacker/commit/0d9b600]
* [#5558](https://github.com/TouK/nussknacker/pull/5558) Added: an info about `Processing mode` to the scenario and divided `Status` toolbar to `Scenario actions` and `Scenario details`

1.13.3 (28 June 2024) This fix was introduced in 1.16.0 version and has only been backported to this version.
------------------------
* [#6285](https://github.com/TouK/nussknacker/pull/6285) Fix for DatabaseLookupEnricher mixing fields values when it is connected to ignite db

1.13.2 (7 Mar 2024)
------------------------
* [#5447](https://github.com/TouK/nussknacker/pull/5447) Fixed `java.lang.reflect.InaccessibleObjectException: Unable to make public java.lang.Object` exception by downgrade of JRE from 17 to 11 in lite runner image for scala 2.13
 
1.13.1 (7 Mar 2024)
-------------------------
[this version was skipped, please use 1.13.2 instead]

1.13.0 (12 Jan 2024)
-------------------------
* [#5051](https://github.com/TouK/nussknacker/pull/5051) Allow users to perform inserts/updates on db by adding `Updates count` strategy to db-query service
* [#4988](https://github.com/TouK/nussknacker/pull/4988) Refactor: Allow to use custom authentication methods in user-defined Authentication Providers
* [#4711](https://github.com/TouK/nussknacker/pull/4711) [#4862](https://github.com/TouK/nussknacker/pull/4862) Added AdditionalUIConfigProviderFactory API that allows changing components' configs and scenario properties' UI configs without model reload
* [#4860](https://github.com/TouK/nussknacker/pull/4860) Rename `additionalProperties` to `scenarioProperties`
* [#4828](https://github.com/TouK/nussknacker/pull/4828) Improvement: Allow passing timestampAssigner at FlinkTestScenarioRunner
* [#4839](https://github.com/TouK/nussknacker/pull/4839) Fixed: Fragment migration to secondary env is again available
* [#4901](https://github.com/TouK/nussknacker/pull/4901) Improvements TestScenarioRunner:
  * Run runner with proper prepared invocation collector for test mode
  * Fix passing global variables on LiteTestScenarioRunner and RequestResponseTestScenarioRunner
  * Add missing tests for passing global variables
  * Fix bug with passing components on RequestResponseTestScenarioRunner
  * Fix bug building source test context on LiteTestScenarioRunner
* [#4854](https://github.com/TouK/nussknacker/pull/4854)[#5059](https://github.com/TouK/nussknacker/pull/5059) Categories configuration redesign
* [#4919](https://github.com/TouK/nussknacker/pull/4919) Improvement: Support for handling runtime exceptions at FlinkTestScenarioRunner
* [#4923](https://github.com/TouK/nussknacker/pull/4923) Fix non-unique test case ids when testing scenario with union
* [#4745](https://github.com/TouK/nussknacker/pull/4745) Improvement: Stricter Node and scenario id validation
* [#4928](https://github.com/TouK/nussknacker/pull/4928) [#5028](https://github.com/TouK/nussknacker/pull/5028) Breaking change: `Validator.isValid` method now takes expression
  and optional evaluated value, instead of just expression. Also:
  * `LiteralRegExpParameterValidator` is renamed to `RegExpParameterValidator`, 
  * `LiteralNumberValidator` is removed,
  * `LiteralIntegerValidator` is considered deprecated and will be removed in the future.
  * new validator: `CompileTimeEvaluableValueValidator` that checks if value is valid at compile time and it should be used instead of literal validators
  * annotation `pl.touk.nussknacker.engine.api.validation.Literal` was renamed to `pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue`
* [#5033](https://github.com/TouK/nussknacker/pull/5033) Updated Scala 2.13 to 2.13.12
* [#4887](https://github.com/TouK/nussknacker/pull/4887) New parameter validator - `ValidationExpressionParameterValidator` that allows to use SpEL (or any other) expression to validate parameters
* [#5077](https://github.com/TouK/nussknacker/pull/5077) Add an option to set schema on connections in SQL enricher
* [#5059](https://github.com/TouK/nussknacker/pull/5059) [#5100](https://github.com/TouK/nussknacker/pull/5100) [#5103](https://github.com/TouK/nussknacker/pull/5103) Breaking change: Scenario type to Category mapping become 1-to-1 instead of 1-to-many.
* [#4978](https://github.com/TouK/nussknacker/pull/4978) [#5241](https://github.com/TouK/nussknacker/pull/5241) Expand `FragmentParameter` with new fields:
  * `hintText` - shown next to the parameter when using the fragment
  * `initialValue` - initial value of the parameter (before user changes it)
  * `required` - whether the parameter is mandatory
  * `valueCompileTimeValidation` - allows configuration of `ValidationExpressionParameterValidator` for this parameter
* [#4953](https://github.com/TouK/nussknacker/pull/4953) Improved node validation
* [#5141](https://github.com/TouK/nussknacker/pull/5141) Security improvement: API endpoints check if user has access rights to Category associated with Processing Type provided in API
* [#5182](https://github.com/TouK/nussknacker/pull/5182) [#5203](https://github.com/TouK/nussknacker/pull/5203) [#5250](https://github.com/TouK/nussknacker/pull/5250) Component, User and Notification API OpenAPI-based documentation (e.g. `https://demo.nussknacker.io/api/docs`)
* [#5171](https://github.com/TouK/nussknacker/pull/5171) Breaking change: some components changed identifier - url's and identifiers in node errors are changed
* [#5171](https://github.com/TouK/nussknacker/pull/5171) Component `mapVariable` was renamed to `record-variable`
* [#5223](https://github.com/TouK/nussknacker/pull/5223) Legacy low level kafka components were removed
* [#5233](https://github.com/TouK/nussknacker/pull/5233) Fix: Not expected type: Null runtime error for non-nullable, optional json fields that were not provided by user in sink
* [#5233](https://github.com/TouK/nussknacker/pull/5233) Added support for schema evolution in kafka using json schema and response sink
* [#5197](https://github.com/TouK/nussknacker/pull/5197) Improved accessing fields in records in expressions - fields can now be statically accessed using indexing
* [#5312](https://github.com/TouK/nussknacker/pull/5312) Collect component clears context variables
* [#5313](https://github.com/TouK/nussknacker/pull/5313) Added CountWhen and Average aggregations

1.12.6 (29 Jan 2024)
------------------------
* [#5447](https://github.com/TouK/nussknacker/pull/5447) Fixed `java.lang.reflect.InaccessibleObjectException: Unable to make public java.lang.Object` exception by downgrade of JRE from 17 to 11 in lite runner image for scala 2.13

1.12.5 (1 Dec 2023)
------------------------
* [#5110](https://github.com/TouK/nussknacker/pull/5110) Fix: The compare option doesn't display fragment input properties between the two versions

1.12.4 (14 Nov 2023)
------------------------
* [#4992](https://github.com/TouK/nussknacker/pull/4992) Fix: List of periodic deployments is now sorted not only by schedule time but also by its creation time.

1.12.3 (26 Oct 2023)
-------------------------
[this version was skipped]

1.12.2 (25 Oct 2023)
-------------------------
[this version was skipped]

1.12.1 (25 Oct 2023)
-------------------------
* [#4885](https://github.com/TouK/nussknacker/pull/4885) Fix: Synchronize embedded engine deployments after designer restart

1.12.0 (6 Oct 2023)
-------------------------
* [#4697](https://github.com/TouK/nussknacker/pull/4697) Change `api/parameters/*/validate` and `api/parameters/*/suggestions` endpoints.
  * Use `processingType` instead of `processName`
  * Add `scenarioName` parameter to `ParametersValidationRequest` used in `api/parameters/*/validate`
* [#4677](https://github.com/TouK/nussknacker/pull/4677) Added validation to SpEL string literal conversions (allow only constant that convert successfully)
* [#4602](https://github.com/TouK/nussknacker/pull/4602) Cleaning subprocess usages after NU 1.11 release
* [#4582](https://github.com/TouK/nussknacker/pull/4582) Fixed: Releasing app resources on the designer close 
* [#4540](https://github.com/TouK/nussknacker/pull/4540) Improvement: Allow selecting a claim from OIDC JWT to represent the username
* [#4555](https://github.com/TouK/nussknacker/pull/4555) Remove: Back compatibility for encoding/decoding UIParameter
* [#4561](https://github.com/TouK/nussknacker/pull/4561) Improvement: Users are not required at OAuth2 config file
* [#4492](https://github.com/TouK/nussknacker/pull/4492) Allow testing fragments using ad hoc testing. 
* [#4572](https://github.com/TouK/nussknacker/pull/4572) The package of improvements:
    - make the properties of the `FlinkStreamingPropertiesConfig` public, so that they can be reused
    - introduce the `CaseClassTypeInfoFactory`, a generic factory for creating `CaseClassTypeInfo`
    - allow passing classLoader at `ResourceLoader.load`
* [#4574](https://github.com/TouK/nussknacker/pull/4574) Improvements: at `KafkaClient` and `RichKafkaConsumer` in kafka-test-utils
* [#4640](https://github.com/TouK/nussknacker/pull/4640) Expand timestamp support to handle more types/formats
* [#4685](https://github.com/TouK/nussknacker/pull/4685) App API OpenAPI-based documentation (e.g. `https://demo.nussknacker.io/api/docs`)
* [#4707](https://github.com/TouK/nussknacker/pull/4707) Support for `overrideFrontendAuthenticationStrategy` configuration parameter in OIDC security model - works the same as in OAuth2 case.
* [#4739](https://github.com/TouK/nussknacker/pull/4739) Add configuration parameter for sending additional headers to InfluxDB (`countsSettings.additionalHeaders`)
* [#4762](https://github.com/TouK/nussknacker/pull/4762) Fix: RegExpParameterValidator, trimming SPeL comprehension
* [#4744](https://github.com/TouK/nussknacker/pull/4744) Fix for OIDC: support for simultaneously Machine-2-Machine and Basic flow handling: 
  * Skip user profile call based on the fact that access token is JWT with subject and user has username configured. 
  * `accessTokenIsJwt` Oidc configuration introduced in [#4283](https://github.com/TouK/nussknacker/pull/4283) is removed: `audience` configuration specifies that access token is a JWT as it was before this change
* [#4797](https://github.com/TouK/nussknacker/pull/4797) Ability to define the name of query parameter with access token that will be passed into tabs url
* [#4804](https://github.com/TouK/nussknacker/pull/4804) Improvement: Allow passing globalVariables on TestRunner

1.11.3 (11 Sep 2023)
-------------------------
* [#4629](https://github.com/TouK/nussknacker/pull/4629) Fix closing of shared SttpBackend when reloading model

1.11.1 (25 Aug 2023)
-------------------------
* [#4603](https://github.com/TouK/nussknacker/pull/4603) Fix subprocess -> fragment migration

1.11.0 (22 Aug 2023)
-------------------------
* [#4454](https://github.com/TouK/nussknacker/pull/4454) Rename 'subprocess' to 'fragment' along with all endpoints (with backward compatibility)
* [#4440](https://github.com/TouK/nussknacker/pull/4440) Improvement: Better exception info handling at KafkaExceptionInfo.inputEvent,
  from now we will return here JSON with all context variables (including context parent tree)
* [#4452](https://github.com/TouK/nussknacker/pull/4452) Ace editor bump 1.4.12 -> 1.23.0
* [#4455](https://github.com/TouK/nussknacker/pull/4455) Improvement: Allow to run TestScenarioRunner in TestRuntime mode
* [#4465](https://github.com/TouK/nussknacker/pull/4465) Faster loading of diagram editor (components panel) and components tab: Component's definition loaded once
* [#4469](https://github.com/TouK/nussknacker/pull/4469) Faster loading of components tab: Component's usages fetched once instead of fetching for each processing type
* [#4469](https://github.com/TouK/nussknacker/pull/4469) Faster loading of components tab: Don't fetch last actions
* [#4353](https://github.com/TouK/nussknacker/pull/4353) Removed isCancelled/isDeployed flags based on `ProcessAction`, `ProcessAction.action` renamed to actionType. Trait `Process` is removed.
* [#4488](https://github.com/TouK/nussknacker/pull/4488) Fix stopping processes with `/api/adminProcessManagement/stop/{processName}` endpoint on recent Flink versions
* [#4517](https://github.com/TouK/nussknacker/pull/4517) Improvement: Better readability of types display:
  * values of primitive types are now displayed in parentheses - previous `String{val}` is now `String(val)`
  * values of record types are now displayed with `Record` prefix - previous `{key: String{val}}` is now `Record{key: String(val)}`

1.10.0 (29 Jun 2023)
-------------------------
* [#4400](https://github.com/TouK/nussknacker/pull/4400) Improvement: Avoid long waits for closing job on test Flink minicluster
* [#4435](https://github.com/TouK/nussknacker/pull/4435) Fix: handle resolving refs when parsing Swagger 2.0 schema in openapi enricher
* [#4275](https://github.com/TouK/nussknacker/pull/4275) Add helper methods for use in expressions:
  * `#COLLECTION`: `concat`, `merge`, `min`, `max`, `slice`, `sum`, `sortedAsc`, `sortedDesc`, `take`, `takeLast`, `join`, `product`, `diff`, `intersect`, `distinct`, `shuffle`, `flatten`
  * `#UTIL`: `split`
* [#4315](https://github.com/TouK/nussknacker/pull/4315) Add support for test with parameters for kafka sources - FlinkKafkaSource / LiteKafkaSource.
* [#4261](https://github.com/TouK/nussknacker/pull/4261) Add TestWithParametersSupport support for flink engine
* [#4294](https://github.com/TouK/nussknacker/pull/4294) Allow to pass username while migrating scenario to secondary environment.
* [#4265](https://github.com/TouK/nussknacker/pull/4265) Removed implicit helper methods in SpEL: sum, today, now, distinct
* [#4230](https://github.com/TouK/nussknacker/pull/4230) Extend TestInfoProvider with getTestParameters to test scenarios based on window with generated fields.
  * Endpoint to test scenario based on parameters
  * New generic dialog to display scenario input parameters
* [#4204](https://github.com/TouK/nussknacker/pull/4204) ProcessingTypeDataProvider has combined data being a result of all processing types
* [#4219](https://github.com/TouK/nussknacker/pull/4219) Components are aware of processing data reload
* [#4256](https://github.com/TouK/nussknacker/pull/4256) Ignore error message and description when comparing errors lists in model migration tests
* [#4264](https://github.com/TouK/nussknacker/pull/4264) Add Unknown type as valid fragment input
* [#4278](https://github.com/TouK/nussknacker/pull/4278) Expression compilation speedup: reusage of type definitions extracted for code suggestions purpose +
  added completions for some missing types like `TimestampType` (`#inputMeta.timestampType`)
* [#4290](https://github.com/TouK/nussknacker/pull/4290) Expression compilation speedup: replace most regular expression matching with plain string matching
* [#4292](https://github.com/TouK/nussknacker/pull/4292) Expose more methods for use in expressions:
  * `java.lang.CharSequence`: `replace`
  * `java.util.Collection`: `lastIndexOf`
  * `java.util.Optional`: `isEmpty`
  * `scala.Option`, `scala.collection.Iterable`: `head`, `nonEmpty`, `orNull`, `tail`
  * `io.circe.*` (deserialized raw JSON objects): `noSpacesSortKeys`, `spaces2SortKeys`, `spaces4SortKeys`
* [#4298](https://github.com/TouK/nussknacker/pull/4298) Support arrays in `BestEffortJsonEncoder`
* [#4283](https://github.com/TouK/nussknacker/pull/4283) Fix for OIDC provider access token verification. For OIDC provider, `accessTokenIsJwt` config property is introduced, with default values `false`.
  This change also introduced a possibility to override username incoming from OIDC provider.
  For more see `usersFile` configuration. This might be helpful when other systems authenticate in Nussknacker in `machine to machine` manner.
* [#4246](https://github.com/TouK/nussknacker/pull/4246) Store components usages along with scenario json.
  Components usages by a scenario are stored in the processes version table. It allows to speed up fetching components usages across all scenarios,
  especially for a big number of scenarios and each with a lot of nodes.
* [#4254](https://github.com/TouK/nussknacker/pull/4254) Add simple spel expression suggestions endpoint to BE
* [#4323](https://github.com/TouK/nussknacker/pull/4323) Improved code suggestions with Typer
* [#4406](https://github.com/TouK/nussknacker/pull/4406) `backendCodeSuggestions` set to `true`, so by default Nussknacker will use new suggestion mechanism
* [#4299](https://github.com/TouK/nussknacker/pull/4299)[4322](https://github.com/TouK/nussknacker/pull/4322) `StateStatus` is identified by its name.
  `ProcessState` serialization uses this name as serialized state value. For compatibility reasons, it is still represented as a nested object with one `name` field.
* [#4312](https://github.com/TouK/nussknacker/pull/4312) Fix for losing unsaved changes in designer after cancel/deploy
* [#4332](https://github.com/TouK/nussknacker/pull/4332) Improvements: Don't fetch state for fragments at /api/processes/status
* [#4326](https://github.com/TouK/nussknacker/pull/4326) Expressions validation mechanism now is accumulating as many errors as possible instead of failing fast - for purpose
  of backend code completion and dictionaries substitution
* [#4339](https://github.com/TouK/nussknacker/pull/4339) Improvement: Don't fetch state for archived/unarchived scenario, return computed based on last state action
* [#4343](https://github.com/TouK/nussknacker/pull/4343) Updated Flink 1.16.1 -> 1.16.2
* [#4346](https://github.com/TouK/nussknacker/pull/4346) Improvement: Don't fetch process state for fragment
* [#4342](https://github.com/TouK/nussknacker/pull/4342) Improvement: Don't run custom action on archived scenario and fragment
* [#4302](https://github.com/TouK/nussknacker/pull/4302) State inconsistency detection was moved from designer to DeploymentManager.
* [#4360](https://github.com/TouK/nussknacker/pull/4360) Fix for spel validations: Typing type reference with class starting from lower case e.g. T(foo) caused nasty error
* [#4357](https://github.com/TouK/nussknacker/pull/4357) Refactoring of scenario properties required by `DeploymentManager` (`TypeSpecificData`) -  
  they are now derived from `ProcessAdditionalFields` and are configured in `additionalPropertiesConfig` in `DeploymentManagerProvider`

1.9.1 (24 Apr 2023)
------------------------
* [#4243](https://github.com/TouK/nussknacker/pull/4243) Fix for: Scenario status remain "in-progress" after attempt of deploy of not validating scenario

1.9.0 (21 Apr 2023)
------------------------
* [#4195](https://github.com/TouK/nussknacker/pull/4195) Add functionality to generate test cases and test scenario without need to download a file.
* [#3986](https://github.com/TouK/nussknacker/pull/3986) Updated sttp 2.2.9 -> 3.8.11
* [#3979](https://github.com/TouK/nussknacker/pull/3979) Updated Flink 1.16.0 -> 1.16.1
* [#4019](https://github.com/TouK/nussknacker/pull/4019) Make displayable process category required
* [#4020](https://github.com/TouK/nussknacker/pull/4020) Pass category name to process migration method
* [#4039](https://github.com/TouK/nussknacker/pull/4039) Fix for: After clicking cancel, sometimes for a moment appear "during deploy" status instead of "during cancel"
* [#4041](https://github.com/TouK/nussknacker/pull/4041) Concurrent deploy, cancel and test from file mechanisms are allowed now
* [#3994](https://github.com/TouK/nussknacker/pull/3994) Unification of editor/raw mode validation for JSON Schema sinks
* [#3997](https://github.com/TouK/nussknacker/pull/3997) Removal of obsolete `subprocessVersions`.
* [#4060](https://github.com/TouK/nussknacker/pull/4060) Notifications about pending deployments of other user not appear anymore.
* [#4071](https://github.com/TouK/nussknacker/pull/4071) Change BCrypt library
* [#4077](https://github.com/TouK/nussknacker/pull/4077) Fix database query invoker to be async
* [#4055](https://github.com/TouK/nussknacker/pull/4055)[#4080](https://github.com/TouK/nussknacker/pull/4080) Removed local state of designer - for HA purpose
* [#4055](https://github.com/TouK/nussknacker/pull/4055) Performance tweaks for API operations like: process status, deploy, cancel
* [#3675](https://github.com/TouK/nussknacker/pull/3675) Improvements: Normalize kafka params name
* [#4101](https://github.com/TouK/nussknacker/pull/4101) Notifications fixes:
  * Scenario state wasn't refreshed after deploy/cancel action was successfully finished (was only after failure)
  * Notification "Deployment of ... failed ..." was presented even for cancel action
* [#4102](https://github.com/TouK/nussknacker/pull/4102) Flink deploy now wait until job is started on TaskManagers before reporting that is finished -
  thanks to that status and versions panel are refreshed with "DEPLOYED" state in the same time
* [#3992](https://github.com/TouK/nussknacker/pull/3992) Fix for compiling scenarios containing filter node with only 'false' edge
* [#4127](https://github.com/TouK/nussknacker/pull/4127) ResourceLoader and bumps commons io 2.4 -> to 2.6
* [#4122](https://github.com/TouK/nussknacker/pull/4122), [#4132](https://github.com/TouK/nussknacker/pull/4132), [#4179](https://github.com/TouK/nussknacker/pull/4179), [#4189](https://github.com/TouK/nussknacker/pull/4189)
  * Add state definitions to `ProcessStateDefinitionManager`
  * Add `StatusResources` endpoint `/statusDefinitions` that returns state definitions with default state properties (such as displayable name, icon and description),
    to allow filtering by state in UI.
  * Combine statuses Failing, Failed, Error, Warning, FailedToGet and MulipleJobsRunning into one status that represents a "Problem".
    Statuses `FailedStateStatus` and "Unknown" are removed.
  * Status configuration for icon, tooltip and description is obligatory.
* [#4100](https://github.com/TouK/nussknacker/pull/4100), [#4104](https://github.com/TouK/nussknacker/pull/4104), [#4150](https://github.com/TouK/nussknacker/pull/4150)
  Before the change, the scenario list for a moment presented "local" states - based only on Nussknacker's actions log.
  After the change, we always present state is based on engine (e.g. Flink) state - in some places like scenario list, it is cached.
* [#4131](https://github.com/TouK/nussknacker/pull/4131) Support for components using other languages than SpEL, added basic support for SpEL in template mode
* [#4135](https://github.com/TouK/nussknacker/pull/4135) Added timeout configuration for fetching scenario state and bumps skuber 3.0.2 -> 3.0.5
* [#4143](https://github.com/TouK/nussknacker/pull/4143) Use `ProcessStateStatus` to detect alerting scenarios in healthcheck `/healthCheck/process/deployment`.
  After this change healthcheck alerts all types of deployment problems based on `ProcessStateStatus`, including "deployed and not running".
* [#4160](https://github.com/TouK/nussknacker/pull/4160) Testing using events from file accepts simplified test record format.
  SourceId and timestamp fields can be omitted from the test record and record field can be inlined. The simplified format works only for scenarios with only one source.
* [#4161](https://github.com/TouK/nussknacker/pull/4161) Update most dependencies to latest versions
* [#4155](https://github.com/TouK/nussknacker/pull/4155) Stop adding response header 'cache-control: max-age=0'. Akka adds correct 'etag' and 'last-modified' headers, hence caching is secure.
* [#4117](https://github.com/TouK/nussknacker/pull/4117)[#4202](https://github.com/TouK/nussknacker/pull/4202) Fragment parameters definition is now computed based on config. Thanks to that you can use `componentsUiConfig` setting to provide
  additional settings like parameter's validators or editors to fragments.
* [#4201](https://github.com/TouK/nussknacker/pull/4201) Fix for: Dynamic nodes (GenericNodeTransformation) has now initial parameters inferred based on definition instead empty List
* [#4224](https://github.com/TouK/nussknacker/pull/4224) Fix for (de)serialization of Flink state when using NU with Scala 2.13. See MigrationGuide for details.

1.8.1 (28 Feb 2023)
------------------------
* [#4018](https://github.com/TouK/nussknacker/pull/4018) Fix for: generate test data mechanism didn't work for json messages with defined schema id
* [#4024](https://github.com/TouK/nussknacker/pull/4024) Fix encoding object in sink with JSON schema pattern properties

1.8.0 (17 Feb 2023)
------------------------
* [#3963](https://github.com/TouK/nussknacker/pull/3963) - Secure processDefinitionData/services endpoint by filtering based on user category "Read" permission
* [#3945](https://github.com/TouK/nussknacker/pull/3945) - Allow to get Map category -> processingType through new categoriesWithProcessingType endpoint.
* [#3821](https://github.com/TouK/nussknacker/pull/3821) - Exact typing & validation of JsonSchema enums (before only String values were handled).
* [#3819](https://github.com/TouK/nussknacker/pull/3819) - Handle JSON Schema refs in sinks
* [#3654](https://github.com/TouK/nussknacker/pull/3654) Removed `/subprocessDetails` in favor of `/processDetails?isSubprocess=true`.
* [#3823](https://github.com/TouK/nussknacker/pull/3823), [#3836](https://github.com/TouK/nussknacker/pull/3836), [#3843](https://github.com/TouK/nussknacker/pull/3843) -
  scenarios with multiple sources can be tested from file
* [#3869](https://github.com/TouK/nussknacker/pull/3869) cross-compile - Scala 2.12 & 2.13
* [#3874](https://github.com/TouK/nussknacker/pull/3874) Tumbling window with OnEvent trigger saves context
* [#3853](https://github.com/TouK/nussknacker/pull/3853) [#3924](https://github.com/TouK/nussknacker/pull/3924) Support of patternProperties in sources/sinks with JSON Schema
* [#3916](https://github.com/TouK/nussknacker/pull/3916) `environmentAlert.cssClass` setting renamed to `environmentAlert.color`
* [#3922](https://github.com/TouK/nussknacker/pull/3922) Bumps: jwks 0.19.0 -> 0.21.3, jackson: 2.11.3 -> 2.13.4
* [#3958](https://github.com/TouK/nussknacker/pull/3958) OpenAPI: specify Content-Type header based on schema
* [#3948](https://github.com/TouK/nussknacker/pull/3948)
  * Performance fix: `kafka` source on Flink engine doesn't serialize schema during record serialization
  * Configuration handling fixes: `avroKryoGenericRecordSchemaIdSerialization` wasn't checked properly
  * Avro: added support for top level array schema
* [#3972](https://github.com/TouK/nussknacker/pull/3972) Lite engine: Kafka transactions are now optional and by default disabled
  for Azure's Event Hubs which doesn't support them so far. For other Kafka clusters they are enabled. You can change this behavior
  by setting `kafkaTransactionsEnabled` configuration option
* [#3914](https://github.com/TouK/nussknacker/pull/3914) Azure Schema Registry and Azure's Avro (de)serialization support

1.7.0 (19 Dec 2022)
------------------------
* [#3560](https://github.com/TouK/nussknacker/pull/3560), [#3560](https://github.com/TouK/nussknacker/pull/3560), [#3595](https://github.com/TouK/nussknacker/pull/3595) Migrate from Flink Scala API to Java API
* JSON Schema handling improvements:
  * [#3687](https://github.com/TouK/nussknacker/pull/3687) Support for union types
  * [#3695](https://github.com/TouK/nussknacker/pull/3695) Fixed delaying JSON records by field in universal source
  * [#3699](https://github.com/TouK/nussknacker/pull/3699) Handling null on JSON schema
  * [#3709](https://github.com/TouK/nussknacker/pull/3709) Support for typing `Map[String, T]` using JSON Schema.
    * When `properties` are defined `additionalProperties` is ignored and type is determined by `properties` - as it was before.
    * When `"additionalProperties": true` type is `Map[String, Unknown]`
    * When `"additionalProperties": T` type is `Map[String, T]`
  * [#3709](https://github.com/TouK/nussknacker/pull/3709) `BestEffortJsonSchemaEncoder` encodes only Strings for `"type": String`
  * [#3730](https://github.com/TouK/nussknacker/pull/3730) Additional fields are not trimmed during encoding when `additionalProperties` are allowed by schema
  * [#3742](https://github.com/TouK/nussknacker/pull/3742) More strict encoding - always validate against schema
  * [#3749](https://github.com/TouK/nussknacker/pull/3749) More precise encoding against integer schema

* Request-response JSON schema sink improvements:
  * [#3607](https://github.com/TouK/nussknacker/pull/3607) Encoder based on response schema.
  * [#3727](https://github.com/TouK/nussknacker/pull/3727) Sink validation changes:
    * Added param `Value validation mode`
    * We no longer support `nullable` param from Everit schema. Nullable schema are supported by union with null e.g. `["null", "string"]`
  * [#3716](https://github.com/TouK/nussknacker/pull/3716) Allow to add additional fields also in `strict validation mode`, if schema permits them.
  * [#3722](https://github.com/TouK/nussknacker/pull/3722) Validation of JSON schema with additionalProperties

* [#3707](https://github.com/TouK/nussknacker/pull/3707), [#3719](https://github.com/TouK/nussknacker/pull/3719), [#3692](https://github.com/TouK/nussknacker/pull/3692), [#3656](https://github.com/TouK/nussknacker/pull/3656), [#3776](https://github.com/TouK/nussknacker/pull/3776),  [#3786](https://github.com/TouK/nussknacker/pull/3786) Improvements in OpenAPI:
  * Support for OpenAPI 3.1.0
  * Basic support for type references in JSON schemas
  * Better logging from OpenAPI enrichers
  * Handling of API Keys in query parameter and cookie
  * It's possible to configure which HTTP codes (404 by default) can be used as successful, empty response
  * Documentation link is taken from global configuration, if operation doesn't provide one
  * Handle recursive schemas gracefully (we fall back to Unknown type on recursion detected)
* Upgrades:
  * [#3738](https://github.com/TouK/nussknacker/pull/3738) Kafka 3.2.3
  * [#3683](https://github.com/TouK/nussknacker/pull/3683) Flink 1.16

* [#3524](https://github.com/TouK/nussknacker/pull/3524) Change base Docker image to eclipse temurin due to openjdk deprecation.
* [#3606](https://github.com/TouK/nussknacker/pull/3606) Removed nussknacker-request-response-app. See MigrationGuide for details.
* [#3626](https://github.com/TouK/nussknacker/pull/3626) Fix for: using Typed.fromDetailedType with Scala type aliases cause exception
* [#3576](https://github.com/TouK/nussknacker/pull/3576) Unified `/processes` and `/processesDetails`. Both endpoints support the same query parameters.
  Added option `skipValidateAndResolve` in `/processesDetails`, `/processes/{name}` and `/processes/{name}/{versionId}`
  to return scenario JSON omitting validation and dictionary resolving.
* [#3680](https://github.com/TouK/nussknacker/pull/3680) Fix: validate multiple same fragments used in a row in legacy scenario jsons (without `outputVariableNames` field in `SubprocessRef`)
* [#3668](https://github.com/TouK/nussknacker/pull/3668) `TestScenarioRunner.requestResponseBased()` api enhancements: returning scenario compilation errors as a `ValidatedNel`
* [#3682](https://github.com/TouK/nussknacker/pull/3682) Extract generic `BaseSharedKafkaProducer`, rename `SharedKafkaProducerHolder` to `DefaultSharedKafkaProducerHolder`.
* [#3701](https://github.com/TouK/nussknacker/pull/3701) `TypedMap` allows access to non-existing keys in SpEL (returning `null`)
* [#3733](https://github.com/TouK/nussknacker/pull/3733) Fix for: some validation (e.g. Flink scenario name validation) were causing error message blinking in scenario properties.
* [#3752](https://github.com/TouK/nussknacker/pull/3752) Do not specify migrations which did not change process in process migration comment. If no migrations, do not add comment.
* [#3754](https://github.com/TouK/nussknacker/pull/3754) Fix for migrating scenarios not existing on target environment [#3700](https://github.com/TouK/nussknacker/issues/3700)
  * Will work after upgrading NU installations on both environments to version containing the fixup.
  * In conversation between versions 1.6 - 1.7 (and reversed) only migration of scenarios that exists on both envs will work.

1.6.1 (8 Nov 2022)
------------------------
* [#3647](https://github.com/TouK/nussknacker/pull/3647) Fix for serving OpenAPI definition and SwaggerUI for deployed RequestResponse scenarios in embedded mode
* [#3657](https://github.com/TouK/nussknacker/pull/3657) Fix for json-schema additionalProperties validation
* [#3672](https://github.com/TouK/nussknacker/pull/3672) Fix contextId assignment for the output of ForEachTransformer (Flink)
* [#3671](https://github.com/TouK/nussknacker/pull/3671) Fix: do not show extra scrollbar on scenario screen when panel too large
* [#3681](https://github.com/TouK/nussknacker/pull/3681) Fix: validate multiple same fragments used in a row in legacy scenario JSON (without `outputVariableNames` field in `SubprocessRef`)
* [#3685](https://github.com/TouK/nussknacker/pull/3685) Fix: inconsistent SwaggerDateTime typing (LocalDateTime/ZonedDateTime)

1.6.0 (18 Oct 2022)
------------------------
* [#3382](https://github.com/TouK/nussknacker/pull/3382) Security fix: Http cookie created by NU when using OAuth2 is now secure.
* [#3385](https://github.com/TouK/nussknacker/pull/3385) Security fix: add http headers `'X-Content-Type-Options':'nosniff'` and `'Referrer-Policy':'no-referrer'`.
* [#3370](https://github.com/TouK/nussknacker/pull/3370) Feature: scenario node category verification on validation
* [#3390](https://github.com/TouK/nussknacker/pull/3390) Request-Response mode available for K8s deployment
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
* [#3524](https://github.com/TouK/nussknacker/pull/3524) Change base Docker image to Eclipse Temurin due to OpenJDK image deprecation.
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
* [#3169](https://github.com/TouK/nussknacker/pull/3169) API endpoint `/api/app/healthCheck` returning short JSON answer with "OK" status is now not secured - you can use it without authentication
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
  Universal Kafka source/sink, handling multiple scenarios like: Avro message for Avro schema, JSON message for JSON schema. Legacy, low level Kafka components can be turned on by new lowLevelComponentsEnabled flag
  * [#3317](https://github.com/TouK/nussknacker/pull/3317) Support JSON Schema in universal source
  * [#3332](https://github.com/TouK/nussknacker/pull/3332) Config option to handle JSON payload with Avro schema
  * [#3354](https://github.com/TouK/nussknacker/pull/3354) Universal source optimization - if message without schemaId, using cache when getting one
  * [#3346](https://github.com/TouK/nussknacker/pull/3346) UniversalKafkaSink provides also 'raw editor'
  * [#3345](https://github.com/TouK/nussknacker/pull/3345) Swagger 2.2.1, OpenAPI 3.1, JSON Schema typing and deserialization same as in OpenAPI components

* [#3249](https://github.com/TouK/nussknacker/pull/3249) Confluent 5.5->7.2, avro 1.9->1.11 bump
* [#3250](https://github.com/TouK/nussknacker/pull/3250) [#3302](https://github.com/TouK/nussknacker/pull/3302) Kafka 2.4 -> 2.8, Flink 1.14.4 -> 1.14.5
* [#3270](https://github.com/TouK/nussknacker/pull/3270) Added type representing null
* [#3263](https://github.com/TouK/nussknacker/pull/3263) Batch periodic scenarios carry processing type to distinguish scenarios with different categories.
* [#3269](https://github.com/TouK/nussknacker/pull/3269) Fix populating cache in CachingOAuth2Service. It is fully synchronous now.
* [#3264](https://github.com/TouK/nussknacker/pull/3264) Added support for generic functions
* [#3253](https://github.com/TouK/nussknacker/pull/3253) Separate validation step during scenario deployment
* [#3328](https://github.com/TouK/nussknacker/pull/3328) Schema type aware serialization of `NkSerializableParsedSchema`
* [#3071](https://github.com/TouK/nussknacker/pull/3071) [3379](https://github.com/TouK/nussknacker/pull/3379) More strict Avro schema validation: include optional fields validation,
  handling some invalid cases like putting long to int field, strict union types validation, reduced number of validation modes to lax | strict.
* [#3289](https://github.com/TouK/nussknacker/pull/3289) Handle asynchronous deployment and status checks better
* [#3071](https://github.com/TouK/nussknacker/pull/3334) Improvements: Allow to import file with different id
* [#3412](https://github.com/TouK/nussknacker/pull/3412) Corrected filtering disallowed types in methods
* [#3363](https://github.com/TouK/nussknacker/pull/3363) Kafka consumer no longer set `auto.offset.reset` to `earliest` by default. Instead, Kafka client will use default Kafka value which is `latest`
* [#3371](https://github.com/TouK/nussknacker/pull/3371) Fix for: Indexing on arrays wasn't possible
* [#3376](https://github.com/TouK/nussknacker/pull/3376) (Flink) Handling Kafka source deserialization errors by exceptionHandler (https://nussknacker.io/documentation/docs/installation_configuration_guide/model/Flink#configuring-exception-handling)

1.4.0 (14 Jun 2022)
------------------------
* [#2983](https://github.com/TouK/nussknacker/pull/2983) Extract Permission to extensions-api
* [#3010](https://github.com/TouK/nussknacker/pull/3010) Feature: Docker Java Debug Option
* [#3003](https://github.com/TouK/nussknacker/pull/3003) Streaming-lite runtime aware of K8s resource quotas
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
* [#2841](https://github.com/TouK/nussknacker/pull/2841) Some performance improvements - reduced number of serialization round-trips for scenario JSON
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
* [#2441](https://github.com/TouK/nussknacker/pull/2441) Avro sink supports defaults of primitive avro types
* [#2498](https://github.com/TouK/nussknacker/pull/2498), [#2499](https://github.com/TouK/nussknacker/pull/2499), [#2503](https://github.com/TouK/nussknacker/pull/2503), [#2539](https://github.com/TouK/nussknacker/pull/2539) EspExceptionHandler is removed from ProcessConfigCreator.
  Flink engine uses now fixed exception handler: FlinkExceptionHandler. All deprecated FlinkEspExceptionHandler implementations are removed.
* [#2543](https://github.com/TouK/nussknacker/pull/2543) Eager parameters can have helpers injected.
* [#2493](https://github.com/TouK/nussknacker/pull/2493) Kafka configuration is now provided by components provider configuration, if not provided avroKryoGenericRecordSchemaIdSerialization default is set to true - previously false
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
  Some additional refactors done: logback configuration enhancements, simpler run.sh script, removed Docker defaults from default configs.
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
  * [#1368](https://github.com/TouK/nussknacker/pull/1368) Publish Docker images/jars via GH actions (experimental)
  * [#1381](https://github.com/TouK/nussknacker/pull/1381) Use GH Actions for coverage
  * [#1383](https://github.com/TouK/nussknacker/pull/1383) Switch github badges
* [#1382](https://github.com/TouK/nussknacker/pull/1382) First E2E FE tests
* [#1373](https://github.com/TouK/nussknacker/pull/1373) Ability to load custom model config programmatically
* [#1406](https://github.com/TouK/nussknacker/pull/1406) Eager services - ability to create service object using static parameters
* [#962](https://gihub.com/TouK/nussknacker/pull/962) New ways of querying InfluxDB for counts, integration tests, no default database name in code
* [#1428](https://github.com/TouK/nussknacker/pull/1428) Kafka SchemaRegistry source/sink can use JSON payloads. In this PR we assume one schema registry contains either JSON or Avro payloads but not both.
* [#1445](https://github.com/TouK/nussknacker/pull/1445) Small refactor of RecordFormatter, correct handling different formatting in kafka-json in test data generation
* [#1433](https://github.com/TouK/nussknacker/pull/1433) Pass DeploymentData to process, including deploymentId and possible additional info
* [#1458](https://github.com/TouK/nussknacker/pull/1458) `PeriodicProcessListener` allows custom handling of `PeriodicProcess` events
* [#1466](https://github.com/TouK/nussknacker/pull/1466) `ProcessManager` API allows to return ExternalDeploymentId immediately from deploy
* [#1405](https://github.com/TouK/nussknacker/pull/1405) 'KafkaAvroSinkFactoryWithEditor' for more user-friendly Avro message definition.
* [#1514](https://github.com/TouK/nussknacker/pull/1514) Expose DeploymentData in Flink UI via `NkGlobalParameters`
* [#1510](https://github.com/TouK/nussknacker/pull/1510) `FlinkSource` API allows to create stream of `Context` (FlinkSource API and test support API refactoring).
* [#1497](https://github.com/TouK/nussknacker/pull/1497) Initial support for multiple (named) schedules in `PeriodicProcessManager`
* [#1499](https://github.com/TouK/nussknacker/pull/1499) ClassTag is provided in params in Avro key-value deserialization schema factory: `KafkaAvroKeyValueDeserializationSchemaFactory`
* [#1533](https://github.com/TouK/nussknacker/pull/1533) Fix: Update process with same JSON
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
* [#1799](https://github.com/TouK/nussknacker/pull/1799) ConfluentAvroToJsonFormatter produces and reads test data in valid JSON format with full kafka metadata and schema ids.
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
* [#1321](https://github.com/TouK/nussknacker/pull/1321) Exception handler accessible via custom node context, Avro record encoding errors reported by exception handler

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
* [#949](https://github.com/TouK/nussknacker/pull/949) JVM options can be configured via JDK_JAVA_OPTIONS env variable (in Docker and standalone distribution)
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
- upgrade to Flink 1.4.2
- upgrade to Scala 2.11.12
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
