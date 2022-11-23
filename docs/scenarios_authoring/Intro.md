---
sidebar_position: 1
---

# Introduction


## Intended audience

This document is intended for those who will use Nussknacker Designer to configure processing scenarios. Nussknacker is a low-code platform; prior knowledge of SQL, JSON and concepts like variables and data types will help master authoring of the stream processing scenarios in Nussknacker. 

**Please try [Quickstart](/quickstart/demo) to quickly understand how to move around Nussknacker Designer, create a simple scenario and see SpEL in action.**

&nbsp;
## Data records
Nussknacker nodes process data records; once the node finishes processing of the data record it hands it over to the next node in the processing flow. Filter, Split, Switch nodes behave exactly like this. 

In the Streaming [processing mode](https://docs.nussknacker.io/documentation/documentation/about/ProcessingModes) data records are often referred to as events. They are read from Kafka topics and processed by the [engine](https://nussknacker.io/documentation/about/engines/) of choice: Flink or Lite. 

In the Request-Response processing mode there is no special name for a data record, even though the same term is also used in the Streaming case. 

&nbsp;
## Nussknacker scenario diagram

Nussknacker provides a drag and drop visual authoring tool (Nussknacker Designer) allowing to define decision algorithms – we call them scenarios – without the need to write code.

A scenario is a sequence of different nodes:

- flow control functions: filter, switch, split etc.
- data enrichments from external sources (JDBC, OpenAPI)
- aggregates in different types of time windows (available with Flink engine)
- custom, tailor-made components, which extend default functionality
- and more

The scenario diagram is a classical [flowchart](https://en.wikipedia.org/wiki/Flowchart) with  parallel processing enabled. The data record processed by the scenario "flows" through the scenario. If there are [splits](./BasicNodes.md#split), the data records start to "flow" in parallel through many branches.  Multiple data records can be produced by the node; for example the [for-each](./BasicNodes.md#foreach) node. Finally, some nodes may terminate the data record - for example the [filter](BasicNodes.md#filter] node.

&nbsp;
## SpEL

Configuring Nussknacker nodes to a large degree is about using SpEL; knowledge of how to write valid expressions in SpEL is an important part of using Nussknacker.

SpEL [Spring Expression Language](https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/expressions.html) is a powerful expression language that supports querying and manipulating data objects. What exactly does the term _expression_ mean and why SpEL is an _expression language_? In programming language terminology, an _expression_ is a combination of values and functions that are combined to create a new value. SpEL allows to write expressions only; therefore it is an expression language. Couple examples:

| Expression           | Result                         | Type                 |
| ------------         | --------                       | --------             |
| 'Hello World'        | "Hello World"                  | String               |
| true                 | true                           | Boolean              |
| {1,2,3,4}            | a list of integers from 1 to 4 | List[Integer]        |
| {john:300, alex:400} | a map (name-value collection)  | Map[String, Integer] |
| 2 > 1                | true                           | boolean              |
| 2 > 1 ? 'a' : 'b'    | "a"                            | String               |
| 42 + 2               | 44                             | Integer              |
| 'AA' + 'BB'          | "AABB"                         | String               |

SpEL is used in Nussknacker to access data processed by a node and expand node's configuration capabilities. Some examples:


* create boolean expression (for example in filters) based on logical or relational (equal, greater than, etc) operators
* access, query and manipulate fields of the incoming data record
* format data records written to sinks
* provide helper functions like date and time, access to system variables
* and many more.

The [SpEL Cheat Sheet page](Spel)  provides an exhaustive list of examples of how to write expressions with SpEL.

&nbsp;
## Data Types

Every SpEL expression returns a value of one of the predefined SpEL data types, like integer, double or boolean, map, etc. Data types in Nussknacker can be a confusing aspect at the beginning, as depending on the context in which data are processed or displayed, different data type schemes are in use - please refer to the [SpEL Cheat Sheet page](Spel#data-types-and-structures) for more information. 

In some contexts data type conversions may be necessary - conversion functions are described [here](Spel#type-conversions).

&nbsp;
## Variables

Nussknacker uses variables as containers for data. Variables have to be declared; a `variable` or `mapVariable` component are used for this. Once declared, a hash sign `"#"` is used to refer to a variable from a SpEL expression. Variables are attributes of the data record, they do not exist by themselves. 

There are three predefined variables: `#input`, `#inputMeta` and `#meta`. 

In the Streaming processing mode the `#input` variable is associated with the event which originally came from the Kafka topic. In the Request-Response processing mode the `#input` variable carries the request data of REST call which invoked Nussknacker scenario. Both in the Streaming and Request-Response cases some nodes not only terminate the input events, but also create new ones. As the result, the #input data record is no longer available after such a node, while the newly created data record (and the variable associated with it) is available "downstream". 


If the event which arrived to some node originally came from the Kafka topic, the metadata associated with this event are available in `#inputMeta` variable. The following meta information fields are available in `#inputMeta`:
* headers 
* key
* leaderEpoch
* offset
* partition
* timestamp 
* timestampType 
* topic. 
Consult Kafka [documentation](https://kafka.apache.org/24/javadoc/org/apache/kafka/clients/consumer/ConsumerRecord.html) for the exact meaning of those fields. 


The `#meta` variable carries meta information about the currently executed scenario. The following meta information elements are available:

* processName - name of the Nussknacker scenario
* properties  

Check [Basic Nodes](BasicNodes#Variable-component) page for examples how to use variables. 

&nbsp;
## Notion of time | Streaming | Flink engine

Notion of passing time is very important in dealing with real time events processing. 
Please see following excellent references to learn about basic concepts:
* [Notion of time in Flink](https://ci.apache.org/projects/flink/flink-docs-stable/docs/concepts/time/)
* [Streaming 101: The world beyond batch by O'Reilly](https://www.oreilly.com/radar/the-world-beyond-batch-streaming-101/)

For Flink engine Flink documentation applies. Certain Nussknacker components make assumptions and have predefined settings, so that the end users don't have to configure all by themselves.

&nbsp;
### Sources and Sinks - Kafka

In general following rules apply:
* We use _event time_ in scenarios to handle notion of passing time
* Kafka record timestamps are used to assign event time to Flink events
* Kafka records produced by Nussknacker sinks have timestamp of event (in the sense of _event time_) that generated them
* We use *bound of order watermark generator*, with configurable amount of lateness (see [kafka.kafkaEspProperties.defaultMaxOutOfOrdernessMillis property in Configuration](../installation_configuration_guide/ModelConfiguration#kafka-configuration) for details). 

&nbsp;
### Aggregations, window processing 

If a new event is triggered by e.g. tumbling time window, its timestamp is equal to the time of the timer that generated it, not system time of the moment when it happened. See [Aggregates in Time Windows](AggregatesInTimeWindows#tumbling-window) for more details.

&nbsp;
## Notion of time | Streaming | Lite engine

Lite engine is stateless, so many concepts important for windows or aggregations do not apply, but following rules apply for Kafka sources and sinks:
* Kafka record timestamps are used to determine time of the event
* Kafka records produced by Nussknacker sinks have timestamp of event that generated them
