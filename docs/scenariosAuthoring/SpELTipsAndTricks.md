---
# front matter metadata for Docusaurus
title: SpEL Tips and Tricks
description: Transform JSON data like a pro
sidebar_label: SpEL Tips and Tricks
#sidebar_position: 

# front matter metadata for LLM
keywords: ["SpEL", "expressions"]
---

# SpEL tips and tricks

## Common errors and misunderstandings

- Unlike JSON, SpEL uses curly brackets to denote the start and end of a list, e.g.:  `{1, 2, 3, 4}`. To make things more confusing, in the code hints and the debugger Nussknacker shows lists using JSON notation for lists. So, stay alert.
- `{:}` stands for the empty record (map). `{}` denotes an empty list.

## Handling data types problems

Sometimes the Nussknacker’s data typing subsystem, on which hints and validations are built, is too restrictive and complains about a data type mismatch. In such a case, you can use the `#CONV.toAny()` function to trick Nussknacker’s data typing system into thinking that the data type of the entered expression is valid. You can see an example of how to use `#CONV.toAny` in the 'Handling enums' [section](/docs/scenariosAuthoring/SpELTipsAndTricks.md#handling-enums).
Note that the `#CONV.toAny()` is quite brutal in how it works - it effectively disables all the type checking during the scenario authoring. Unless you monitor logs and scenario metrics, you may not notice runtime validation errors concealed during scenario authoring by using `#CONV.toAny()`.

## Handling enums

Both AVRO and JSON schema specification allow you to define an enumeration value, defined as a constant list of values (strings) this enum can hold. For example, let’s consider this AVRO DeliveryStatus type:

```json
{
  "type": "enum",
  "name": "status",
  "symbols" : ["ORDERED", "READY", "DELIVERED"]
}
```

Suppose we want to define some kind of logic dependent on delivery status. If we use field of this type in a filter/choice node, we will discover that it is of type EnumSymbol (a generic Java type representing the enum) and cannot be compared with plain String - even if it contains one of the allowed values.

What is the EnumSymbol? It is a generic Java type representing the enum. Currently, the workaround is to convert it to String before the comparison is done, as shown in the screenshot below.

Now, imagine that we got one of the above enum values in a variable of type string. If you try to make the comparison, the Nussknacker typing system will complain:

The workaround here is to use the #CONV.toAny() function to trick the typing subsystem into thinking that it is a valid enum symbol:

## Safe list navigation

Often, we are interested in the first element of the list. If the list is empty and we try to access the first element, we will get a runtime error. The obvious way out is to use the Elvis operator:

`myList.size == 0 ? null : myList[0]`

There is a shorter way to achieve exactly the same:

`myList.^[true]`

How does it work? The `.^` returns a first element of a filtered list. In our case the filtering predicate is simply `true`, so the filter will return the original list. The `.^` will safely return the first element of the list - if it exists, or `null` if the list is empty.
If you want the last element of the list - and you are not certain whether list is empty or not - just use:

`myList.$[true]`

## Non-trivial operations on JSON records and lists

The `!` operator is truly powerful; one can achieve a lot of magic with it. A few examples follow.
&nbsp;

### Convert record to a list

Suppose one wants to convert a JSON record to a list containing records with fieldName and fieldValue fields.

The example input record (referred to as `#myInputRecord` later) look like this:

`{"fieldA": "124.60", "fieldB": "123"}`

and the expected result list is below.

```json
[
  {
    "fieldName": "fieldA",
    "numValue": 124.6
  },
  {
    "fieldName": "fieldB",
    "numValue": 123
  }
]
```

The expression to use is as follows:

`#myRecord.![{"fieldName":  #this.key, "numValue": #this.value.toDouble}]`
&nbsp;

### Convert list to a map (record)

 The toMap() method converts a list which contains `{key: "key-name", value: some-value}` elements to a [map](/docs/scenariosAuthoring/SpEL.md#recordsobjectsmaps). 

Example - the following expression:
&nbsp;
`{{key: "k1", value: 11}, {key: "k2", value: true}, {key: "k3", value: "myString"}}.toMap()`

will be evaluated to:
```json 
{
  "k1": 11,
  "k2": true,
  "k3": "myString"
},
```

Use can use [dynamic navigation](/docs/scenariosAuthoring/SpEL.md#dynamic-navigation) to access fields of a map or a record. 
&nbsp;

### Not trivial list transformations

In this example, a `{1,2,3,4,5}` list is transformed to a list of records in two steps.

Expression:

`{1,2,3,4,5}.![{#this, #this mod 2}].![{"value" : #this[0], "odd?" : #this[1] == 1 ? true : false}]`

Result (in JSON notation) of the first step:

```json
[
  {1,1},
  {2,0},
  {3,1},
  {4,0},
  {5,1}
]
```

Final result (JSON notation):

```json
[
  {"value": 1, "odd?": true },
  {"value": 2, "odd?": false },
  {"value": 3, "odd?": true },
  {"value": 4, "odd?": false },
  {"value": 5, "odd?": true }
]
```

## Arithmetic with zero and numeric types

SpEL arithmetic depends on the [numeric types](/docs/scenariosAuthoring/SpEL.md#data-types-and-structures) used in an expression. As a result, the same operation may either fail or produce a value, depending on the data.

### Division by zero

| Example &nbsp; | Result        | Reasoning   |
|----------------|---------------|-------------|
| `1 / 0`        | Runtime error | Both operands are treated as integers, so SpEL performs integer division. Integer division has no concept of Infinity/NaN, so dividing by zero fails. |
| `1.0 / 0`      | `Infinity`    | The presence of 1.0 makes the operation floating-point. With floating-point arithmetic, dividing a non-zero number by zero means “the result grows without bound”, so the engine returns Infinity (or -Infinity for a negative numerator). This is a <strong>valid</strong> result. |
| `0.0 / 0`      | `NaN`         | This is the “undefined” case. The question becomes <em>“what number multiplied by 0 gives 0?”</em> — many answers fit, so there is no single meaningful result. Floating-point arithmetic signals this with NaN. |

The `Infinity` and `NaN` special values propagate further and do not stop scenario execution. The bottom line: when division is part of decision logic, handle the zero case in the scenario flow.
A common approach is to add a [Filter](/docs/components/filter.mdx) node before the calculation and decide what should happen when the divisor is zero.

### General rules 

1. Decide which arithmetic mode you are in:
   - Integer mode if both operands are integer types (e.g. `Integer`, `Long`).
   - Floating-point mode if any operand is floating-point (e.g. `Double`, `Float`) — including a divisor like `0.0`.
Some numeric types (notably `BigDecimal`) may also throw an error on division by zero.

2. If the divisor is not zero
   - Integer mode: result is truncated toward zero.
   - Floating-point mode: normal fractional result.

3. If the divisor is zero - see previous section.


## Handling date and time

The date and time related data types are explained in [SpEL Cheat Sheet](/docs/scenariosAuthoring/SpEL.md).
The date and time related functions and methods are documented [here](/docs/scenariosAuthoring/functionsAndMethods.md#date-and-date_format---datetime-utility-functions).

### How to check whether an event happened **today** (server timezone)?

```spel
#eventInstant
  .atZone(#DATE.defaultTimeZone)
  .toLocalDate ==
#DATE.nowAtDefaultTimeZone.toLocalDate
```

### How to check whether an event happened today in a specific timezone?

```
#eventInstant
  .atZone(#DATE.zone('Europe/Warsaw'))
  .toLocalDate ==
#DATE.nowAtZone(#DATE.zone('Europe/Warsaw')).toLocalDate
```

### How to compare a date/time value with a literal?

`#localDateTime.isAfter('2024-03-01T09:00:00')`
`#localDate.isBefore('2024-03-01')`
`#localTime.isAfter('09:00')`


### How to attach a timezone to an `Instant`?
`#instantVar.atZone(#DATE.zone('Europe/Warsaw'))`