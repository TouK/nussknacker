---
sidebar_position: 10
---

# SpEL Cheat Sheet
## Expressions and types

Expressions used in Nussknacker are primarily written using SpEL (Spring Expression language) - simple, yet powerful expression language. 
SpEL is based on Java ([reference documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions)), but no prior Java knowledge is needed to use it. 

The easiest way to learn SpEL is looking at examples which are further down this page. Some attention should be paid to data types, described in more detail in the next section, as depending on the context in which data are processed or displayed, different data type schemes are in use.

Check out [SpEL overview](./Intro.md#spel) for the overview of how SpEL is used by Nussknacker.

## Data types and structures

The data types are used primarily for:                            
 - validation - e.g. to detect attempt to use incorrect data type, for example numeric field instead of a string, or checking if field used in expression exists at all.
 - code completion - suggestions appearing in UI when editing expressions.
                         
Types of events in the Kafka streams or data returned by enrichers can be often discovered from some sort of schema registry, for example Confluent Schema Registry, SQL table schema or description of REST API. Nussknacker can also infer types of variables defined by user.

The data types used in the execution engine, SpEL expressions and data structures are Java based. 
These are also the data type names that appear in code completion hints. 
In most cases Nussknacker can automatically convert between Java data types and JSON and Avro formats. JSON will be used for REST API enrichers, while AVRO should be first choice for format of Kafka messages. The rules Nussknacker uses to convert between different data type systems can be found [here](../integration/DataTypingAndSchemasHandling.md) - in most cases this information will not be needed during scenario authoring. 

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

More information about how to declare each type in Avro is in [Avro documentation](http://avro.apache.org/docs/current/spec.html#schemas), 
especially about [Avro logical types](http://avro.apache.org/docs/current/spec.html#Logical+Types).

### Records/objects/maps
         
In Nussknacker, the following data types share common processing characteristics:
- `object` in JSON
- `record` or `map` in Avro
- `Map` and [POJO](https://en.wikipedia.org/wiki/Plain_old_Java_object) in Java
                     
In many cases Nussknacker can convert between them automatically.
For the user, the most significant difference is (using Avro terminology) 
between `record` and `map`. Both can describe following JSON structure:

input = `{ name: 'John', surname: 'Doe'}`                            

The main difference is that in case of `record` Nussknacker "knows" which fields (`name` and `surname`)
are available and suggests and validates fields and their types.
For example, `#input.name` is valid, while `#input.noname` or `#input.name > 0` as field name or type do not match.

On the other hand, `map` describes "generic" structure - Nussknacker tacitly assumes it can contain **any** field, but only of certain type (e.g. we can have a "map of Strings", "map of Integers" etc. If this type is `Unknown` the values might be of any type).
                                                             
Nussknacker usually infers structure of record from external source (e.g. Avro schema), but it can also detect it from map literals.

### Arrays/lists

In Nussknacker (e.g. in code completion) JSON / Avro arrays are referred to as `Lists`; 
also in some context `Collection` can be met (it's Java API for handling lists, sets etc.).

### Date/Time

See [Handling data/time](#handling-datetime) for detailed description of how to deal with date and time in Nussknacker.


## SpEL syntax

### Basics
                                  
Most of the literals are similar to JSON ones, in fact in many cases JSON structure is valid SpEL. 
There are a few notable exceptions:
- Lists are written using curly braces: `{"firstElement", "secondElement"}`, as `[]` is used to access elements in array 
- Strings can be quoted with either `'` or `"`
- Field names in records do not to be quoted (e.g. `{name: "John"}` is valid SpEL, but not valid JSON)

| Expression             | Result                         | Type                 |
| ------------           | --------                       | --------             |
| `'Hello World'`        | "Hello World"                  | String               |
| `true`                 | true                           | Boolean              |
| `null`                 | null                           | Null                 |
| `{}`                   | an empty list                  | List[Unknown]        |
| `{1,2,3,4}`            | a list of integers from 1 to 4 | List[Integer]        |
| `{:}`                  | an empty record                | Record{}             |
| `{john:300, alex:400}` | a record (name-value collection)| Record{alex: Integer(400), john: Integer(300)} |
| `#input`               | variable                       |                      |
| `'AA' + 'BB'`          | "AABB"                         | String               |
                                    
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
| <code>true &#124;&#124; false</code>         | true      | Boolean  |
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
| `{foo: 1L, bar: 2L, tar: 3L}.?[#this.key == "foo" OR #this.value > 2L]` |  {'tar': 3, 'foo': 1}                 | Map[String, Long] |


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
| `{1,2,3,4}.?[#this ge 3]`                 | {3, 4}       | List[Integer] |
| `#usersList.?[#this.firstName == 'john']` | {'john doe'} | List[String]  |
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

| Expression                     | Result           | Type          |
| ------------                   | --------         | --------      |
| `{1,2,3,4}.![#this * 2]`       | {2, 4, 6, 8}     | List[Integer] |
| `#listOfPersons.![#this.name]` | {'Alex', 'John'} | List[String]  |
| `#listOfPersons.![#this.age]`  | {42, 24}         | List[Integer] |
| `#listOfPersons.![7]`          | {7, 7}           | List[Integer] |


For other operations on lists, please see the `#COLLECTION` [helper](#built-in-helpers).

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
If you need to invoke the same method in many places, probably the best solution is to create additional helper.

| Expression             | Result    | Type     |
| ------------           | --------  | -------- |
| `T(java.lang.Math).PI` | 3.14159.. | Double   |

### Chaining with dot
| Expression                                                   | Result    | Type     |
| ------------                                                 | --------  | -------- |
| `{1, 2, 3, 4}.?[#this > 1].![#this > 2 ? #this * 2 : #this]` | {2, 6, 8} | Double   |

### Type conversions

It is possible to convert from a type to another type and this can be done by implicit and explicit conversion.

#### Implicit conversion

SpEL has many built-in implicit conversions that are available also in Nussknacker. Mostly conversions between various
numeric types and between `String` and some useful logical value types. Implicit conversion means that when finding
the "input value" of type "input type" (see the table below) in the context where "target type" is expected, Nussknacker
will try to convert the type of the "input value" to the "target type". Some examples:

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
| `'' + 42`                           | `42`              | Integer    | String      |

More usage examples can be found in [this section](#conversions-between-datetime-types).

#### Explicit conversions

Explicit conversions are available in utility classes and build-in java conversion mechanisms:

| Expression                                                      | Result                     | Type            |
|-----------------------------------------------------------------|----------------------------|-----------------|
| `#NUMERIC.toNumber('42')`                                       | 42                         | Number          |
| `#NUMERIC.toNumber('42').toString()`                            | '42'                       | String          |
| `#DATE_FORMAT.parseOffsetDateTime('2018-10-23T12:12:13+00:00')` | 1540296720000              | OffsetDateTime  |
| `#DATE_FORMAT.parseLocalDateTime('2018-10-23T12:12:13')`        | 2018-10-23T12:12:13+00:00  | LocalDateTime   |

## Built-in helpers

Nussknacker comes with the following helpers:

| Helper        | Functions                                      |
|---------------|------------------------------------------------|
| `COLLECTION`  | Operations on collections                      |
| `CONV`        | General conversion functions                   |
| `DATE`        | Date operations (conversions, useful helpers)  |
| `DATE_FORMAT` | Date formatting/parsing operations             |
| `GEO`         | Simple distance measurements                   |
| `NUMERIC`     | Number parsing                                 |
| `RANDOM`      | Random value generators                        |
| `UTIL`        | Various utilities (e.g. identifier generation) |


## Handling date/time.

### Date/time data types

Formats of date/time are pretty complex - especially in Java. There are basically three ways of storing date:
- as timestamp - absolute value, number of milliseconds since 1970-01-01T00:00:00 UTC. In Nussknacker this is 
  usually seen as `Instant` or `Long`. This format is handy for storing/sending values, a bit problematic when
  it comes to computations like adding a month or extracting date. 
- as date/time without timezone information (this is usually handy if your system is in one timezone).
  Converting to timestamp is done using Nussknacker server timezone.
  In Nussknacker they are usually represented as `LocalDate` and `LocalDateTime`. Suitable for date computations like adding a month or extracting date. 
- as date/time with stored timezone. In Nussknacker usually seen as `ZonedDateTime`. Suitable for date computations like adding a month or extracting date. You need to know TimeZone ID, if you want you use date/time with stored timezone. A full list of TimeZone IDs can be found [here](https://www.tutorialspoint.com/get-all-the-ids-of-the-time-zone-in-java).
- as date/time with stored time offset. In Nussknacker usually seen as `OffsetDateTime`. Contrary to `ZonedDateTime` doesn't handle daylight saving time. 
  Quite often used to hold timestamp with additional information showing what was the local date/time from "user perspective"

### Conversions between date/time types

Conversions of different types of dates are handled either by
- `#DATE` helper methods e.g.:
  - `#DATE.toEpochMilli(#zonedDate)`
  - `#DATE.toInstant(#long)` - converts long value to `Instant`
- methods on some types of objects, e.g.
  - `#instantObj.toEpochMilli` - returns timestamp for `#instantObj` of type `Instant`
  - `#localDate.atStartOfDay()` - returns `LocalDateTime` at midnight for `#localDate` of type `LocalDate`
  - `#localDateTime.toLocalDate` - truncates to date for `#localDateTime` of type `LocalDateTime`
  - `#zonedDate.toInstant` - converts `ZonedDateTime` to `Instant`
  - `#instant.atZone('Europe/Paris')` - converts `ZonedDateTime` to `Instant`
  - `#instant.atOffset('+01:00')` - converts `OffsetDateTime` to `Instant`
- automatically by implicit conversion mechanism e.g.
  - `#instant.atZone('Europe/Paris')` - `Europe/Paris` String was automatically converted to `ZonedId`
  - `#instant.atOffset('+01:00')` - `+01:00` String was automatically converted to `ZonedId`
  - `#time.isAfter('09:00')` - `09:00` String was automatically converted to `LocalTime`
  - `#date.isBefore('2020-07-01')` - `2020-07-01` String was automatically converted to `LocalDate`
  - `#dateTime.isAfter('2020-05-01T11:00:00')` - `2020-05-01T11:00:00` String was automatically converted to `LocalDateTime`

### Date/time utility methods

`DATE` helper contains also some other useful helper methods, mainly for date range checks and computations of periods and durations e.g.:
- `#DATE.isBetween(#localTime, '09:00', '17:00')` - checks if `LocalTime` is in (inclusive) range `<09:00, 17:00>`
- `#DATE.isBetween(#dayOfWeek, #DATE.MONDAY, #DATE.FRIDAY)` - checks if `DayOfWeek` is in (inclusive) range `<MONDAY, FRIDAY>`
- `#DATE.isBetween(#localDate, '2020-06-01', '2020-07-01')` - checks if `LocalDate` is in (inclusive) range `<2020-06-01, 2020-07-01>`
- `#DATE.isBetween(#localDateTime, '2020-06-01T11:00:00', '2020-07-01T11:00:00')` - checks if `LocalDateTime` is in (inclusive) range `<2020-06-01T11:00:00, 2020-07-01T11:00:00>`
- `#DATE.periodBetween(#from, #to).getMonths` - computes `Period` between `from` and `to` and return number of full months between those two dates
- `#DATE.durationBetween(#from, #to).toDays` - computes `Duration` between `from` and `to` and return number of full days between those two dates.
  Keep in mind that `Duration` is not daylight saving time aware - it computes seconds difference and divide it by number of seconds in given period.
- In case of days it will be `86400` seconds.

Some useful constants are also available:
- `#DATE.MONDAY`, `#DATE.TUESDAY`, ...  - day of weeks
- `#DATE.JANUARY`, `#DATE.FEBRUARY`, ... - months
- `#DATE.zuluTimeZone` - Zulu timezone which always has time zone offset equals to UTC
- `#DATE.UTCOffset` - UTC offset
- `#DATE.defaultTimeZone` - Default time zone for Nussknacker application

### Parsing of date/time

Also, `#DATE_FORMAT` helper methods can be used to parse or format certain data type from/to the String. It is not recommended to use parsing
in scenarios because it will obfuscate logic. Better way is to configure properly message schema. But sometimes it is the only way to handle it. Available helpers:
- `#DATE_FORMAT.parseOffsetDateTime('2020-01-01T11:12:13+01:00')` - parse `OffsetDateTime` in ISO-8601 format
- `#DATE_FORMAT.parseOffsetDateTime('2020-01-01T11:12:13+01:00', 'yyyy-MM-dd'T'HH:mm:ssXXX')` - parse `OffsetDateTime` in given `DateTimeFormatter` format
- `#DATE_FORMAT.parseOffsetDateTime('2020-01-01T11:12:13+01:00', #dateTimeFormatter)` - parse `OffsetDateTime` using given `DateTimeFormatter`

Equivalent variants of `parse` methods are available also for other date/time types: `LocalTime`, `LocalDate`, `LocalDateTime`, `Instant` and `ZonedDateTime`.

### Formatting of date/time

To format date/time can be used `#DATE_FORMAT.format(#dateTime)` method which accept various date/time types and formats it in ISO-8601 format.
Also `DateTimeFormatter` can be used directly via e.g. `#DATE_FORMAT.formatter('EEEE').format(#date)`. Other formatter factory methods:
- `#DATE_FORMAT.formatter('EEEE', 'PL')` - creates `DateTimeFormatter` using given pattern (`EEEE`) and locale (`PL`)
- `#DATE_FORMAT.lenientFormatter('yyyy-MM-dd')` - creates lenient version of `DateTimeFormatter` using given pattern. Lenient parser may use heuristics 
to interpret inputs that do not precisely match format e.g. format `E` will accept: `mon`, `Mon` and `MONDAY` inputs. 
On the other hand, formatter created using `#DATE_FORMAT.formatter()` method will accept only `Mon` input.
- `#DATE_FORMAT.lenientFormatter('yyyy-MM-dd EEEE', 'PL')` - creates lenient version `DateTimeFormatter` using given pattern and locale

For full list of available format options take a look at [DateTimeFormatter api docs](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html).

## Casting.

When a type cannot be determined by parser, the type is presented as `Unknown`. When we know what the type will be on
runtime, we can cast given type, and then we can operate on a cast type.

E.g. having a variable `obj` of a type: `List[Unknown]` and we know the elements are strings then we can cast elements
to String: `#obj.![#this.canCastTo('java.lang.String') ? #this.castTo('java.lang.String') : null]`.
