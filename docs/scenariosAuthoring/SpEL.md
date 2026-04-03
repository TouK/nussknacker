---
# front matter metadata for Docusaurus
title: SpEL Cheat Sheet
description: Learn SpEL by examples 
sidebar_label: SpEL Cheat Sheet
# sidebar_position: 

# front matter metadata for LLM

processingModes: [Streaming, Request-Response]
keywords: ["SpEL", "expressions"]
---

# SpEL Cheat Sheet

:::note
Expressions in Nussknacker include [SpEL expressions, string templates, and JSON templates](/docs/scenariosAuthoring/introduction.mdx#ways-of-providing-parameter-values). This document focuses on SpEL expressions; for brevity, the term “expression” is used throughout to refer to SpEL expressions.
:::

## Expressions and types

Expressions used in Nussknacker are primarily written using SpEL (Spring Expression Language) - simple, yet powerful expression language.
SpEL is based on Java ([reference documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions)), but no prior Java knowledge is needed to use it.

The easiest way to learn SpEL is by looking at examples which are further down this page. Some attention should be paid to data types, described in more detail in the next section, as depending on the context in which data is processed or displayed, different data type schemes are in use.

Check out [SpEL overview](/docs/scenariosAuthoring/introduction.mdx#spel) for the overview of how SpEL is used by Nussknacker.

## Data types and structures

The data types are used primarily for:

 - validation - e.g. to detect attempt to use incorrect data type, for example numeric field instead of a string, or checking if field used in expression exists at all.
 - code completion - suggestions appearing in UI when editing expressions.

Types of events in the Kafka streams or data returned by enrichers can often be discovered from some sort of schema registry, for example Confluent Schema Registry, SQL table schema or description of REST API. Nussknacker can also infer types of variables defined by the user.

The data types used internally by Nussknacker, SpEL expressions and data structures are Java-based.
These are also the data type names that appear in code completion hints.
In most cases Nussknacker can automatically convert between Java data types and JSON and Avro formats. JSON will be used for REST API enrichers, while AVRO should be the first choice for format of Kafka messages. The rules Nussknacker uses to convert between different data type systems can be found [here](/docs/integrations/dataTypingAndSchemasHandling.mdx) - in most cases this information will not be needed during scenario authoring.

Below is the list of the most common data types. In Java types column package names are omitted for brevity,
they are usually `java.lang` (primitives), `java.util` (List, Map) and `java.time`

### Basic (primitive data types)

| Java type  | Comment                                    |
|------------|--------------------------------------------|
| null       |                                            |
| String     | UTF-8                                      |
| Boolean    |                                            |
| Integer    | 32bit                                      |
| Long       | 64bit                                      |
| Float      | single precision                           |
| Double     | double precision                           |
| BigDecimal | enable computation without rounding errors |
| UUID       | uuid                                       |

More information about how to declare each type in Avro is in [Avro documentation](https://avro.apache.org/docs/++version++/specification/#schema-declaration), especially about [Avro logical types](https://avro.apache.org/docs/++version++/specification/#logical-types).

### Records/objects/maps

In Nussknacker, the following data types share common processing characteristics:

- `object` in JSON
- `record` or `map` in Avro
- `Map` and [POJO](https://en.wikipedia.org/wiki/Plain_old_Java_object) in Java

The above structures are modelled in Nussknacker as records or maps. In many cases Nussknacker can automatically convert between on one side JSON/Avro/Java structures mentioned above and Nussknacker's record or map on  the other.
Both `record` and `map` can describe the following JSON structure:

```json 
{ "name": "John", "surname": "Doe"}
```

The main difference is that in case of `record` Nussknacker "knows" which fields (`name` and `surname`)
are available and suggests and validates fields and their types.
For example if the above structure is in the `input` variable, `#input.name` is valid, while `#input.noname` or `#input.name > 0` as field name or type do not match.

On the other hand, `map` describes "generic" structure - Nussknacker tacitly assumes it can contain **any** field, but only of certain type (e.g. we can have a "map of Strings", "map of Integers" etc. If this type is `Unknown` the values might be of any type).

Nussknacker usually infers structure of record from external source (e.g. Avro schema), but it can also detect it from map literals.

Finally, it is worth noting that the above JSON structure can be also defined in SpEL like below:
` { name: "John", surname: 'Doe' }`
which is invalid in "regular" JSON. 

### Arrays/lists

In Nussknacker (e.g. in code completion) JSON / Avro arrays are referred to as `Lists`;
also in some contexts, `Collection` can be encountered (it's Java API for handling lists, sets etc.).

### Date/Time

Date/time handling is inherently complex:
- time zones and daylight saving rules affect how time is interpreted,
- different use cases (e.g. display vs computation) require different representations.

There are basically three ways of representing date/time:

- **As an absolute timestamp** — a point on the UTC time-line.
In Nussknacker this is represented either as:
- `Long` — number of milliseconds since 1970-01-01T00:00:00 UTC (epoch millis)
- `Instant` — the same point in time, represented as a date/time data type

Both represent an **absolute moment on the UTC timeline**:
- `Long` is a numeric form (good for storage and comparison)
- `Instant` is a datetime data type form - good for further date/time operations

They can be freely converted between each other.
- **As date/time without timezone information** — handy if your system treats values as “local time”.
  In Nussknacker they are usually represented as `LocalDate`, `LocalTime`, `LocalDateTime`.  Suitable for calendar computations like adding a month or extracting date/time fields. *Use LocalDateTime only when timezone does not matter yet; the moment it does — convert to ZonedDateTime or Instant*.
- **As date/time with timezone/offset**:
  - `ZonedDateTime` stores a **ZoneId** (handles daylight saving time rules).
  - `OffsetDateTime` stores only a **ZoneOffset** (does **not** handle DST rules; it’s just “+01:00”, “+02:00”…).

:::tip
 Rule of thumb:
 - calendar logic → `LocalDate` / `LocalDateTime` / `ZonedDateTime`
 - storage/order/interop → `Instant` / `Long`
:::

Sometimes you may see in hints a `TemporalAncestor` data type. This is a name for a group of date / time data types; any of the following can be used if `TemporalAncestor` is required: `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`.

### Unknown

In certain situations, like [dynamic navigation](/docs/scenariosAuthoring/SpEL.md#dynamic-navigation) when Nussknacker is not able to determine data type, the evaluated expression is of `Unknown` data type. You can further dynamically navigate it or [convert](/docs/scenariosAuthoring/SpEL.md#type-conversions) it to the desired data type.


## SpEL syntax

### Basics

Most of the literals are similar to JSON ones, in fact in many cases JSON structure is valid SpEL.
There are a few notable exceptions:

- Lists are written using curly braces: `{"firstElement", "secondElement"}`, as `[]` is used to access elements in array
- Strings can be quoted with either `'` or `"`
- Field names in records do not need to be quoted (e.g. `{name: "John"}` is valid SpEL, but not valid JSON)

| Expression             | Result                         | Type                 |
| ------------           | --------                       | --------             |
| `'Hello World'`        | "Hello World"                  | String               |
| `true`                 | true                           | Boolean              |
| `null`                 | null                           | Null                 |
| `{}`                   | an empty list                  | List\[Unknown\]        |
| `{1,2,3,4}`            | a list of integers from 1 to 4 | List\[Integer\]        |
| `{:}`                  | an empty record                | Record{}             |
| `{john:300, alex:400}` | a record (name-value collection)| Record{alex: Integer(400), john: Integer(300)} |
| `#input`               | variable                       |                      |
| `'AA' + 'BB'`          | "AABB"                         | String               |
| `7`                    | 7                              | Integer              |
| `7L`                   | 7                              | Long                 |
| `3.14`                 | 3.14                           | Float                |
| `3.14D`                | 3.14                           | Double               |


### Arithmetic Operators

The `+`, `-`, `*` arithmetic operators work as expected.

| Operator | Equivalent symbolic operator | Example expression | Result |
|----------|------------------------------|--------------------|--------|
| `div`    | `/`                          | `7 div 2`          | 3      |
| `div`    | `/`                          | `7.0 div 2`        | 2.3333333333 |
| `mod`    | `%`                          | `23 mod 7`         | 2      |

### Conditional Operators

| Expression                                   | Result    | Type     |
| ------------                                 | --------  | -------- |
| `2 == 2`                                     | true      | Boolean  |
| `2 > 1`                                      | true      | Boolean  |
| `true AND false`                             | false     | Boolean  |
| `true && false`                              | false     | Boolean  |
| `true OR false`                              | true      | Boolean  |
| `true \|\| false`                             | true      | Boolean  |
| `2 > 1 ? 'a' : 'b'`                          | "a"       | String   |
| `2 < 1 ? 'a' : 'b'`                          | "b"       | String   |
| `#nonNullVar == null ? 'Unknown' : 'Success'` | "Success" | String   |
| `#nullVar == null ? 'Unknown' : 'Success'`   | "Unknown" | String   |
| `#nullVar?:'Unknown'`                        | "Unknown" | String   |
| `'john'?:'Unknown'`                          | "john"    | String   |

### Relational operators

| Operator | Equivalent symbolic operator | Example expression | Result |
|----------|------------------------------|--------------------|--------|
| `lt`     | `<`                          | `3 lt 5`           | true   |
| `gt`     | `>`                          | `4 gt 4`           | false  |
| `le`     | `<=`                         | `3 le 5`           | true   |
| `ge`     | `>=`                         | `4 ge 4`           | true   |
| `eq`     | `==`                         | `3 eq 3`           | true   |
| `ne`     | `!=`                         | `4 ne 2`           | true   |
| `not`    | `!`                          | `not true`         | false  |

### Strings operators

| Expression      | Result      | Type     |
| --------------  | ----------- | -------- |
| `'AA' + 'BB'`   | "AABB"      | String   |

### Method invocations

As Nussknacker uses Java types, some objects are more than data containers - there are additional methods
that can be invoked on them. Method parameters are passed in parentheses, usually parameter details
are shown in code completion hints.

| Expression                 | Result     | Type    |
| -------------------------- | ---------- | ----    |
| `'someValue'.substring(4)` | "Value"    | String  |
| `'someValue'.length()`     | 9          | Integer |

### Accessing elements of a list or a record

| Expression                                                              | Result                                | Type              |
|-------------------------------------------------------------------------|---------------------------------------|-------------------|
| `{1,2,3,4}[0]`                                                          | 1                                     | Integer           |
| `{jan:300, alex:400}[alex]`                                             | a value of field 'alex', which is 400 | Integer           |
| `{jan:300, alex:400}['alex']`                                           | 400                                   | Integer           |
| `{jan:{age:24}}, alex:{age: 30}}}['alex']['age']`                       | 30                                    | Integer           |
| `{foo: 1L, bar: 2L, tar: 3L}.?[#this.key == "foo" OR #this.value > 2L]` |  {'tar': 3, 'foo': 1}                 | Map\[String, Long\] |

Attempting to access non-present elements will cause exceptions. For lists, they are thrown in runtime and for records
they occur before deployment of a scenario during expression validation.

| Expression                    | Error                                           |
|-------------------------------|-------------------------------------------------|
| `{1,2,3,4}[4]`                | Runtime error: Index out of bounds              |
| `{jan:300, alex:400}['anna']` | Compilation error: No property 'anna' in record |

### Filtering lists

Special variable `#this` is used to operate on single element of list.
Filtering all the elements uses a syntax of `.?`.
In addition to filtering all the elements, you can retrieve only the first or the last value.
To obtain the first element matching the predicate, the syntax is `.^`.
To obtain the last matching element, the syntax is `.$`.

| Expression                                | Result       | Type          |
| ------------                              |--------------| --------      |
| `{1,2,3,4}.?[#this ge 3]`                 | {3, 4}       | List\[Integer\] |
| `#usersList.?[#this.firstName == 'john']` | {'john doe'} | List\[String\]  |
| `{1,2,3,4}.^[#this ge 3]`                 | {3}          | Integer       |
| `{1,2,3,4}.$[#this ge 3]`                 | {4}          | Integer       |

### Transforming lists

Special variable `#this` is used to operate on single element of list.

Examples below assume following structure:

```Person: {name: String, age: Integer }
listOfPersons: List[Person]
person1 = name: "Alex"; age: 42
person2 = name: "John"; age: 24
listOfPersons = {person1, person2}
```

| Expression                                                      | Result               | Type                 |
|-----------------------------------------------------------------|----------------------|----------------------|
| `{1,2,3,4}.![#this * 2]`                                        | {2, 4, 6, 8}         | List\[Integer\]        |
| `#listOfPersons.![#this.name]`                                  | {'Alex', 'John'}     | List\[String\]         |
| `#listOfPersons.![#this.age]`                                   | {42, 24}             | List\[Integer\]        |
| `#listOfPersons.![7]`                                           | {7, 7}               | List\[Integer\]        |
| `#listOfPersons.![{key: #this.name, value: #this.age}].toMap()` | {Alex: 42, John: 24} | Map\[String, Integer\] |

For other operations on lists, please see the `#COLLECTION` [function group](/docs/scenariosAuthoring/SpEL.md#transforming-data-with-functions-and-methods).

### Safe navigation

When you access nested structure, you have to take care of null fields, otherwise you'll end up with
error. SpEL provides helpful safe navigation operator, it's basically shorthand for conditional operator:
`#someVar?.b` means `#someVar != null ? #someVar.b : null`

| Expression  | `#var` value | Result                         | Type                            |
| ----------- | ------------ | --------                       | --------                        |
| `#var.foo`  | {foo: 5}     | 5                              | Integer                         |
| `#var.foo`  | null         | java.lang.NullPointerException | java.lang.NullPointerException  |
| `#var?.foo` | {foo: 5}     | 5                              | Integer                         |
| `#var?.foo` | null         | null                           | Null                            |

### Invoking static methods

It is possible to invoke Java static methods directly with SpEL. Nussknacker can prevent invocations
of some of them due to security reasons. Invoking static methods is an advanced functionality, which can lead
to incomprehensible expressions, also code completions will not work with many of them.
If you need to invoke the same method in many places, probably the best solution is to create additional function.

| Expression             | Result    | Type     |
| ------------           | --------  | -------- |
| `T(java.lang.Math).PI` | 3.14159.. | Double   |

### Chaining with dot

| Expression                                                   | Result    | Type     |
| ------------                                                 | --------  | -------- |
| `"myString".contains("my")`                                  |   true    | Integer  |
| `"myString".contains("my").length`                           |   4       | Boolean  |
| `{1, 2, 3, 4}.?[#this > 1].![#this > 2 ? #this * 2 : #this]` | {2, 6, 8} | Double   |



### Dynamic navigation

When we deal with structures whose schema is not known to Nussknacker (e.g. data from kafka topics without existing avro/json schema) we will end up
with the `Unknown` type in Designer. For such one type (`Unknown`) we allow dynamic-like access using `[]` operator to access nested fields/elements.

Example `exampleObject` json-like variable

```json
{
    "someField": 123,
    "someNestedObject": {
        "someFieldInNestedObject": "value"
    },
    "someArrayWithObjects": [
        {
            "someFieldInObjectInArray": "value" 
        }
    ]
}
```

can be accessed in e.g. in following ways:

* `#exampleObject['someField']`
* `#exampleObject[#variableWithKeyName]`
* `#exampleObject.get('someField')`
* `#exampleObject['someNestedObject']['someFieldInNestedObject']`
* `#exampleObject['someArrayWithObjects'][0]['someFieldInObjectInArray']`

Every unknown accessed field/element will produce `Unknown` data type, which can be further navigated or [converted](/docs/scenariosAuthoring/SpEL.md#type-conversions) to a desired type.

### Type conversions

It is possible to convert from one type to another type and this can be done by implicit and explicit conversion.

#### Explicit conversions

Explicit conversions are available as built-in functions and function groups (`DATE`, `DATE_FORMAT` and `CONV`).
List of built-in functions:

- `canBe(className)`/`to(className)`/`toOrNull(className)`
- `canBeBoolean`/`toBoolean`/`toBooleanOrNull`
- `canBeInteger`/`toInteger`/`toIntegerOrNull`
- `canBeLong`/`toLong`/`toLongOrNull`
- `canBeDouble`/`toDouble`/`toDoubleOrNull`
- `canBeBigDecimal`/`toBigDecimal`/`toBigDecimalOrNull`
- `canBeList`/`toList`/`toListOrNull`
- `canBeMap`/`toMap`/`toMapOrNull`

The `canBe`, `to` and `toOrNull` functions take the name of target class as a parameter, in contrast to, for
example, `canBeLong` which has the name of target class in the function name and is the shortcut for: `canBe('Long')`.
We have added some functions with types in their names, for example: `canBeLong` to have shortcuts to the most common
types.

Functions with the prefix `canBe` check whether a type can be converted to the appropriate type. Functions with the `to`
prefix convert a value to the desired type, and if the operation fails, an exception is propagated further. Functions
with the `to` prefix and `OrNull` suffix convert a value to the desired type, and if the operation fails, a null value
is returned.

Examples of conversions:

| Expression                                                               | Result                      | Type              |
|--------------------------------------------------------------------------|-----------------------------|-------------------|
| `'123'.canBeDouble`                                                      | true                        | Boolean           |
| `'123'.toDouble`                                                         | 123.0                       | Double            |
| `'abc'.toDoubleOrNull`                                                   | null                        | Double            |
| `'123'.canBe('Double')`                                                  | true                        | Boolean           |
| `'123'.to('Double')`                                                     | 123.0                       | Double            |
| `'abc'.toOrNull('Double')`                                               | null                        | Double            |
| `'abc'.toLong`                                                           | exception thrown in runtime | Long              |
| `{{name: 'John', age: 22}}.![{key: #this.name, value: #this.age}].toMap` | {John: 22}                  | Map\[String, Long\] |
| `'2018-10-23T12:12:13'.to('LocalDateTime')`                              | 2018-10-23T12:12:13+00:00   | LocalDateTime     |

Conversions only make sense between specific types. We limit SpeL's suggestions to show only possible conversions.
Below is a matrix which shows which types can be converted with each other:

| Source type ⬇ \\ Target type ➡ | BigDecimal         | BigInteger         | Boolean       | Byte               | Charset       | ChronoLocalDate    | ChronoLocalDateTime | Currency      | Double             | Float              | Integer            | List          | Locale        | LocalDate     | LocalDateTime | LocalTime     | Long               | Map           | Unknown       | UUID          | Short              | String             | ZoneId        | ZoneOffset    | All existing types |
|------------------------------------------------------|--------------------|--------------------|---------------|--------------------|---------------|--------------------|---------------------|---------------|--------------------|--------------------|--------------------|---------------|---------------|---------------|---------------|---------------|--------------------|---------------|---------------|---------------|--------------------|--------------------|---------------|---------------|--------------------|
| BigDecimal                                           | ✅               | ✅ | ✖           | ⚠️      | ✖           | ✖                | ✖                 | ✖           | ✅ | ⚠️      | ⚠️      | ✖           | ✖           | ✖           | ✖           | ✖           | ✅ | ✖           | ✖           | ✖           | ⚠️      | ✅ | ✖           | ✖           | ✖                |
| BigInteger                                           | ✅ | ✅                | ✖           | ⚠️      | ✖           | ✖                | ✖                 | ✖           | ✅ | ⚠️      | ⚠️      | ✖           | ✖           | ✖           | ✖           | ✖           | ✅ | ✖           | ✖           | ✖           | ⚠️      | ✅ | ✖           | ✖           | ✖                |
| Byte                                                 | ✅ | ✅ | ✖           | ✅                | ✖           | ✖                | ✖                 | ✖           | ✅ | ✅ | ✅ | ✖           | ✖           | ✖           | ✖           | ✖           | ✅ | ✖           | ✖           | ✖           | ✅ | ✅ | ✖           | ✖           | ✖                |
| Double                                               | ✅ | ✅ | ✖           | ⚠️      | ✖           | ✖                | ✖                 | ✖           | ✅                | ⚠️      | ✅ | ✖           | ✖           | ✖           | ✖           | ✖           | ✅ | ✖           | ✖           | ✖           | ⚠️      | ✅ | ✖           | ✖           | ✖                |
| Float                                                | ✅ | ✅ | ✖           | ⚠️      | ✖           | ✖                | ✖                 | ✖           | ✅ | ✅                | ✅ | ✖           | ✖           | ✖           | ✖           | ✖           | ✅ | ✖           | ✖           | ✖           | ⚠️      | ✅ | ✖           | ✖           | ✖                |
| Integer                                              | ✅ | ✅ | ✖           | ⚠️      | ✖           | ✖                | ✖                 | ✖           | ✅ | ⚠️      | ✅                | ✖           | ✖           | ✖           | ✖           | ✖           | ✅ | ✖           | ✖           | ✖           | ⚠️      | ✅ | ✖           | ✖           | ✖                |
| LocalDate                                            | ✖                | ✖                | ✖           | ✖                | ✖           | ✅ | ✖                 | ✖           | ✖                | ✖                | ✖                | ✖           | ✖           | ✅           | ✖           | ✖           | ✖                | ✖           | ✖           | ✖           | ✖                | ✅ | ✖           | ✖           | ✖                |
| LocalDateTime                                        | ✖                | ✖                | ✖           | ✖                | ✖           | ✖                | ✅  | ✖           | ✖                | ✖                | ✖                | ✖           | ✖           | ✖           | ✅           | ✖           | ✖                | ✖           | ✖           | ✖           | ✖                | ✅ | ✖           | ✖           | ✖                |
| Long                                                 | ✅ | ✅ | ✖           | ⚠️      | ✖           | ✖                | ✖                 | ✖           | ✅ | ⚠️      | ⚠️      | ✖           | ✖           | ✖           | ✖           | ✖           | ✅                | ✖           | ✖           | ✖           | ⚠️      | ✅ | ✖           | ✖           | ✖                |
| Unknown                                              | ⚠️      | ⚠️      | ⚠️ | ⚠️      | ⚠️ | ⚠️      | ⚠️       | ⚠️ | ⚠️      | ⚠️      | ⚠️      | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️      | ⚠️ | ⚠️ | ⚠️ | ⚠️      | ⚠️      | ⚠️ | ⚠️ | ⚠️      |
| UUID                                                 | ✖                | ✖                | ✖           | ✖                | ✖           | ✖                | ✖                 | ✖           | ✖                | ✖                | ✖                | ✖           | ✖           | ✖           | ✖           | ✖           | ✖                | ✖           | ✖           | ✖           | ✖                | ✅ | ✖           | ✖           | ✖                |
| Short                                                | ✅ | ✅ | ✖           | ✅ | ✖           | ✖                | ✖                 | ✖           | ✅ | ✅ | ✅ | ✖           | ✖           | ✖           | ✖           | ✖           | ✅ | ✖           | ✖           | ✖           | ✖                | ✅ | ✖           | ✖           | ✖                |
| String                                               | ⚠️      | ⚠️      | ⚠️ | ⚠️      | ⚠️ | ⚠️      | ⚠️       | ⚠️ | ⚠️      | ⚠️      | ⚠️      | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️      | ⚠️ | ⚠️ | ⚠️ | ⚠️      | ✅      | ⚠️ | ⚠️ | ⚠️      |

Where:
✅ - conversion is possible
✖ - conversion is  not possible
⚠️ - conversion is potentially failing

Conversion examples using functions from the `DATE_FORMAT` function group:

| Expression                                                      | Result                     | Type            |
|-----------------------------------------------------------------|----------------------------|-----------------|
| `#DATE_FORMAT.parseOffsetDateTime('2018-10-23T12:12:13+00:00')` | 1540296720000              | OffsetDateTime  |
| `#DATE_FORMAT.parseLocalDateTime('2018-10-23T12:12:13')`        | 2018-10-23T12:12:13+00:00  | LocalDateTime   |

Functions available in the `CONV`, `DATE` and `DATE_FORMAT` are documented [here](/docs/scenariosAuthoring/functionsAndMethods.md); check also [here](/docs/scenariosAuthoring/SpEL.md#handling-datetime) for discussion of date and time data types.

#### Implicit conversion

SpEL has many built-in implicit conversions that are available also in Nussknacker. Mostly conversions between various
numeric types and between `String` and some useful logical value types. Implicit conversion means that when finding
the "input value" of type "input type" (see the table below) in the context where "target type" is expected, Nussknacker
will try to convert the type of the "input value" to the "target type". This behavior can be encountered in particular
when passing certain values to method parameters (these values can be automatically converted to the desired type).
Some conversion examples:

| Input value                              | Input type | Conversion target type |
|------------------------------------------|------------|------------------------|
| `12.34`                                  | Double     | BigDecimal             |
| `12.34f`                                 | Float      | BigDecimal             |
| `42`                                     | Integer    | BigDecimal             |
| `42L`                                    | Long       | BigDecimal             |
| `'Europe/Warsaw'`                        | String     | ZoneId                 |
| `'+01:00'`                               | String     | ZoneOffset             |
| `'09:00'`                                | String     | LocalTime              |
| `'2020-07-01'`                           | String     | LocalDate              |
| `'2020-07-01T'09:00'`                    | String     | LocalDateTime          |
| `'en_GB'`                                | String     | Locale                 |
| `'ISO-8859-1'`                           | String     | Charset                |
| `'USD'`                                  | String     | Currency               |
| `'bf3bb3e0-b359-4e18-95dd-1d89c7dc5135'` | String     | UUID                   |

Usage example:

| Expression                          | Input value       | Input type | Target type |
|-------------------------------------|-------------------|------------|-------------|
| `#DATE.now.atZone('Europe/Warsaw')` | `'Europe/Warsaw'` | String     | ZoneId      |
| `'' + 42`                           | `'42'`            | Integer    | String      |

More usage examples can be found in [this section](/docs/scenariosAuthoring/SpEL.md#type-conversions)

## Transforming data with functions and methods

Expressions in Nussknacker can use a wide range of built-in **functions** and **methods** which can be used to test for specific conditions, perform computations, convert between data types, manipulate lists (collections) and many more.  Check [here](/docs/scenariosAuthoring/functionsAndMethods.md) for more information. 



