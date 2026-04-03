---
title: Glossary
---

# Glossary

## Component

A **component** is a reusable building block of a [scenario](#scenario). Each component performs a specific type of processing, for example: filtering, transforming, aggregating, or communicating with an external system.  

Some components interact with data sources or services defined by [integrations](#integration); these include [sources](#source), [sinks](#sink), and [enrichers](#enricher).  

When a component is placed on the scenario graph, it becomes a [node](#node).  


## Deployment Manager
Part of Nussknacker [Designer](#designer) which deploys scenarios to the [engine](#engine) where they are processed.

## Designer

Part of Nussknacker where authoring of [scenarios](#scenario) is performed.

## Engine
Runtime platform, where [scenarios](#scenario) authored with Nussknacker [Designer](#designer) are processed.

Nussknacker can use one of two engines:

- Lite - scenarios are deployed as microservices on K8s.
- Flink - (only streaming [processing mode](#processing-mode)) scenarios are deployed as Flink jobs.

Depending on the installation, the engine can in Nussknacker cloud or on-premise. 

## Enricher

An **enricher** is a [component](#component) that *adds information to records already being processed in a scenario*.  
It queries an external system - for example, a database, web service, or ML models inference server - and attaches the obtained data to the current [record](#record).  

Enrichers are provided by [integrations](#integration) and typically require parameters such as endpoint location, authentication, or query details. See also [processor](#processor).


## Event

Term used in streaming [processing mode](#processing-mode) only. It is the same as [record](#record) and additionally has a creation time timestamp which lets Nussknacker apply time-based processing logic.


## Integration

An **integration** connects Nussknacker with an external system that provides or receives data - such as a message broker, database, API service, machine learning platform, or data catalog.  

Each integration defines how Nussknacker communicates with that system and automatically creates the corresponding [components](#component) (for example, *source*, *sink*, or *enricher*) that can be used in [scenarios](#scenario).  

In Cloud, integrations are configured centrally in the [Admin panel](/docs/cloudUI/admin.mdx), so shared settings - such as connection details, authentication, or serialization format - need to be entered only once and can be reused by multiple components.  


## Node

A node is an instance of a [component](#component) used in a [scenario](#scenario).

Almost all nodes take parameters. For example, a filter node takes a boolean expression as a parameter. This expression
is evaluated at runtime to decide whether a given event should pass the filter node.


## Processing mode

A **processing mode** defines how a [scenario](#scenario) handles [records](#record) - how they are received, processed, and produced.  

Each mode represents a different way of interacting with external systems and controlling the flow of data:  

- **Streaming** - processes records continuously as they arrive from sources such as message queues or event streams.  
- **Request-response** - processes one record on demand, returning an immediate result to the caller.  
- **Batch** - (planned) processes a finite collection of records, typically loaded from files or databases. 

Read [here](/docs/about/ProcessingModes.md) for more information about processing modes.

## Processor

[Component](#component) that causes or can cause side effects (for example writes a message to a log). 

## Record

A **record** is a single piece of information processed by Nussknacker.  
It can be a message received from a stream, a row read from a batch file, or a request sent to a decision service.  

Each [scenario](#scenario) reads, transforms, and produces records according to its logic, regardless of the [processing mode](#processing-mode) - streaming, batch, or request-response.  

Some [components](#component) create or emit records - for example [sources](#source) and [sinks](#sink) - while others, such as [enrichers](#enricher), only modify or extend existing ones.  

Records typically contain multiple 'data structures' ([variables](/docs/scenariosAuthoring/introduction.mdx#variables)), such as identifiers, timestamps and any other domain-specific attributes, which can be accessed and modified by the scenario.

:::note
In this documentation, **record** is a neutral term used across all [processing modes](/docs/about/ProcessingModes.md).  
In purely streaming contexts, we typically use **event** - that simply means a  record with a timestamp, which lets Nussknacker apply time-based processing logic.
:::

## Scenario

A Scenario defines how records are processed in Nussknacker. It is built as a graph of [nodes](#node), each node being an instance of a component such as a source, enricher, filter, or sink.
When you deploy a scenario, Nussknacker runs this graph in the chosen [processing mode](#processing-mode) inside its managed runtime.

Scenarios are authored by means of [Nussknacker Designer](#designer). 


## Sink

A **sink** is a [component](#component) that *sends processed records from a scenario to an external system* - such as a message broker, database, API, or analytics platform. Unlike [processor](#processor), sink can only terminate a scenario branch. 




## Source

A **source** is a [component](#component) that *reads records from an external system* and passes them into a [scenario](#scenario) for processing.  
Typical external systems include message brokers, databases, or API services.  

Each source is defined by an [integration](#integration) that specifies how records are fetched into scenarios.  




