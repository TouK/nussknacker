---
# front matter metadata for Docusaurus
title: Streaming Processing Mode
description: Understand the concepts undelying the streaming processing mode
sidebar_label: Streaming Processing Mode
# sidebar_position: 

# front matter metadata for LLM

processingModes: [Streaming]
# keywords: []
---

# Streaming Processing Mode

In Streaming [Processing Mode](/docs/about/GLOSSARY.md#processing-mode), Nussknacker continuously processes incoming events as they arrive, enabling real-time decisions and actions.

Unlike batch processing, where data is collected and processed in chunks, streaming treats data as a flow of events — such as user interactions, sensor readings, or updates in a database — and reacts to them with minimal delay. This makes it ideal for time-sensitive use cases like fraud detection, personalization, monitoring, or operational automation.

Nussknacker Cloud supports a variety of streaming data sources, allowing you to build scenarios that ingest and act on events in motion. Whether your data comes from messaging systems, change data capture (CDC) tools, or other event streams, Nussknacker helps you transform and respond to those events as they happen — all without writing complex code.


## Notion of time

Notion of passing time is very important in dealing with real time events processing. Functionality like [aggregates in time windows](/docs/components/aggregatesInTimeWindows.mdx) and [stream joins](/docs/components/singleSideJoin.mdx) needs information about event creation timestamp. This information can be made available to Nussknacker using different methods:
- events in Kafka topics have event creation time, unless overridden (see next), this information will be used by Nussknacker
- for Kafka sources you can configure a SpEL expression allowing Nussknacker to compute the event creation time.
- for [Table sources](/docs/components/tableSource.mdx), you can define [event time attributes](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/sql/create/#watermark) while configuring the [Table integration](/docs/integrations/tableAPI.mdx)

Please see following excellent references to learn about basic concepts:

* [Notion of time in Flink](https://nightlies.apache.org/flink/flink-docs-master/docs/concepts/time#/timely-stream-processing)
* [Streaming 101: The world beyond batch by O'Reilly](https://www.oreilly.com/radar/the-world-beyond-batch-streaming-101/)



## Sources and Sinks

In the streaming processing mode Nussknacker supports a wide range of sources (both bounded and unbounded) and sinks.

Unbounded sources:
[Event Generator](/docs/components/eventGenerator.mdx)

Unbounded sources and sinks:
* [Kafka](/docs/integrations/kafka.md)
* [Azure Event Hubs](/docs/stepByStepInstructions/AzureEventHubs.mdx)
* [MQTT]
* [Redpanda](/docs/stepByStepInstructions//Redpanda.md)
* [WebSocket](/docs/components/websocket.mdx)

Bounded sources and sinks:
* [Clickhouse](/docs/integrations/databases.mdx#clickhouse)
* [MariaDB](/docs/integrations/databases.mdx#mariadb)
* [MySQL](/docs/integrations/databases.mdx#mysql)
* [Oracle](/docs/integrations/databases.mdx#oracle)
* [PostgreSQL](/docs/integrations/databases.mdx#postgresql)
* [Redshift](/docs/integrations/databases.mdx#redshift)
* [Snowflake](/docs/integrations/databases.mdx#snowflake)

[Contact us](/docs/about/contact.md) if your source or sink is not supported yet.

You can have multiple sources and sinks in a scenario working in the streaming processing mode. 

## Bounded sources and streaming mode

Flink, and consequently Nussknacker can process bounded sources in the streaming mode. When all records from the bounded source are ingested and processed, the scenario simply terminates. The [aggregates in the time windows](/docs/components/aggregatesInTimeWindows.mdx) currently cannot be used in scenarios processing bounded sources, as events read from bounded sources currently have no *event time* assigned. We are planning to add the possiblity of assigning *event time* to the records read from bounded sources in the near future.

## Predefined variables

In **Streaming** processing mode the `#input` variable is associated with an event that has been read from a source and contains the data read from the source.

Some nodes specific to the streaming processing nodes (e.g. aggregates) may terminate the incoming events and create new events. In such cases, the `#input` variable will no longer be available downstream - only the newly created record (and the variables associated with it) will exist downstream.

The `#meta` variable carries meta information about the scenario under execution. This variables' contents can change during scenario execution as it's a dynamically allocated variable. The following meta information elements are available:

* processName - name of the Nussknacker scenario
* properties

If the event originated from a Kafka topic, the metadata associated with this event is available in an `#inputMeta` variable. The following meta information fields are available:

* headers
* key
* leaderEpoch
* offset
* partition
* timestamp
* timestampType
* topic

Consult Kafka [documentation](https://kafka.apache.org/33/javadoc/org/apache/kafka/clients/consumer/ConsumerRecord.html) for the exact meaning of those fields.

## Exactly-once processing

Nussknacker allows to process events using the *exactly-once* semantics; this feature is provided by Flink. 

If you use Kafka compatible sinks [contact us](/docs/about/contact.md) if you need this feature enabled. 
If you use [database](/docs/integrations/databases.mdx) sink, you need to ensure that Flink decides to use upsert rather then insert method for the events written to the sink. This is achieved by defining a primary key on the sink table. You can read more about this in the [Flink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/connectors/table/jdbc/#idempotent-writes). 

## Scenario properties

Click Kebab menu icon (three vertically aligned dots next to the scenario name) on the right panel and access the properties of the scenario.

**Scenario properties explained**

| Property name                  | Description             |
|-----------------|-------------------------|
| Name            | Scenario name |
| Checkpoint interval in seconds | Checkpointing is a key Apache Flink mechanism used to ensure that in the case of failure Flink jobs (= deployed Nussknacker scenarios) can be resumed from the last saved checkpoint [See here](https://nightlies.apache.org/flink/flink-docs-master/docs/concepts/stateful-stream-processing/#checkpointing) for explanation.        |
| Parallelism | Parallelism determines how many workers (called task managers in Flink) are employed to process events.   |
| Spill state to disk            | [Stateful transformers](/docs/components/index.mdx) need to remember data "between" events; this data is called *state*. The state can grow very large, if there is not enough RAM to keep state in memory, it can be spilled to disk.   |
| IO mode     | You can speed up processing in [enrichers](/docs/components/index.mdx) case by choosing *asynchronous*. If you choose so, it may happen that events will be leaving your scenario in different order than they arrived - so watch out if your downstream logic makes assumptions on the sequencing of events |
