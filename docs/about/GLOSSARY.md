---
title: Glossary
---

### Component

Components are building blocks of [scenarios](./GLOSSARY.md#scenario). Once a component is used in 
a [scenario](./GLOSSARY.md#scenario), it becomes a [node](GLOSSARY.md#node).

There are different types of components, each performing a different type of processing. An example of a basic component 
would be a filter, which filters only those records which match certain criteria. An example of a complex component 
would be a session window, which can perform aggregations in a session window.

Some components need to be configured before they can be used from the Designer. Enrichers are a good example of such 
components - database connection information, openAPI service location, etc. are configured in the component configuration.
     
For more information about the available components check [Basic nodes](../scenarios_authoring/BasicNodes.md), 
[Data sources and sinks](../scenarios_authoring/DataSourcesAndSinks.md), 
[Aggregates in time windows](../scenarios_authoring/AggregatesInTimeWindows.md) and 
[Enrichers](../scenarios_authoring/Enrichers.md).

### Deployment Manager

Part of [Nussknacker Designer](./GLOSSARY.md#nussknacker-designer) which deploys [scenarios](./GLOSSARY.md#scenario) 
to the [engine](GLOSSARY.md#engine) where they are processed. 

### Engine

Runtime platform, where  [scenarios](./GLOSSARY.md#scenario) authored with 
[Nussknacker Designer](./GLOSSARY.md#nussknacker-designer) are processed.

Nussknacker can use one of two engines:
- Lite - scenarios are deployed as microservices on K8s
- Flink - (only Streaming mode) scenarios are deployed as Flink jobs

### Model

Nussknacker is a highly configurable tool, it can work with different [engines](./GLOSSARY.md#engine), multiple 
instances of the same engine, multiple [components](./GLOSSARY.md#component) configurations. The term model is used 
to integrate all the 'moving parts' which work behind the scenes of the scenario. Model comprises:
* components and helper variables
* expressions language configuration
* listeners
* error handling strategy

### Node

A node is a [component](./GLOSSARY.md#component) used in a [scenario](./GLOSSARY.md#scenario). You can use the same 
component many times (for example you can have many filters in one scenario) and each instance is a node of the scenario.

Almost all nodes take parameters. For example a filter node takes a boolean expression as a parameter. This expression 
is evaluated at runtime to decide whether a given event should pass the filter node.
  
   
### Nussknacker Designer

Part of Nussknacker where authoring of [scenarios](./GLOSSARY.md#scenario) is performed.
   
### Processing mode
Processing mode defines how [scenario](./GLOSSARY.md#scenario) deployed on an [engine](./GLOSSARY.md#engine) 
interacts with the outside world. Currently, there are two processing modes: Streaming and Request-Response, 
you can read more [here](./ProcessingModes.md).
      
### Scenario

Scenario describes a decision algorithm i.e. how events should be processed and what actions should be performed. 
Scenarios are authored by means of [Nussknacker Designer](./GLOSSARY.md#nussknacker-designer). Scenarios are 
composed of [nodes](./GLOSSARY.md#node).
