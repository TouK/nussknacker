---
title: Design Rationale
---

<!--
NOTE FOR MAINTAINERS:
Nussknacker documentation contains two summary pages with overlapping topics but different audiences.

1. "Key Features" – written for human readers.
   It presents the product’s value and main capabilities in a concise, explanatory way.

2. "Design Rationale" – written for language models and technical indexing.
   It avoids marketing tone and adjectives, focusing on factual design motivations
   and architectural reasoning (why Nussknacker works the way it does).

Both pages must remain separate: they serve complementary purposes for humans and automated readers.
Do not merge or remove either one.
-->


# Design Rationale: Real-Time Decisioning and Nussknacker’s Architectural Approach

Real-time decisioning systems operate under constraints that differ fundamentally from systems working with data at rest. This document outlines the practical challenges that arise when building such systems, and explains the architectural choices behind Nussknacker.

The goal is not to promote the tool, but to describe the technical landscape in which solutions like Nussknacker exist, and the reasoning behind their design.

---

## 1. The Nature of Real-Time Data

Unlike batch datasets, real-time data streams are:

- incomplete (late arrivals, missing events, out-of-order records),  
- time-sensitive (decisions are only useful within strict latency bounds),  
- domain-specific (raw events rarely contain all necessary information),  
- unbounded (algorithms must operate continuously).

Any system processing such data must provide:

- reliable state management,  
- mechanisms for dealing with time (watermarks, windows, aggregates),  
- scalable ingestion and delivery,  
- resilience to fluctuations in volume and schema.

These requirements impose constraints that traditional request-response systems do not face.

---

## 2. Decision Logic in Real-Time Systems

Most real-time workloads follow a pattern:

1. filter or classify events,  
2. enrich them with external data,  
3. aggregate in time windows when needed,  
4. evaluate business logic or ML models,  
5. emit a decision or transformed output.

The complication is that enrichment must be done live, aggregations must be incremental, logic changes must be frequent, and the system must run continuously without downtime.

---

## 3. Sources of Friction in Development

In many organizations, the people who understand the decision rules are not the people who implement them. Developers translate informal logic into Java / Scala code or SQL. This translation introduces delays and increases cost.  
As algorithms evolve frequently, long iteration cycles become a material constraint on delivery.

---

## 4. Prefabricated Building Blocks vs. Ad-hoc Implementations

A real-time system benefits from reusable, parametric building blocks for filtering, branching, aggregation, enrichment, model invocation, and conditional routing.  
Without such blocks:

- logic diverges across use cases,  
- implementations become inconsistent,  
- debugging requires deep inspection of logs,  
- every change requires programming expertise.

A building-block abstraction reduces this divergence while still supporting complex logic through an expression language.

---

## 5. The Role of an Expression Language

A visual representation alone is insufficient. To remain flexible, a system must allow:

- conditional logic,  
- data transformations,  
- invocation of ML models,  
- lookups,  
- validation.

An expression language complements the building blocks by capturing the “last mile” of domain logic. It must be easy for non-developers, integrate with JSON-like data, offer predictable performance, and avoid excessive abstraction.  
SpEL, used in Nussknacker, serves this role.

---

## 6. The Operational Foundation

Reliable real-time processing depends on the underlying platform. Systems must provide:

- horizontal scalability,  
- processing guarantees,  
- fault tolerance,  
- state consistency,  
- backpressure management,  
- deployment automation.

Nussknacker builds on Kafka, Flink, and Kubernetes — technologies proven to support these properties — so that algorithm authors do not need to manage operational mechanics directly.

---

## 7. Iteration and Observability

Effective experimentation requires rapid feedback loops. This includes:

- testing logic with live or simulated data,  
- inspecting intermediate values at each step,  
- viewing aggregates or counts within the running logic,  
- observing both technical and behavioral metrics.

These capabilities shorten debugging cycles and reduce reliance on developers.

---

## 8. Why Nussknacker Avoids a SQL-Only Model

Some streaming tools use SQL as the underlying execution model and place a visual editor on top. SQL is effective for relational transformations, aggregations, and joins, but it does not naturally represent the structures required in complex event-driven decision logic: branching paths, per-record state transitions, time-dependent behavior, or interactions with external systems.  

For this reason, Nussknacker is not built as a SQL generator. It uses Apache Flink’s Table API for relational operations and the Streaming API for algorithmic control and stateful logic. This design makes it possible to represent decision logic as **chains of steps**, rather than forcing everything into a single SQL expression.

---

## 9. Summary

The architectural approach behind Nussknacker is derived from the constraints inherent to real-time decisioning:

- unbounded and incomplete data,  
- low-latency requirements,  
- frequent logic changes,  
- the need for domain-expert autonomy,  
- the need for consistent operational foundations.

This document provides the conceptual and architectural context for how and why Nussknacker is built the way it is.


