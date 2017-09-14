#Designing processes with UI 

In this section you will learn how to create Nussknacker processes using defined model. In the examples we'll 
  use sample model that comes with Nussknacker distribution. You can find latest sources [here](https://github.com/TouK/nussknacker/tree/master/engine/example/src/main/scala/).
  
If you want to learn how to develop your own model, please see [API section](API.md)  

###Global process properties
These include:
* parallelism (see [Flink docs](https://flink.apache.org/faq.html#what-is-the-parallelism-how-do-i-set-it))
* checkpoint interval (see [Flink docs](https://ci.apache.org/projects/flink/flink-docs-release-{{book.flinkMajorVersion}}/setup/checkpoints.html))
* should state be kept in memory, or should RocksDB be used (see [Flink docs](https://ci.apache.org/projects/flink/flink-docs-release-{{book.flinkMajorVersion}}/ops/state_backends.html))
* properties for configuring ExceptionHandler of model

###Process variables
At beginning there is only one variable - `input`, contains single record for processing.

###Expressions

Currently expressions in Nussknacker can be written using Spring Expression Language.
 You can find extensive documentation 
 [here](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/expressions.html).

#Basic nodes

Node works with data stream.
It can produce, fetch, send, collect data or organize data flow.


Each node has at least two parameters: `Id` and `Description`. 
Id has to be unique in process. It identifies node usage.
Description is just comment for node.

Depends on type, node could have input and output. 
Input if exists has to have exactly one assigned flow from another node.
Output could have multiple flows, or none, or could have at least one obligatory.


## Filter 
### Parameters
There are two parameters: `Expression`, and `Disabled`.

Expression is written in SpEL. Should be evaluated to logical value. 
Outgoing data flow depends if result expression is true or false.

Disabled has logical value. 
If is checked, expression is't evaluated, and returns value `true`.         
### Flow
Has input and one or two outputs. 

If there is one output, only outgoing pipe is named `true`, 
and each record which expression evaluates to true passes.
Otherwise record is gone.

If there are two outputs, one pipe is named `true` and another `false`.
Each record which expression evaluates to `true` goes to `true` pipe,
and other record goes to `false` pipe.  



###Subprocesses
TBD

###Using custom transformers
TBD
