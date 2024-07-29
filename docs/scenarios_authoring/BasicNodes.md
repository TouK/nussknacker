---
sidebar_position: 2
---

# Basic Components

Nodes work with data records. They can produce, fetch, send and collect data or organize the flow. Each node has at least two parameters: `Name` and `Description`. Name has to be unique in a scenario. Description is a narrative of your choice.

Most of the nodes have both input and at least one output flow. Source and sink nodes are an exception.

Sinks and filters can be disabled by selecting `Disable` checkbox.

&nbsp;
## Variable

A Variable component is used to declare a new variable. In its simplest form a variable declaration looks like the example  below. As the data record has been read from a data source, the `#input` variable holds the data record's value. After that, the record's (`#input`) value is assigned to a newly declared `myFirstVariable` variable.

![alt_text](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_variable0.png   "Scenario with variable declaration")


As you can see in the `variable` configuration form below, Nussknacker has inferred the data type of the `#input` variable. Nussknacker can do this based on the information available from the previous components.

![alt_text](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_variable1.png "Variable declaration form")


In the next example `#input` variable is used to create an expression returning a boolean value. If the input source contains JSON objects and they contain an `operation` field, the value of the field can be obtained using the following pattern:


`#input.operation`


![alt_text](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_variable2.png "Screenshot_tooltip")

Note that internally Nussknacker converts the JSON object into a SpEL record.
&nbsp;
## RecordVariable

The specialized `record-variable` component can be used to declare a record variable (JSON object)


![alt_text](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_recordVariable0.png "record-variable form")


The same outcome can be achieved using a plain `Variable` component. Just make sure to write a valid SpEL expression.


![alt_text](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_recordVariable1.png "record-variable declaration using a plan Variable component")

&nbsp;
## Filter

Filters let through records that satisfy a filtering condition.

![filter graph single](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_filter0.png)

You can additionally also define an additional `false sink`. Records from the `source` which meet the filter's conditions are going to be directed to the `true sink`, while others end up in the `false sink`.

![filter graph](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_filter1.png)

The Expression field should contain a SpEL expression for the filtering conditions and should produce a boolean value.

![filter window](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_filter2.png)

&nbsp;
## Choice

Choice is a more advanced variant of the filter component - instead of one filtering condition, you can define multiple conditions in some defined order.
It distributes incoming records among output branches in accordance with the filtering conditions configured for those branches.

![choice graph](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_choice0.png)

After a record leaves`source` it arrives in `choice`, the record's attributes' values are tested against each of the defined conditions.  If `#input.color` is `blue`, the record ends up in `blue sink`.  
If `#input.color` is `green`,the record is sent to the `green sink`. For every other value, the record is sent to `sink for others` because condition `true` is always true.
Order of evaluation of conditions is the same as is visible in the configuration form - top to bottom. You can modify the order using the drag & drop functionality.
Order is also visible on the designer graph in an edge's (the arrow connecting the nodes) description as a number. Be aware that the layout button can change displayed order of nodes, but it has no influence on order of evaluation.

![choice window](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_choice1.png)

&nbsp;
## Split

Split node logically splits processing into two or more parallel branches. Each branch receives all data records and processes them independently and in parallel.

In the Request - Response processing mode you can use this feature to paralellize and hence speed up the processing. You must use a sequence of [Union](./BasicNodes.md#union) and [Collect](./RRDataSourcesAndSinks.md#collect) nodes to merge parallelly executed branches and collect the results from these branches. A discussion of Request - Response scenario with multiple branches can be found [here](./RRDataSourcesAndSinks.md#scenario-response-in-scenarios-with-split-and-for-each-nodes).
In the Streaming processing mode the most typical reason for using a Split node is to define dedicated logic and dedicated sink for each of the branches. 

![split graph](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_split0.png)

Example: (Streaming processing mode) - every record from the `source` goes to `sink 1` and `sink 2`. 

Split node doesn't have additional parameters.

&nbsp;
## ForEach

![for_each](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_foreach0.png)

`for-each` transforms the incoming event to N events, where N is number of elements in the Elements list.
&nbsp;

This node has two parameters:
- Elements - list of values over which to loop. It can contain both fixed values and expressions evaluated during execution.
- Output Variable Name - the name of the variable to which current element value will be assigned.

For example, when:
- Elements is `{"John", "Betty"}`
- Output Variable Name is `outputVar`,
&nbsp;

then two events will be emitted, with `#outputVar` equal to `John` for the first event and `Betty` for the second.

&nbsp;
The `#input` variable is available downstream the `for-each` node.  

&nbsp;
## Union

![union_window](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_union0.png)

Union merges multiple branches into one branch. 

In the Streaming processing mode events from the incoming branches are passed to the output branch without an attempt to combine or match them.
&nbsp;

In the Request - Response processing mode only one [response sink](./RRDataSourcesAndSinks.md#sink) can return value. If you have parallel branches of processing the Union node is used to merge them and then [Collect](./RRDataSourcesAndSinks.md#collect) node is used to collect results of processing in each of the merged branches. Check [Introduction to Scenario Authoring](./Intro.md#nussknacker-scenario-diagram) for details on how to interpret the scenario graph in different processing modes.

The `#input` variable will be no longer available downstream the union node; a new variable will be available instead, which is defined in the union node.


Branch names visible in the node configuration form are derived from node names preceding the union node.

Example:
![union_example](../autoScreenshotChangeDocs/Auto_Screenshot_Change_Docs_-_basic_components_-_union1.png)

Entry fields:
- Output Variable Name - the name of the variable containing results of the merge (replacing previously defined variables, in particular `#input`).
- Output Expression - there is one expression for each of the input branches. When there is an incoming event from a particular input branch, the expression defined for that branch is evaluated and passed to the output branch. The expressions defined for respective branches need to be of identical data type. In the example above it is always a record containing fields `branchName` and `value`.

Note, that the `#input` variable used in the Output expression field refers to the content of the respective incoming branch.

