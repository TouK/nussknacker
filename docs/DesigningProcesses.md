#Designing processes 

In this section you will learn how to create Nussknacker processes using defined model. In the examples we'll use sample model that comes with Nussknacker distribution. You can find latest sources [here](https://github.com/TouK/nussknacker/tree/master/engine/example/src/main/scala/).
  
If you want to learn how to develop your own model, please see [API](API.md)  

###Global process properties
* parallelism (see [Flink docs](https://flink.apache.org/faq.html#what-is-the-parallelism-how-do-i-set-it))
* checkpoint interval (see [Flink docs](https://ci.apache.org/projects/flink/flink-docs-release-{{book.flinkMajorVersion}}/setup/checkpoints.html))
* should state be kept in memory, or should RocksDB be used (see [Flink docs](https://ci.apache.org/projects/flink/flink-docs-release-{{book.flinkMajorVersion}}/ops/state_backends.html))
* properties for configuring ExceptionHandler of model

###Process variables
In the beginning there is only one variable - `input`, contains single record for processing.

###Expressions
Currently expressions in Nussknacker can be written using Spring Expression Language.
 You can find extensive documentation [here](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/expressions.html). Autocomplete function (Ctrl-Space) is available in expression input boxes. 
See [more detailed documentation](Spel.md)

#Basic nodes
Node works with a data stream. It can produce, fetch, send, collect data or organize data flow.

Each node has at least two parameters: `Name` and `Description`. Name has to be unique in process. It identifies node usage. Description is as everyone can see.

Depending on its type, node can have input and output. Input has to have exactly one assigned flow from another node. Output can have multiple flows, or none, or can have at least one obligatory.



##Subprocesses
Subprocesses let you abstract common used parts of process. See [subprocesses](designingProcesses/Subprocesses.md) for more detailed description of how they work.

##Custom transformations
More complex operations like joins or aggregations are handled by custom nodes, their usage and semantics can depend on execution engine.
Please refer to [Flink custom transformers](designingProcesses/FlinkCustomTransformers.md) for examples.
