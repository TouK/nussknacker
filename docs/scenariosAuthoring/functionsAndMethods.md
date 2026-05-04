---
# front matter metadata for Docusaurus
title: Transforming data 
description: Perform arithmetical computations, manipulate date and time, lists (collections), geolocation information, etc
sidebar_label: Transforming Data
# sidebar_position: 


# front matter metadata for LLM
keywords: ["functions", "methods", "SpEL", "expressions"]
---

# Transforming Data

:::note
Functions and methods can be used in all [expression types](/docs/scenariosAuthoring/introduction.mdx#ways-of-providing-parameter-values) that support SpEL evaluation. 
:::

:::note
Check [here](/docs/scenariosAuthoring/SpEL.md#unknown) how to handle Unknown data type.
:::

## Introduction

Expressions in Nussknacker can use a wide range of built-in **functions** and **methods** which can be used to test for specific conditions, perform computations, convert between data types, manipulate lists (collections) and many more. 

 
This page lists available functions and methods.
## Methods


Methods operate on specific data types (for example, string or list values) and are invoked using the dot notation, for example:

```
"John".length
#stringVariable.contains("my")
```
:::note
You can check methods available in particular context ("on the data type of the current value") by typing dot (".") at the end of the current expression (no space characters in between)
:::

Methods can also be chained, meaning the result of one method becomes the input to the next:
`"myString".contains("my").toString.length`
Here the result of the `contains` method (`true`) is chained to `toString` method which yields `"true"` and in turn chained to `length` method yielding integer value of 4. 

See also [here](/docs/scenariosAuthoring/SpEL.md#chaining-with-dot) for more examples of chaining.

## Common tasks (practical usage)

### Date/time handling

Check the [SpeL Cheat Sheet](/docs/scenariosAuthoring/SpEL.md#datetime) to understand the data types use to store information about data and time. In particular, note the difference between LocalDateTime, OffsetDateTime and ZonedDateTime - ignoring it can lead to errors which can be difficult to spot. 

#### Most common case: convert a string to a usable date/time value

If your input string is already in ISO-8601 format, use the appropriate parse... function directly:

`#DATE_FORMAT.parseInstant("2024-03-01T09:30:00Z")`
`#DATE_FORMAT.parseLocalDate("2024-03-01")`
`#DATE_FORMAT.parseLocalTime("10:15:30")`
`#DATE_FORMAT.parseLocalDateTime("2024-03-01T09:30:00")`
`#DATE_FORMAT.parseOffsetDateTime("2024-03-01T09:30:00+01:00")`
`#DATE_FORMAT.parseZonedDateTime("2024-03-01T09:30:00+01:00[Europe/Warsaw]")`

If your input string is not in ISO-8601 format, provide a format pattern describing how the string is structured:

`#DATE_FORMAT.parseLocalDateTime("20260130-08:43:08.851", "yyyyMMdd-HH:mm:ss.SSS")`

You can also pass a DateTimeFormatter created with `#DATE_FORMAT.formatter(...)` or `#DATE_FORMAT.lenientFormatter(...)`.

#### Creating date/time values (practical entry points)

Most date/time logic starts with “give me a value of type X”.

| Goal | Expression | Result type | Notes |
|---|---|---|---|
| “now” as an absolute instant | `#DATE.now` | `Instant` | timezone-independent |
| “now” in server default timezone | `#DATE.nowAtDefaultTimeZone` | `ZonedDateTime` | uses `#DATE.defaultTimeZone` |
| “now” in a specific timezone | `#DATE.nowAtZone(#DATE.zone('Europe/Warsaw'))` | `ZonedDateTime` | explicit `ZoneId` creation |
| “now” with a specific offset | `#DATE.nowAtOffset(#DATE.zoneOffset('+01:00'))` | `OffsetDateTime` | explicit `ZoneOffset` creation |
| “now” as an ISO string | `#DATE_FORMAT.format(#DATE.now)` | `2026-03-20T17:05:59.861948847Z` |  |
| “now” as an ISO string with zone info | `#DATE_FORMAT.format(#DATE.now.atZone("Europe/Warsaw"))` | `2026-03-20T18:05:59.862164321+01:00[Europe/Warsaw]` |  |
| “now” as a custom formatted string | `#DATE_FORMAT.formatter("yyyy-MM-dd HH VV").format(#DATE.nowAtZone("Europe/Warsaw"))` | `2026-03-20 18 Europe/Warsaw` |  |
| parse string with ISO datetime info | `#DATE_FORMAT.parseInstant('2024-03-01T09:30:00Z')` | `Instant` | explicit parsing |
| parse string with ISO time | `#DATE_FORMAT.parseLocalTime('10:15:30')`         | `LocalTime` | explicit time string parsing |
| parse string with ISO LocalDate | `#DATE_FORMAT.parseLocalDate('2024-03-01')` | `LocalDate` | explicit parsing |
| combine date + time | `#DATE.localDateTime(#dateVar, #timeVar)` | `LocalDateTime` | avoids string concatenation |
| parse string with ISO ZonedDateTime | `#DATE_FORMAT.parseZonedDateTime('2024-03-01T09:30:00+01:00[Europe/Warsaw]')` | `ZonedDateTime` | explicit parsing |
| parse string with ISO OffsetDateTime | `#DATE_FORMAT.parseOffsetDateTime('2024-03-01T09:30:00+01:00')` | `OffsetDateTime` | explicit parsing |



#### Conversions between date/time types (condensed)

Most workflows follow this path: `String` → `LocalDateTime` → `ZonedDateTime` → `Instant` → `Long` epoch millis.

##### Most common conversions

| From → To | Expression | Notes |
|---|---|---|
| `ISO datetime string with zone info` → `ZonedDateTime` | `#DATE_FORMAT.parseZonedDateTime("2026-01-30T08:43:08.851Z")` | Z → UTC (“Zulu”) timezone (no offset) |
| `ISO datetime string without zone info` → `LocalDateTime` | `#DATE_FORMAT.parseLocalDateTime("2026-01-30T08:43:08.851")` |  |
| `datetime string in non ISO format` → `LocalDateTime` | `#DATE_FORMAT.parseLocalDateTime("20260130-08:43:08.851", "yyyyMMdd-HH:mm:ss.SSS")` |  |
| `LocalDateTime`→ `ZonedDateTime` | `#localDateTimeVar.atZone(#DATE.zone('Europe/Warsaw'))` |  |
| `ZonedDateTime` →  `Instant`| `#zonedDateTimeVar.toInstant` | |
| `Instant` → `Long` (epoch millis) | `#instantVar.toEpochMilli` | |
| `Long` (epoch millis) → `Instant` | `#DATE.toInstant(#millis)` | absolute time |

##### Other conversions

| From → To | Expression | Notes |
|---|---|---|
| `string with zone` → `ZonedDateTime` | `#DATE_FORMAT.parseZonedDateTime("2026-01-30T08:43:08.851Z")` | `Z` = UTC |
| `ZonedDateTime` → `Long (epoch millis)` | `#DATE.toEpochMilli(#zoneDatetimeVar)` | absolute timestamp |
| `OffsetDateTime` → `Long (epoch millis)` | `#DATE.toEpochMilli(#datetimeWithOffsetVar)` | absolute timestamp |
| `LocalDateTime` → `Long (epoch millis)` | `#DATE.toEpochMilli(#localDatetimeVar,  #DATE.defaultTimeZone)` | requires timezone |
| `LocalDateTime` → `Long (epoch millis)` | `#DATE.toEpochMilli(#localDatetimeVar, #DATE.UTCOffset)` | requires offset |
| `LocalDateTime` → `Instant` | `#DATE.toInstantAtDefaultTimeZone(#localDatetimeVar)` | assumes server default TZ |
| `Instant` → `ZonedDateTime` | `#instant.atZone(#DATE.defaultTimeZone)` | attach timezone |
| `Instant` → `OffsetDateTime` | `#instant.atOffset(#DATE.UTCOffset)` | attach offset |
| `LocalDate` → `LocalDateTime` | `#dateVar.atStartOfDay()` | midnight |
| `LocalDateTime` → `LocalDate` | `#localDatetimeVar.toLocalDate()` | truncation of time information |


:::note
`LocalDateTime` does *not* represent a specific moment in time. When you need an absolute timestamp (`Instant` / `Long`), you must provide a timezone or offset.
:::

#### DATE vs DATE_FORMAT helper

- #DATE — computations and conversions on date/time values (e.g. “now”, ranges, durations, epoch conversion)
- #DATE_FORMAT — conversion between String and date/time values (parsing and formatting)

For full reference check [here](#date---date-and-time-utility-functions) and [here](#date_format---parsing-and-formatting-between-string-and-datetime-types). 

For full list of available format options take a look at [DateTimeFormatter api docs](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html).



#### Date/time functions

Check [date / time utility functions](#date---date-and-time-utility-functions) for details of date/time related functions.
For full list of available format options take a look at [DateTimeFormatter api docs](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html).



## Functions 

Functions are organized into themed **function groups** - such as `NUMERIC`, `DATE` or `GEO`- each group providing a set of related functions. 

Below is an example of calling an `abs()` function; this function is the NUMERIC function group.

`#NUMERIC.abs(-6.7)`

The function group name must be preceded with the hash (#) character.

:::note
You can check functions available in a function group by typing dot (".") after the function group name (no space in between)
:::

### Built-in function groups - reference

Nussknacker comes with the following function groups:

| Function group | Functions                                                          |
|---------------|--------------------------------------------------------------------|
| `BASE64`      | Encoding \& decoding [Base64](https://en.wikipedia.org/wiki/Base64) |
| `COLLECTION`  | Operations on collections                                          |
| `CONV`        | General conversion functions                                       |
| `DATE`        | Date conversions and computations                      |
| `DATE_FORMAT` | Date formatting/parsing operations                                 |
| `GEO`         | Simple distance measurements                                       |
| `NUMERIC`     | Number parsing                                                     |
| `RANDOM`      | Random value generators                                            |
| `UTIL`        | Various utilities (e.g. identifier generation)                     |

### BASE64 encoding and decoding

- `#BASE64.decode(String)` - decode Base64 value to String
- `#BASE64.encode(String)` - encode String value to Base64
- `#BASE64.urlSafeDecode(String)` - decode URL-safe Base64 value to String
- `#BASE64.urlSafeEncode(String)` - encode String value to URL-safe Base64

### COLLECTION - operations on collections

- `#COLLECTION.(List, List)` - concatenates lists, returns List
- `#COLLECTION.diff(List,List)` - returns a list that contains all elements contained in list1, that don't appear in list2
- `#COLLECTION.distinct(List)` - returns a list that contains unique elements from the given list
- `#COLLECTION.flatten(Collection)` - returns a list of all elements from all lists in the given list
- `#COLLECTION.intersect(List,List)` - returns a list that contains all unique elements that are contained by both list1 and list2
- `#COLLECTION.join(List,String)` - creates a string made of all elements of the list separated with the given separator
- `#COLLECTION.max(Collection)` - returns the largest element (elements must be comparable)
- `#COLLECTION.merge(Map)` - merges maps. Values in the first map will be overwritten with values from the other one if keys are the same
- `#COLLECTION.min(Collection)` - returns the smallest element (elements must be comparable)
- `#COLLECTION.product(List,List)` - cross joins two lists of maps: eg. product({{a: 'a'},{b: 'b'}}, {{c: 'c'},{d: 'd'}}) => {{a: 'a',c: 'c'},{b: 'b',c: 'c'},{a: 'a',d: 'd'},{b: 'b',d: 'd'}}
- `#COLLECTION.reverse(List)` - returns a list that contains elements in reversed order from the given list
- `#COLLECTION.shuffle(Collection)` - returns a copy of the list with its elements shuffled
- `#COLLECTION.slice(Collection,Integer,Integer)` - returns a slice of the list starting with start index (inclusive) and ending at stop index (exclusive)
- `#COLLECTION.sortedAsc(Collection)` - returns a list of all elements sorted in ascending order (elements must be comparable)
- `#COLLECTION.sortedAscBy(Collection,String)` - returns a list of all elements sorted by record field in ascending order (elements must be comparable)
- `#COLLECTION.sortedDesc(Collection)` - returns a list of all elements sorted in descending order (elements must be comparable)
- `#COLLECTION.sum(Collection)` - returns a sum of all elements
- `#COLLECTION.take(List,Integer)` - returns a list made of first n elements of the given list
- `#COLLECTION.takeLast(List,Integer)` - returns a list made of last n elements of the given list

### CONV - conversion functions

- `#CONV.toAny(Unknown)` - wrap param in 'Unknown' type to make it usable in places where type checking is too much restrictive; returns Unknown
- `#CONV.toJson(String)` - convert String value to JSON; returns Unknown; check [here](/docs/scenariosAuthoring/SpEL.md#dynamic-navigation) how to access fields in this case
- `#CONV.toJsonOrNull(String)` - convert String value to JSON or null in case of failure; returns Unknown
- `#CONV.toJsonString(Unknown)` - convert JSON to String
- `#CONV.toNumber(String or Number)` - deprecated - will be removed in 1.19

### DATE - date and time utility functions 

`DATE` function group contains functions for date range checks and computations of periods and durations e.g.:

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

### DATE_FORMAT - parsing and formatting between String and date/time types
- `#DATE_FORMAT.format(TemporalAccessor)` -> `String` - render LocalTime, LocalDate, LocalDateTime, OffsetDateTime or ZonedDateTime in ISO-8601 format
- `#DATE_FORMAT.formatter(String)` -> `DateTimeFormatter` - creates DateTimeFormatter using given pattern
- `#DATE_FORMAT.formatter(String,Locale)` -> `DateTimeFormatter` - creates DateTimeFormatter using given pattern and locale
- `#DATE_FORMAT.lenientFormatter(String)` -> `DateTimeFormatter` - creates lenient version of DateTimeFormatter using given pattern
- `#DATE_FORMAT.lenientFormatter(String,Locale)` -> `DateTimeFormatter` - creates lenient version of DateTimeFormatter using given pattern and locale
- `#DATE_FORMAT.parseInstant(String)` -> `Instant` - parse Instant in ISO-8601 format e.g. '2011-12-03T10:15:30Z'
- `#DATE_FORMAT.parseLocalDate(String)` -> `LocalDate` - parse LocalDate in ISO-8601 format e.g. '2011-12-03'
- `#DATE_FORMAT.parseLocalDate(String,String)` -> `LocalDate` - parse LocalDate in DateTimeFormatter format
- `#DATE_FORMAT.parseLocalDate(String,DateTimeFormatter)` -> `LocalDate` - parse LocalDate using given formatter
- `#DATE_FORMAT.parseLocalDateTime(String)` -> `LocalDateTime` - parse LocalDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30'
- `#DATE_FORMAT.parseLocalDateTime(String,String)` -> `LocalDateTime` - parse LocalDateTime in DateTimeFormatter format
- `#DATE_FORMAT.parseLocalDateTime(String,DateTimeFormatter)` -> `LocalDateTime` - parse LocalDateTime using given formatter
- `#DATE_FORMAT.parseLocalTime(String)` -> `LocalTime` - parse LocalTime in ISO-8601 format e.g. '10:15' or '10:15:30'
- `#DATE_FORMAT.parseLocalTime(String,String)` -> `LocalTime` - parse LocalTime in DateTimeFormatter format
- `#DATE_FORMAT.parseLocalTime(String,DateTimeFormatter)` -> `LocalTime` - parse LocalTime using given formatter
- `#DATE_FORMAT.parseOffsetDateTime(String)` -> `OffsetDateTime` - parse OffsetDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30+01:00'
- `#DATE_FORMAT.parseOffsetDateTime(String,String)` -> `OffsetDateTime` - parse OffsetDateTime in DateTimeFormatter format
- `#DATE_FORMAT.parseOffsetDateTime(String,DateTimeFormatter)` -> `OffsetDateTime` - parse OffsetDateTime using given formatter
- `#DATE_FORMAT.parseZonedDateTime(String)` -> `ZonedDateTime` - parse ZonedDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30+01:00[Europe/Paris]'
- `#DATE_FORMAT.parseZonedDateTime(String,String)` -> `ZonedDateTime` - parse ZonedDateTime in DateTimeFormatter format
- `#DATE_FORMAT.parseZonedDateTime(String,DateTimeFormatter)` -> `ZonedDateTime` - parse ZonedDateTime using given formatter


### GEO - simple distance measurements

- `#GEO.azimuth(Number latitude1, Number longitude1, latitude2 Number, Number longitude2)` -> `Double` - calculate initial bearing (azimuth) in degrees from the first point to the second point. Result in range [0, 360).
- `#GEO.closestPointOnLine(Number latitudeA, Number longitudeA, Number latitudeB, Number longitudeB, Number latitudeC, Number longitudeC)` -> `List[Double]` - calculate the closest point on the line segment AB to point C. Returns [latitude, longitude].
- `#GEO.distanceInKm(Number latitude1, Number longitude1, Number latitude2, Number longitude2)` -> `Double` - calculate distance in km between two points (with decimal coordinates), using haversine algorithm
- `#GEO.midpoint(Number latitude1, Number longitude1, Number latitude2, Number longitude2)` -> `List[Double]` - calculate geographic midpoint between two points on Earth. Returns [latitude, longitude].
 
### NUMERIC - number parsing

- `#NUMERIC.abs(Number)` - returns the absolute value of a value.
- `#NUMERIC.ceil(Double)` - returns the smallest (closest to negative infinity) double value that is greater than or equal to the argument and is equal to a mathematical integer.
- `#NUMERIC.divide(Number,Number)`
- `#NUMERIC.equal(Number,Number)`
- `#NUMERIC.floor(Double)` - returns the largest (closest to positive infinity) value that is less than or equal to the argument and is equal to a mathematical integer.
- `#NUMERIC.greater(Number,Number)`
- `#NUMERIC.greaterOrEqual(Number,Number)`
- `#NUMERIC.largeSum(Number,Number)`
- `#NUMERIC.lesser(Number,Number)`
- `#NUMERIC.lesserOrEqual(Number,Number)`
- `#NUMERIC.max(Number,Number)`
- `#NUMERIC.min(Number,Number)`
- `#NUMERIC.minus(Number,Number)`
- `#NUMERIC.multiply(Number,Number)`
- `#NUMERIC.negate(Number)`
- `#NUMERIC.notEqual(Number,Number)`
- `#NUMERIC.plus(Number,Number)`
- `#NUMERIC.pow(Double,Double)` - returns the value of the first argument raised to the power of the second argument
- `#NUMERIC.remainder(Number,Number)`
- `#NUMERIC.round(Double)` - returns the closest long to the argument. The result is rounded to an integer by adding 1/2, taking the floor of the result, and casting the result to type long.
- `#NUMERIC.sum(Number,Number)`
- `#NUMERIC.toNumber(String or Number)` - parse string to number

### RANDOM - random values generators

- `#RANDOM.nextBoolean` - returns an uniformly distributed boolean value
- `#RANDOM.nextBooleanWithSuccessRate(Double)` - returns a boolean value with a success rate determined by the given parameter. Success rate should be between 0.0 and 1.0. For 0.0 always returns false and for 1.0 always returns true
- `#RANDOM.nextDouble` - returns an uniformly distributed double value between 0.0 (inclusive) and 1.0 (exclusive)
- `#RANDOM.nextDouble(Double,Double)` - returns an uniformly distributed double value between fromInclusive and toExclusive
- `#RANDOM.nextDouble(Double)` - returns an uniformly distributed double value between 0.0 and toExclusive
- `#RANDOM.nextInt(Integer,Integer)` - returns an uniformly distributed integer value between fromInclusive and toExclusive
- `#RANDOM.nextInt(Integer)` - returns an uniformly distributed integer value between 0 and toExclusive
- `#RANDOM.nextLong(Long,Long)` - returns an uniformly distributed long value between fromInclusive and toExclusive
- `#RANDOM.nextLong(Long)` - returns an uniformly distributed long value between 0 and toExclusive

### UTIL - utilities

- `#UTIL.split(String,String)` - splits given text into a List of Strings using given regular expression. Note that trailing empty strings won't be discarded; e.g. split('a|b|', '|') returns a list of three elements, the last of which is an empty string
- `#UTIL.uuid` - generate unique identifier (https://en.wikipedia.org/wiki/Universally_unique_identifier)