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

###Configuring sources and sinks

###Process variables

###Expressions

Currently expressions in Nussknacker can be written using Spring Expression Language. You can find extensive documentation [here](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/expressions.html).

###Subprocesses
TBD

###Using custom transformers
TBD
