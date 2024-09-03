---
sidebar_position: 1
---

# Introduction

## Intended audience

Nussknacker provides a drag and drop visual authoring tool (Nussknacker Designer). This tool allows the user to define decision algorithms – we call them scenarios – without the need to write code. This document is intended for those who will use Nussknacker Designer to configure the logic used to process data using Nussknacker scenarios. Nussknacker is a low-code platform; prior knowledge of SQL, JSON and familiarity with concepts like variables and data types will help master data processing with Nussknacker. 

**Please try the [Demo](/quickstart/demo) to quickly understand how to move around Nussknacker Designer, create a simple scenario and see SpEL in action.**

&nbsp;
## Nussknacker scenario diagram

In the Nussknacker scenario diagram, we illustrate how Nussknacker functions as a decision-making algorithm represented in a graph. This scenario guides the processing of various types of data, ranging from website clicks and bank transactions to sensor readings. By applying the scenario template to input data, Nussknacker produces output, either in the form of processed data or information detailing the decisions made, depending on the scenario's specifications.

Every scenario has to start with a datasource - we have to specify what kind of data we want to work with. In Nussknacker we just name it "source". The rest of the scenario is a sequence (Directed Acyclic Graph or DAG to be more precise) of different nodes:
- flow control functions: filter, switch, split etc.
- data enrichments from external sources (JDBC, OpenAPI)
- aggregates in different types of time windows (available with Flink engine)
- custom, tailor-made components, which extend default functionality
- and more

The nodes affect the data records as they flow through the scenario. In a typical scenario, you first check if a particular situation (data record) is of interest to you (you [filter](./BasicNodes.md#filter) out the ones that aren't). Then you fetch additional information needed to make the decision ([enrich](./Enrichers.md) the event) and add some conditional logic based on that information ([choice](./BasicNodes.md#choice)). If you want to explore more than one alternative, you can at any point [split](./BasicNodes.md#split) the flow into parallel paths. At the end of every scenario is a sink node (or nodes if there are parallel paths which haven't been [merged](./BasicNodes.md#union)). 

In the **Streaming** [processing mode](../about/ProcessingModes.md) the data records processed by a scenario are called events. They are read from Kafka topics and processed by an [engine](../about/engines/Engines.md) of choice: Flink or Lite. Events enter the scenario "via" a source node. The nodes process events; once the node finishes processing of an event, it hands it over to the next node in the processing flow. If there is a [split](./BasicNodes.md#split) node, the event gets "multiplied" and now two or more events "flow" in parallel through branches of the scenario.  There are also other nodes which can "produce" events; for example the [for-each](./BasicNodes.md#foreach) node or [time aggregate](AggregatesInTimeWindows.md) nodes. Finally, some nodes may terminate an event - for example the [filter](./BasicNodes.md#filter) node. The important takeaway here is that a single event that entered a scenario may result in zero, one or many events leaving the scenario (being written to Kafka topic).

In the **Request-Response** processing mode it is a request data record which enters a scenario. The best and easiest way to understand how this request will be processed by Nussknacker's scenario is to think of it as of Streaming mode with a singular event. All the considerations from the previous paragraph apply. The most important trait of a **Request-Response** scenario is that it's synchronous: some other computer system sends a request to Nussknacker and awaits a response. That request is the input to the scenario and the output - the decision - is a response. Since the other system is awaiting a response, there has to be exactly one. The natural question to ask is what will happen when there are nodes in the scenario which "produce" additional data records - for-each or split. The topic of how to handle such situations is covered  [here](RRDataSourcesAndSinks.md#scenario-response-in-scenarios-with-split-and-for-each-nodes). 

&nbsp;
## SpEL

Configuring Nussknacker nodes is about using SpEL to a large degree; knowledge of how to write valid SpEl expressions is an important part of using Nussknacker.

SpEL [Spring Expression Language](https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/expressions.html) is a powerful expression language that supports querying and manipulating data objects. What exactly does the term _expression_ mean and why is SpEL an _expression language_? In programming language terminology, an _expression_ is a union of values and functions that are joined to create a new value. SpEL only allows you to write expressions; therefore it is considered an expression language. A couple of examples:

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

SpEL is used in Nussknacker to access data processed by a node or expand a node's configurability. For instance:


* create a boolean expression (for example in filters) based on logical or relational (equal, greater than, etc) operators
* access, query and manipulate fields of a data record
* format data records written to sinks
* provide helper functions like getting current date and time 
* access to system variables
* and many more.

The [SpEL Cheat Sheet page](Spel.md)  provides an exhaustive list of examples on how to write expressions with SpEL.

&nbsp;
## Data Types

Every SpEL expression returns a value of one of the predefined SpEL data types, like integer, double, boolean, map, etc. Data types in Nussknacker can be a confusing aspect at the beginning, as depending on the context in which data is processed or displayed, different data type schemes will be used - please refer to the [SpEL Cheat Sheet page](Spel.md#data-types-and-structures) for more information. 

In some contexts data type conversions may be necessary - conversion functions are described [here](Spel.md#type-conversions).

&nbsp;
## Variables

Nussknacker uses variables as containers for data. Variables have to be declared; a `variable` or `record-variable` component is used for this. Once declared, a hash sign `"#"` is used to refer to a variable from a SpEL expression. Variables are atrributes of a data record, they do not exist by themselves. 

There are three predefined variables: `#input`, `#inputMeta` and `#meta`. 

In **Streaming** processing mode the `#input` variable is associated with an event that has been read from a Kafka topic. In the **Request-Response** processing mode the `#input` variable carries the request data of a REST call which invoked Nussknacker scenario. Both in the Streaming and Request-Response cases some nodes not only terminate input events but also create new ones. As the result, the `#input` data record is no longer available after such a node. The newly created data record (and the variable associated with it) is available "downstream" -in subsequent nodes. 


If the event originated from a Kafka topic, the metadata associated with this event is available in an `#inputMeta` variable. The following meta information fields are available in `#inputMeta`:
* headers
* key
* leaderEpoch
* offset
* partition
* timestamp 
* timestampType 
* topic. 
Consult Kafka [documentation](https://kafka.apache.org/33/javadoc/org/apache/kafka/clients/consumer/ConsumerRecord.html) for the exact meaning of those fields. 


The `#meta` variable carries meta information about the scenario under execution. This variables' contents can change during scenario execution as it's a dynamically allocated variable. The following meta information elements are available:

* processName - name of the Nussknacker scenario
* properties  

Check [Basic Nodes](BasicNodes.md#variable) page for examples how to use variables. 

&nbsp;
