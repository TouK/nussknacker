---
sidebar_position: 8
---

# SpEL Cheat Sheet
## Expressions and types

Expressions used in Nussknacker are primarily written using SpEL (Spring Expression language) - simple, yet powerful expression language. 
SpEL is based on Java ([reference documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions)), but no prior Java knowledge is needed to use it. 

The easiest way to learn SpEL is looking at examples which are further down this page. Some attention should be paid to data types, described in more detail in the next section, as depending on the context in which data are processed or displayed, different data type schemes are in use.

Check out [SpEL overview](Intro#spel) for the overview of how SpEL is used by Nussknacker.

## Data types and structures

The data types are used primarily for:                            
 - validation - e.g. to detect attempt to use incorrect data type, for example numeric field instead of a string, or checking if field used in expression exists at all.
 - code completion - suggestions appearing in UI when editing expressions.
                         
Types of events in the Kafka streams or data returned by enrichers can be often discovered from some sort of schema registry, for example Confluent Schema Registry, SQL table schema or description of REST API. Nussknacker can also infer types of variables defined by user.

The data types used in the execution engine, SpEL expressions and data structures are Java based. 
These are also the data type names that appear in code completion hints. 
In most cases Nussknacker can automatically convert between Java data types and JSON and AVRO formats. JSON will be used for REST API enrichers, while AVRO should be first choice for format of Kafka messages.

Below is the list of the most common data types, with their JSON and Avro counterparts. 
In Java types column package names are omitted for brevity, 
they are usually `java.lang` (primitives), `java.util` (List, Map) and `java.time`

### Basic (primitive data types)

| Java type  | JSON    | Avro                     | Comment                                    |
| ---------- | ------- | -------                  | -----------                                |
| null       | null    | null                     |                                            |
| String     | string  | string                   | UTF-8                                      |
| Boolean    | boolean | boolean                  |                                            |
| Integer    | number  | int                      | 32bit                                      |
| Long       | number  | long                     | 64bit                                      |
| Float      | number  | float                    | single precision                           |
| Double     | number  | double                   | double precision                           |
| BigDecimal | number  | bytes or fixed + decimal | enable computation without rounding errors |
| UUID       | string  | string + uuid            | uuid                                       |

More information about how to declare each type in Avro you can find in [Avro ducumentation](http://avro.apache.org/docs/current/spec.html#schemas), 
especially about [Avro logical types](http://avro.apache.org/docs/current/spec.html#Logical+Types).

### Records/objects
         
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

On the other hand, `map` describes "generic" structure - Nussknacker tacitly assumes it can contain any field of any type.
                                                             
Nussknacker usually infers structure of record from external source (e.g. AVRO schema), but it can also detect it from map literals.


### Arrays/lists

In Nussknacker (e.g. in code completion) JSON / Avro arrays are refered to as `Lists`; 
also in some context `Collection` can be met (it's Java API for handling lists, sets etc.).


### Handling date/time.

#### Date/time data types

Formats of date/time are pretty complex - especially in Java. There are basically three ways of storing date:
- as timestamp - absolute value, number of milliseconds since 1970-01-01T00:00:00 UTC. In Nussknacker this is 
  usually seen as `Instant` or `Long`. This format is handy for storing/sending values, a bit problematic when
  it comes to computations like adding a month or extracting date. 
- as date/time without timezone information (this is usually handy if your system is in one timezone).
  Converting to timestamp is done using Nussknacker server timezone.
  In Nussknacker they are usually represented as `LocalDate` and `LocalDateTime`. Suitable for date computations like adding a month or extracting date. 
- as date/time with stored timezone. In Nussknacker usually seen as `ZonedDateTime`. Suitable for date computations like adding a month or extracting date. 
- as date/time with stored time offset. In Nussknacker usually seen as `OffsetDateTime`. Contrary to `ZonedDateTime` doesn't handle daylight saving time. 
  Quite often used to hold timestamp with additional information showing what was the local date/time from "user perspective"

The following table mapping of types, possible JSON representation (no standard here though) and 
mapping of AVRO types (`int + date` means `int` type with `date` logical type):

| Java type      | JSON    | Avro                                                                  | Sample                                   | Comment                                                      |
| -------------- | ------- | ---------------------------                                           | --------------------------               | ----------------------                                       |
| LocalDate      | string  | int + date                                                            | 2021-05-17                               | Timezone is not stored                                       |
| LocalTime      | string  | int + time-millis or long + time-micros                               | 07:34:00.12345                           | Timezone is not stored                                       |
| LocalDateTime  | string  | not supported yet                                                     | 2021-05-17T07:34:00                      | Timezone is not stored                                       |
| ZonedDateTime  | string  | long + timestamp-millis or timestamp-micros (supported only in sinks) | 2021-05-17T07:34:00+01:00                |                                                              |
| OffsetDateTime | string  | long + timestamp-millis or timestamp-micros (supported only in sinks) | 2021-05-17T07:34:00+01:00\[Europe/Paris] |                                                              |
| Instant        | number  | long + timestamp-millis or timestamp-micros                           | 2021-05-17T05:34:00Z                     | Timestamp (millis since 1970-01-01) in human readable format |
| Long           | number  | long, long + local-timestamp-millis or local-timestamp-micros         | 123456789                                | Raw timestamp (millis since 1970-01-01)                      |

#### Conversions between date/time types

Conversions of different types of dates are handled either by
- `#DATE` helper methods e.g.:
  - `#DATE.toEpochMilli(#zondeDate)`
- methods on some types of objects, e.g.
  - `#instantObj.toEpochMilli` returns timestamp for `#instantObj` of type `Instant`
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

#### Date/time utility methods

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

#### Parsing of date/time

Also, `#DATE_FORMAT` helper methods can be used to parse or format certain data type from/to the String. It is not recommended to use parsing
in scenarios because it will obfuscate logic. Better way is to configure properly message schema. But sometimes it is the only way to handle it. Available helpers:
- `#DATE_FORMAT.parseOffsetDateTime('2020-01-01T11:12:13+01:00')` - parse `OffsetDateTime` in ISO-8601 format
- `#DATE_FORMAT.parseOffsetDateTime('2020-01-01T11:12:13+01:00', 'yyyy-MM-dd'T'HH:mm:ssXXX')` - parse `OffsetDateTime` in given `DateTimeFormatter` format
- `#DATE_FORMAT.parseOffsetDateTime('2020-01-01T11:12:13+01:00', #dateTimeFormatter)` - parse `OffsetDateTime` using given `DateTimeFormatter`

Equivalent variants of `parse` methods are available also for other date/time types: `LocalTime`, `LocalDate`, `LocalDateTime`, `Instant` and `ZonedDateTime`.

#### Formatting of date/time

To format date/time can be used `#DATE_FORMAT.format(#dateTime)` method which accept various date/time types and formats it in ISO-8601 format.
Also `DateTimeFormatter` can be used directly via e.g. `#DATE_FORMAT.formatter('EEEE').format(#date)`. Other formatter factory methods:
- `#DATE_FORMAT.formatter('EEEE', 'PL')` - creates `DateTimeFormatter` using given pattern (`EEEE`) and locale (`PL`)
- `#DATE_FORMAT.lenientFormatter('yyyy-MM-dd')` - creates lenient version of `DateTimeFormatter` using given pattern. Lenient parser may use heuristics 
to interpret inputs that do not precisely match format e.g. format `E` will accept: `mon`, `Mon` and `MONDAY` inputs. 
On the other hand, formatter created using `#DATE_FORMAT.formatter()` method will accept only `Mon` input.
- `#DATE_FORMAT.lenientFormatter('yyyy-MM-dd EEEE', 'PL')` - creates lenient version `DateTimeFormatter` using given pattern and locale

For full list of available format options take a look at [DateTimeFormatter api docs](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html).

# SpEL syntax

## Literals
                                  
Most of the literals are similar to JSON ones, in fact in many cases JSON structure is valid SpEL. 
There are a few notable exceptions:
- Lists are written using curly braces: `{"firstElement", "secondElement"}`, as `[]` is used to access elements in array 
- Empty record is `{:}`, to distinguish it from empty list: `{}`
- Strings can be quoted with either `'` or `"`
- Field names in records do not to be quoted (e.g. `{name: "John"}` is valid SpEL, but not valid JSON)

| Expression             | Result                         | Type                 |
| ------------           | --------                       | --------             |
| `'Hello World'`        | "Hello World"                  | String               |
| `true`                 | true                           | Boolean              |
| `null`                 | null                           | Null                 |
| `{1,2,3,4}`            | a list of integers from 1 to 4 | List[Integer]        |
| `{john:300, alex:400}` | a map (name-value collection)  | Map[String, Integer] |
| `#input`               | variable                       |                      |
                                    
## Arithmetic Operators

| Expression    | Result   | Type     |
| ------------  | -------- | -------- |
| `42 + 2`      | 44       | Integer  |
| `'AA' + 'BB'` | "AABB"   | String   |

## Conditional Operators

| Expression                                   | Result    | Type     |
| ------------                                 | --------  | -------- |
| `2 == 2`                                     | true      | boolean  |
| `2 > 1`                                      | true      | boolean  |
| `true AND false`                             | false     | boolean  |
| `true && false`                              | false     | boolean  |
| `true OR false`                              | true      | boolean  |
| <code>true &#124;&#124; false</code>         | true      | boolean  |
| `2 > 1 ? 'a' : 'b'`                          | "a"       | String   |
| `2 < 1 ? 'a' : 'b'`                          | "b"       | String   |
| `#nonNullVar == null ? 'Unkown' : 'Success'` | "Success" | String   |
| `#nullVar == null ? 'Unknown' : 'Success'`   | "Unknown" | String   |
| `#nullVar?:'Unknown'`                        | "Unknown" | String   |
| `'john'?:'Unknown'`                          | "john"    | String   |

## Method invocations

As Nussknacker uses Java types, some objects are more than data containers - there are additional methods 
that can be invoked on them. Method parameters are passed in parentheses, usually parameter details 
are shown in code completion hints.

| Expression                 | Result     | Type    |
| -------------------------- | ---------- | ----    |
| `'someValue.substring(4)`  | "Value"    | String  |
| `'someValue'.length()`     | 9          | Integer |

## Accessing elements of a list or a record

| Expression                  | Result                                | Type     |
| ------------                | --------                              | -------- |
| `{1,2,3,4}[0]`              | 1                                     | Integer  |
| `{jan:300, alex:400}[alex]` | a value of field 'alex', which is 400 | Integer  |

## Filtering lists
                          
Special variable `#this` is used to operate on single element of list.

| Expression                                | Result       | Type          |
| ------------                              | --------     | --------      |
| `{1,2,3,4}.?[#this ge 3]`                 | {3, 4}       | List[Integer] |
| `#usersList.?[#this.firstName == 'john']` | {'john doe'} | List[String]  |

## Mapping lists

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

## Safe navigation

When you access nested structure, you have to take care of null fields, otherwise you'll end up with 
error. SpEL provides helpful safe navigation operator, it's basically shorthand for conditional operator:
`#someVar?.b` means `#someVar != null ? #someVar.b : null`

| Expression        | Result                         | Type                           |
| ------------      | --------                       | --------                       |
| `null.someField`  | java.lang.NullPointerException | java.lang.NullPointerException |
| `null?.someField` | null                           | Null                           |

## Invoking static methods

It is possible to invoke Java static methods directly with SpEL. Nussknacker can prevent invocations
of some of them due to security reasons. Invoking static methods is advanced functionality, which can lead
to incomprehensible expressions, also code completions will not work with many of them. 
If you need to invoke the same method in many places, probably the best solution is to create additional helper.

| Expression             | Result    | Type     |
| ------------           | --------  | -------- |
| `T(java.lang.Math).PI` | 3.14159.. | Double   |

## Chaining with dot
| Expression                                                   | Result    | Type     |
| ------------                                                 | --------  | -------- |
| `{1, 2, 3, 4}.?[#this > 1].![#this > 2 ? #this * 2 : #this]` | {2, 6, 8} | Double   |

## Type conversions

SpEL has many built-in implicit conversions that are available also in Nussknacker. Mostly conversions between various
numeric types and between `String` and some useful logical value types. Some examples:

| Input value                              | Input type | Conversion target type |
| -----------                              | ---------- | ---------------------- |
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

You can also use explicit conversions that are available in utility classes and build-in java conversion mechanisms:

| Expression                                                      | Result                    | Type           |
| ------------                                                    | --------                  | --------       |
| `#NUMERIC.toNumber('42')`                                       | 42                        | Number         |
| `#NUMERIC.toNumber('42').toString()`                            | '42'                      | String         |
| `#DATE_FORMAT.parseOffsetDateTime('2018-10-23T12:12:13+00:00')` | 1540296720000             | OffsetDateTime |
| `#DATE_FORMAT.parseLocalDateTime('2018-10-23T12:12:13')`        | 2018-10-23T12:12:13+00:00 | LocalDateTime  |
| `'' + 42`                                                       | '42'                      | String         |


## Built-in helpers 

| Helper        | Functions                                      |
| -------       | -----------                                    |
| `GEO`         | Simple distance measurements                   |
| `NUMERIC`     | Number parsing                                 |
| `CONV`        | General conversion functions                   |
| `DATE`        | Date operations (conversions, useful helpers)  |
| `DATE_FORMAT` | Date formatting/parsing operations             |
| `UTIL`        | Various utilities (e.g. identifier generation) |
| `AGG`         | Aggregator functions                           |
