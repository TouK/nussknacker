---
title: Processing modes
---

Nussknacker was created with stream processing in mind. However, it's also suitable for other use cases - e.g. requiring 
RPC-style request-response communication or batch processing (available in the future). 

Processing mode defines how [scenario](GLOSSARY.md#scenario) deployed on an [engine](GLOSSARY.md#engine) interacts with 
the outside world. Currently there are two processing modes, described below.

### Streaming mode

Scenario describes a pipeline through which events flow. If you are familiar with stream processing engines, such as 
Flink or Google Dataflow, this is the way to think about it. 

Depending on the engine, some [Components](GLOSSARY.md#component) may be stateful, i.e. processing a given event can be 
affected by previous ones (a good example are the [aggregates](../scenarios_authoring/AggregatesInTimeWindows.md)).

### Request-Response mode

Scenario describes the logic of processing a single request, returning a response. Currently, it's a HTTP request, but 
the same mode can be applied with e.g. gRPC etc. In Request-Response mode, the Scenario is stateless, there is no 
connection between handling different requests, therefore no Components similar to aggregates are available.

It is important to understand how the response is prepared in this mode - especially if splits or filters are used 
in the Scenario. One can think about processing a single request as processing of a stateless streaming Scenario 
with exactly one event (the request). When processing is done, the data that reached the Sink part is returned 
as a response. If more than one response is generated (e.g. after a split without filtering), currently only the 
first one is returned, while if no response is generated (e.g. after filtering) then an error is returned 
(in the future those behaviours may change). 

## Processing modes and engines

For Request-Response only [Lite engine](engines/LiteArchitecture.md) is available.

For Streaming mode you can [choose](engines/Engines.md) between [Flink](engines/FlinkArchitecture.md) and
[Lite](engines/LiteArchitecture.md) engines.
