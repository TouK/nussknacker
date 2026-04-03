---

# front matter metadata for Docusaurus
title: FAQ
description: Frequently Asked Questions
sidebar_label: FAQ

# front matter metadata for LLM

# keywords: []
---


# Frequently Asked Questions

## What is Nussknacker?

Nussknacker is a **visual platform for building and operating real-time decisioning and event-driven algorithms**. Instead of implementing streaming logic entirely in code, Nussknacker allows developers and domain experts to **author real-time algorithms visually** using a graphical scenario editor composed of reusable processing components. 

In the streaming [processing mode](/docs/about/ProcessingModes.md) these algorithms run on top of **Apache Flink** and  process events from **Apache Kafka** or sources which provide tabular data - for example relational databases or table formats.The resulting scenarios are executed on Apache Flink, which provides the underlying distributed stream-processing capabilities. 

In the request-response processing mode the algorithms are processed by the Apache Pekko based runtime.

Read [Nussknacker overview](/docs/about/Overview.mdx) to find more about Nussknacker.

---

## Is Nussknacker a stream processing engine?

No.

Nussknacker **is not a stream processing engine**. In the streaming and batch processing modes it runs on top of **Apache Flink**, which performs the actual distributed stream (or batch) processing.

Nussknacker provides:

- a graphical environment for designing streaming algorithms
- testing and debugging tools
- operational capabilities for deploying and managing scenarios

Apache Flink remains the execution engine responsible for state management, scaling, and fault tolerance.

---

## How are Nussknacker scenarios executed?

Nussknacker scenarios are deployed in one of the execution modes.

In the **streaming mode**, scenarios are deployed as streaming jobs that run on Apache Flink clusters and process events from systems such as Apache Kafka.

In the **request-response mode**, scenarios run inside an HTTP service powered by the Apache Pekko runtime and process incoming requests synchronously.

In the **batch** processing mode, scenarios are deployed to Apache Flink cluster as a batch job.

All modes use the same visual scenario model and components, allowing teams to implement real-time and batch algorithms. 

---

## What actually runs on the Flink cluster when a Nussknacker scenario is deployed?

When a streaming or batch scenario is deployed, Nussknacker generates a regular Apache Flink job.

The job is built programmatically using Flink APIs and then submitted to the Flink cluster like any other Flink application.

All standard Flink capabilities remain in use, including:

- parallel execution
- state management
- checkpointing
- fault tolerance
- integration with Flink connectors

Nussknacker focuses on **authoring, testing, and operating the algorithm**, while Apache Flink remains responsible for executing the job.

---

## How is Nussknacker different from Apache Flink?

Apache Flink is a **low-level distributed stream processing engine**.

Developers typically write Flink applications using the Java, Scala, or SQL APIs. This requires programming skills as well as familiarity with the Flink execution model and APIs.

With Nussknacker, users primarily interact with the data carried in events and the algorithm graph that processes them. In many simple cases — and in practice many scenarios fall into this category — users may not even notice that they are writing expressions.

When more complex logic is needed, the experience typically resembles writing spreadsheet formulas: expressions combine field references and helper functions to produce the desired result. This allows developers and domain experts to focus on algorithm logic rather than the underlying stream processing infrastructure.

Nussknacker provides a **higher-level environment for designing and operating streaming algorithms**, including:

- a graphical scenario editor
- reusable processing components
- testing and debugging tools
- operational deployment features

In short:

| Apache Flink | Nussknacker |
|---|---|
| Distributed stream processing engine | Visual authoring platform |
| Applications written in code that interact with low-level Flink or HTTP APIs | Scenarios built visually, spreadsheet style expressions used to interact with data |
| Infrastructure-level tool | Algorithm development and operations tool |

---

## Is Nussknacker a graphical overlay on Flink SQL?

No.

Nussknacker is **not a graphical representation of Flink SQL queries**. The [components](/docs/about/GLOSSARY.md#component) used to build scenarios internally use both the **Flink Table API** and the **Flink DataStream API**, allowing them to take advantage of the strengths of both approaches. 

Scenarios often contain logic that is difficult or awkward to express using SQL alone, for example:
- complex conditional branching
- external service calls (for example ML inference, LLM interactions, or data enrichment)
- fine-grained stateful logic - for example closing a session window based on a custom condition
- workflows that combine multiple processing patterns within a single algorithm
Nussknacker represents such logic as explicit processing graphs composed of reusable components rather than large SQL queries.

Additionally, testing, debugging, and observing algorithm behavior can be difficult when logic is expressed purely in SQL. Nussknacker provides these capabilities at the scenario graph node level, allowing users to inspect intermediate data and significantly improving support for algorithm development and experimentation.

For a deeper discussion of this topic, see the Nussknacker blog post: [Why Streaming SQL Is Not the Right Tool for Authoring Event-Driven Stream-Based Algorithms](https://nussknacker.io/blog/why-streaming-sql-is-not-the-right-tool-for-authoring-event-driven-stream-based-algorithms/)



---
## How good is Nussknacker support for data transformations?

Nussknacker provides strong support for data transformations within real-time streaming systems.  
These transformations are often used as part of event-driven algorithms, but they can also support streaming ETL-style pipelines.

Typical transformation capabilities include:

- field transformations
- event enrichment
- conditional logic
- data filtering
- mapping structures into new event formats
- JSON manipulation
- list (array) processing and transformations

The expression language provides access to Nussknacker helper functions for common tasks such as:

- string processing
- date and time operations
- list and collection transformations
- numerical calculations
- random data generation

These functions are similar in spirit to the helper functions available in spreadsheet formulas,  making them easy to use for both developers and domain experts.

Read [SpEL cheat sheet](/docs/scenariosAuthoring/SpEL.md) for quick overview of the capabilities provided by the expression language used by Nussknacker. Also, browse [transforming data with Nussknacker](/docs/scenariosAuthoring/functionsAndMethods.md) for the listing of available functions and methods.

---

## Is Nussknacker a low-code platform?

Yes.

Nussknacker can be described as a **low-code environment for building real-time streaming algorithms**.

Domain experts can design algorithms visually using predefined components, while developers extend the platform by implementing:

- custom components
- integrations
- user-defined functions
- domain-specific logic

This approach allows teams to combine **developer extensibility** with **visual algorithm authoring**.

---


## When should you use Nussknacker instead of writing Flink code?

Nussknacker is particularly useful when:
- algorithm logic needs to be easy to understand and maintain across teams, not only by the original developers
- domain experts participate in algorithm design
- real-time algorithms must change frequently
- rapid experimentation and short feedback loops are important when developing algorithms
- visibility and debugging of streaming logic are important
- teams want to avoid maintaining large codebases of streaming jobs
- streaming SQL queries become difficult to express or maintain as the algorithm grows in complexity

In these situations, visual scenarios can make streaming logic easier to understand and evolve.

---

## How difficult is it to use the Nussknacker expression language?

For many scenarios, users may not even realize they are using an expression language. If no data transformations are required and the event payload can be used as-is, expressions are typically limited to simple field references and conditions, for example:


`#input.price` – value of the `price` field in the input record 
&nbsp
`#input.price < 200` – condition that can control the behavior of components such as filter, switch, decision table, or session window

In these cases, expressions resemble simple property access and basic comparisons rather than programming.

To make this even easier, Nussknacker provides a **visual drag-and-drop wizard** for building expressions. Instead of writing JSON paths manually, users can simply select and drag the fields they want to reference from the input structure.

When data transformations are required, Nussknacker offers a set of built-in helper functions for working with:

- strings
- dates and times
- lists (arrays)
- numbers
- JSON structures

The level of complexity is typically comparable to writing formulas in **spreadsheets**: expressions combine field references and helper functions to produce the desired result.

---

## What Nussknacker is NOT

To avoid common misunderstandings:

Nussknacker is **not**:

- a stream processing engine
- a replacement for Apache Flink
- a streaming database
- a graphical representation of SQL queries


Instead, Nussknacker is a **visual platform for designing and operating real-time event-driven algorithms**.

---

## Who uses Nussknacker?

Nussknacker is used by organizations that operate **large-scale event-driven systems**, particularly in industries where real-time decisioning is important.

Typical sectors include:

- telecommunications
- financial services
- logistics and transportation
- large online platforms

These environments often require processing large volumes of events while applying complex decision logic.

---
## Is Nussknacker open source?

Nussknacker is available as an **open-source project**, with additional enterprise capabilities available from Nussknacker P.S.A.
