---
sidebar_position: 2
---

# Basic Nodes

Nodes work with a data stream. They can produce, fetch, send, collect data or organize data flow. Each node has at least two parameters: `Name` and `Description`. Name has to be unique in a scenario. Description is a narrative of your choice.  

Most of the nodes, with source and sink nodes being notable exceptions, have input and at least one output flow.

Sinks and filters can be disabled by selecting `Disable` checkbox. 


## Variable component

A Variable component is used to declare a new variable; in the simplest form a variable declaration looks like in the example  below. As the event was read from the Kafka topic, the `#input` variable stores its content and  its value is assigned to a newly declared `myFirstVariable` variable. 


![what is this about](img/variableDeclarationInScenario.png "Scenario with variable declaration")


As you can see in the `variable` configuration form below, Nussknacker inferred the data type of the `#input` variable from the information already available to Nussknacker. 

![alt_text](img/variableDeclarationForm.png "Variable declaration form")


In the next example `#input` variable is used to create an expression returning a boolean value. If the input Kafka topic contains json objects and they contain `operation` field, the value of this field can be obtained in the following way: 


`#input.operation` 

Note that internally Nussknacker converts JSON’s object into SpEL’s map. 



![alt_text](img/simpleExpression.png "image_tooltip")


## mapVariable 

The specialized `mapVariable` component can be used to declare a map variable (object in JSON)


![alt_text](img/mapVariableMapForm.png "mapVariable form")


The same can be achieved using a plain `Variable` component, just make sure to write a valid SpEL expression. 


![alt_text](img/mapVariableBasicForm.png "mapVariable declaration using a plan Variable component")

## Filter 
   
Filter passes records which satisfies filtering condition. It can have one or two outputs. 
![filter graph](img/filter_graph.png)

Records from the `source` which meet filter's condition go to the `true sink`, and others go to the `false sink`. 

![filter graph single](img/filter_graph_single.png)

Records from the `source` which meets condition go to the `blue sink`, and others are filtered out. 

![filter window](img/filter_window.png)
The Expression field should contain the SpEL expression for the filtering conditiona and should produce a boolean value

## Split 
 
Split node logically splits processing into two or more parallel branches. Each branch receives all records and processes them independently. 

![split graph](img/split_graph.png)

Every record from the `source` gos to `sink 1` and `sink 2`. Split node doesn't have additional parameters.



## Switch
   
Switch distributes incoming records among output branches in accordance with the filtering criteria configured in those branches.
 
![switch graph](img/switch_graph.png)

Each record form the `source` is tested against condition defined on the edge. If `#color` is `blue` record goes to the `blue sink`.  If `#color` is `green` record goes to the `green sink`. For every other value record goes to the `sink for others`.

![switch window](img/switch_window.png)

The Switch node takes two parameters: `Expression` and `exprVal`. `Expression` contains expression which is evaluated for each record; result is assigned to the variable configured in `exprVal` entry field - `#color` in the example above.
 
![switch_edge_condition](img/switch_edge_condition.png)

Eeach edge outgoing from `Switch` node has a boolean expression attached to it; if the expression evaluates to true the record is allowed to pass through this edge. Record go to the first output with matching condition. *Order of matching outgoing edges is not guaranteed.*

![switch_edge_default](img/switch_edge_default.png)

There can be at most one edge of type `Default`, and it gets all records that don't match any `Condition` edge. 


## Union

![union_window](img/union_window.png)

Union merges multiple branches into one branch. Events from the incoming branch are passed to the output branch without an attempt to combine or match them. 
The #input variable will be no longer available downstream the union node; a new variable will be available instead; it is defined in the union node configuration form.


Branch names visible in the node configuration form are derived from node names preceding the union node.

Example:
![union_example](img/union_example.png)

Entry fields:
- Output Variable Name - the name of the variable containing results of the merge.
- Output Expression - the value of this expression will be passed to the output branch. The output expression is defined separately for each input branch.

Please note, that the #input variable used in the Output expression field refers to the content of the respective incoming branch.

## UnionMemo
![union_memo_window](img/union_memo_window.png)

Similarly to Union, UnionMemo node merges branches into one branch, events are emitted on every incoming event and event time is inherited from the incomming event.

There are however important differences in the way UnionMemo works:
- events from the incoming branches are matched together based on some key value
- data that arrived from any of the incoming branches will be memoized by the UnionMemo node for time duration defined in stateTimeout. If new event arrives before stateTimeout, the stateTimeout timer is reset

Example:
![union_memo_example](img/union_memo_example.png)

UnionMemo merges multiple branches into one stream. For each incoming branch two parameters are configured:
- key - it's value should be of type `String`, defines how elements from branches will be matched together
- value - the value of this expression which will be put the field with name the same as branch id

#input variable is no longer available downstream the UnionMemo, a new variable whose name is defined by "Output variable name' parameter will be present instead:
```$json
{
  "key": `value of key expression for given event`,
  "branch1": `value of output expression if memoized, otherwise null`,
  "branch2": `value of output expression if memoized, otherwise null`,
  "branch3": `value of output expression if memoized, otherwise null`,
  ...
}
```

## PreviousValue

![previous_value_window](img/previous_value_window.png)

`previousValue` stores arbitrary value for the given key. This element has two parameters:
- groupBy - expression defining key for which we compute aggregate, e.g. `#input.userId`
- value - stored value

For example, given stream of events which contain users with their current location, when we set 
- groupBy is `#input.userId`
- value is `#input.location`

then the value of output variable is the previous location for current user. If this is the first appearance of this user, **current** location will be returned.


## Delay

Holds event in the node until 
  *event time* + `delay` >= max (*event time* ever seen by the delay node). 

The `key` parameter will be removed in the future release of Nussknacker, for the time being configure it to `#inputMeta.key`.

## DeadEnd

![dead_end_window](img/dead_end.png)

`dead-end` is a special type of the sink that sends your data into the void. 
It is handy when you want to end your scenario without specifying exact data sink at the moment. 