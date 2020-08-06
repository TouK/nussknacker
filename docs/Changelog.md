Project status
==============
Nussknacker is production ready - it's been used in major Polish telcom since beginning of 2017. However, it's still
under active development, so some parts of the API may change in the future.

For detailed instructions how to migrate to the newest version please see [migration guide](MigrationGuide.md).

Nussknacker versions
====================

0.2.0 (not released yet)
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

0.1.2
------------------------
* [#965](https://github.com/TouK/nussknacker/pull/965) Added new, 'aggregate-tumbling' node.
* [#957](https://github.com/TouK/nussknacker/pull/957) Custom node `aggregate` has now additional aggregation function `Sum`.
 Also was changed parameter from `windowLengthInSeconds` to `windowLength` with human friendly duration input.

0.1.1
------------
* Branch parameters now can be eager (computed during process compilation)
* More restrictive type checking in SpEL - mainly added verification of types of method's paramaters
* Added support for Kafka consumer group strategies - setted up by `kafka.consumerGroupNamingStrategy` configuraton option
* Bugfixes for joins

0.1.0
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


Compatibility matrix
====================

Table below contains versions of libraries/apps that can be used with Nussknacker 

|Nussknacker| Flink | Kafka  | InfluxDB | Grafana |
|-----------|-------|--------|----------|---------|
| master    |**1.7.2**|0.11.0.2|1.2.0     | 5.4.0   |
| 0.0.12    |1.7.2  |0.11.0.2| 1.2.0    | 5.4.0   |
| 0.0.11    |1.6.1  |0.11.0.2| 1.2.0   | 5.4.0  |
| 0.0.10    |**1.6.1**  |0.11.0.2| 1.2.0    | 5.4.0   |
| 0.0.9     |1.4.2  |0.11.0.2| 1.2.0        | 5.4.0       |
| 0.0.8     |1.4.2  |0.11.0.2| 1.2.0    | 4.0.1   |
| 0.0.7     |1.3.1  |0.9.0.1 | 1.2.0    | 4.0.1   |
| 0.0.6     |1.3.1  |0.9.0.1 | 1.2.0    | 4.0.1   |


