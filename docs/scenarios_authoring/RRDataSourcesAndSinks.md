---
sidebar_position: 4
---

# Request-Response specific components

## Concepts

### General
In Request-Response mode you interact with Nussknacker Lite runtime engine in the same way as with any "server" in the
Internet. You send request which contains request parameters and you get response from the server. The protocol for this
communication is HTTP - the same protocol is used by browsers when they communicate with web servers. When the  browser is used to browse internet pages, the web server responds with content encoded as HTML. In Nussknacker case, the Nussknacker Lite engine ("the server") respondes with content encoded as JSON. You may envoke Nussknacker Lite engine directly from the browser and watch the JSON response in the browser window. 

Not very surprisingly, in the Request-Response processing mode the only possible source component is request and the only sink component is response.

### Schemas
Unlike in the streaming processing mode, there is no Schema Registry which store data schemas - request and response schemas in our case. Still, the data schema of the request and response ("input" and "output") need to be declared to Nussknacker; they are defined alongside the scenario (as the scenario property). The "language" used to define schemas is called [JSON Schema](https://json-schema.org/). Nussknacker uses those schemas to understand how the request and response should look like, assist you with hints and validate expressions you write. 

### Is Nussknacker request response scenario a decision tree?
The way in which the scenario diagram should be interpreted in the Request - Response processing mode requires some attention. The request - response scenario diagram is not a [decision tree](https://en.wikipedia.org/wiki/Decision_tree). During a decision tree "processing" one and only one "branch" of a tree is "activated"; "processing" ends up in one of the end leaves. As in a decision tree each leaf represents a decision (or result) - one decision (result) is generated.

In Request-Response processing mode, the diagram is more like a flow chart. The data record processed by the scenario (#input at the very start of the processing) "flows" through the scenario. If there are [splits](./BasicNodes.md#split), the data records start to "flow" in parallel through many branches. If there are [for-each](./BasicNodes.md#foreach) nodes, multiple records are produced as the result. Because the Request-Response scenario can return only one response, a question arises what exactly will be returned in such cases and how to - if neccessary - return all data records produced in a scenario. 

### Scenario response in scenarios with split and for-each nodes
If parallel branches end with response nodes and more than one response is generated (e.g there is no filtering after split), the only response which is returned is the one which was chronologically generated first. If no response is generated (e.g. after filtering), an error is returned (in the future this behaviour may change).

If you want to to return data from multiple parallel branches, use [union](./BasicNodes.md#union) node to merge branches and [collect](./BasicNodes.md#collect) node to collect data records into a list. Your scenario should have only one response node.

If you use for-each node and need to collect all the results into a list of values, use [collect](#collect) node.

## Components
### Common configuration - Request-Response schema

See [schemas](#schemas) subsection of the Concepts section for the rationale behind schemas. 

Click Properties icon on the right panel to define input and output schemas.

![RR schema](img/rrProperties.png "RR properties")

### Request-Response source

The request node is very simple - it does not require any configuration. Just drop it on the diagram and proceed with authoring the scenario.

### Request-Response sink

The `response` sink configuration form will show a list of fields defined in the [Output schema](#common-configuration---request-response-schema).

![RR sink](img/rrSink.png "Kafka sink")

If you prefer to define the whole response as one expression containing value of the response instead of filling
individual fields, you can do that by switching `Raw editor` to `true`.

### Collect

![collect](img/collect.png)

As described in the [Concepts section](#concepts), there are cases when during single invocation of the request - response scenario, multiple data records start to "flow" through the scenario. Just to reiterate, this situation happens in the following cases:
- as the result of the execution of the [for-each](./BasicNodes.md#) node,
- if [split](./BasicNodes.md#split) node is used

In such cases, the `collect` node provides a convenient way of collecting all these records into a list. If data records are in parallel branches, a [union](./BasicNodes.md/#union) node should be used  to merge the branches first.

Collect node takes two arguments:
- Input expression - this expression will be evaluated for all data "records' which "flow" through the scenario and will be collected into a list. 
- Output variable name - name of the variable which will store the above mentioned list.

Example:
- For-each node executes on a list `{"one", "two", "three"}`, the output variable is `Element`.
- The subsequent variable node defines `elementSize` variable; `#Element.length` (returns length of the string) is an expression defining this variable. Because this node is "after" the for-each node, it will execute as many times as there are elements in the list on which for-each node executed, producing 3 #elementSize records.
- Collect node collects all occurences of `#elementSize` record into a list.

The output from collect node will be a list: `{3, 3, 5}`.

_Collect is designed to be used in simple collect cases, it might not work as expected in nested structures (like for-each inside for-each)_
