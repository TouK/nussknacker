---
# front matter metadata for Docusaurus - this document is NOT intended for Docusaurs
title: Functions and Methods listing for the Assistant  
description: Functions and Methods listing for the Assistant  
# front matter metadata for LLM

keywords: ["functions", "methods", "SpEL", "expressions"]
---

# Functions and Methods Listing

<!-- This document is for Assistant only - it is more more comprehensive that the document for humans, however more repetitive -->

Functions and methods can be used in all [expression types](/docs/scenariosAuthoring/introduction.mdx#ways-of-providing-parameter-values) that support SpEL evaluation. 


## Function groups
### BASE64 
```
.decode(String) -> String - decode Base64 value to String
.encode(String) -> String - encode String value to Base64
.urlSafeDecode(String) -> String - decode URL-safe Base64 value to String
.urlSafeEncode(String) -> String - encode String value to URL-safe Base64
```

### COLLECTION
```
.concat(java.util.List) -> java.util.List - concatenates lists
.diff(java.util.List,java.util.List) -> java.util.List - returns a list that contains all elements contained in list1, that don't appear in list2
.distinct(java.util.List) -> java.util.List - returns a list that contains unique elements from the given list
.flatten(java.util.Collection) -> java.util.List - returns a list of all elements from all lists in the given list
.intersect(java.util.List,java.util.List) -> java.util.List - returns a list that contains all unique elements that are contained by both list1 and list2
.join(java.util.List,String) -> String - creates a string made of all elements of the list separated with the given separator
.max(java.util.Collection) -> Comparable - returns the largest element (elements must be comparable)
.merge(java.util.Map) -> java.util.Map - merges maps. Values in the first map will be overwritten with values from the other one if keys are the same
.min(java.util.Collection) -> Comparable - returns the smallest element (elements must be comparable)
.product(java.util.List,java.util.List) -> java.util.List - cross joins two lists of maps: eg. product({{a: 'a'},{b: 'b'}}, {{c: 'c'},{d: 'd'}}) => {{a: 'a',c: 'c'},{b: 'b',c: 'c'},{a: 'a',d: 'd'},{b: 'b',d: 'd'}}
.reverse(java.util.List) -> java.util.List - returns a list that contains elements in reversed order from the given list
.shuffle(java.util.Collection) -> java.util.List - returns a copy of the list with its elements shuffled
.slice(java.util.Collection,Integer,Integer) -> java.util.List - returns a slice of the list starting with start index (inclusive) and ending at stop index (exclusive)
.sortedAsc(java.util.Collection) -> java.util.List - returns a list of all elements sorted in ascending order (elements must be comparable)
.sortedAscBy(java.util.Collection,String) -> java.util.List - returns a list of all elements sorted by record field in ascending order (elements must be comparable)
.sortedDesc(java.util.Collection) -> java.util.List - returns a list of all elements sorted in descending order (elements must be comparable)
.sum(java.util.Collection) -> Number - returns a sum of all elements
.take(java.util.List,Integer) -> java.util.List - returns a list made of first n elements of the given list
.takeLast(java.util.List,Integer) -> java.util.List - returns a list made of last n elements of the given list
```

### CONV
```
.toAny(Unknown) -> Unknown - wrap param in 'Unknown' type to make it usable in places where type checking is too much restrictive
.toJson(String) -> Unknown - convert String value to JSON
.toJsonOrNull(String) -> Unknown - convert String value to JSON or null in case of failure
.toJsonString(Unknown) -> String - convert JSON to String
.toNumber(String or Number) -> Number - deprecated - will be removed in 1.19
```
### DATE
```
.APRIL -> java.time.Month
.AUGUST -> java.time.Month
.DECEMBER -> java.time.Month
.FEBRUARY -> java.time.Month
.FRIDAY -> java.time.DayOfWeek
.JANUARY -> java.time.Month
.JULY -> java.time.Month
.JUNE -> java.time.Month
.MARCH -> java.time.Month
.MAY -> java.time.Month
.MONDAY -> java.time.DayOfWeek
.NOVEMBER -> java.time.Month
.OCTOBER -> java.time.Month
.SATURDAY -> java.time.DayOfWeek
.SEPTEMBER -> java.time.Month
.SUNDAY -> java.time.DayOfWeek
.THURSDAY -> java.time.DayOfWeek
.TUESDAY -> java.time.DayOfWeek
.UTCOffset -> java.time.ZoneOffset - returns UTC time zone offset
.WEDNESDAY -> java.time.DayOfWeek
.defaultTimeZone -> java.time.ZoneId - returns default time zone
.durationBetween(java.time.temporal.Temporal,java.time.temporal.Temporal) -> java.time.Duration - computes Duration between two dates: start date inclusive and end date exclusive
.isBetween(java.time.LocalDate,java.time.LocalDate,java.time.LocalDate) -> Boolean - checks if day is in range <fromInclusive, toInclusive>.
.isBetween(java.time.LocalDateTime,java.time.LocalDateTime,java.time.LocalDateTime) -> Boolean - checks if day is in range <fromInclusive, toInclusive>.
.isBetween(java.time.DayOfWeek,java.time.DayOfWeek,java.time.DayOfWeek) -> Boolean - checks if day of week is in range <fromInclusive, toInclusive>. if to < from in ISO standard (numerous from MONDAY), then checks if day of week is in one of ranges <from, SUNDAY> and <MONDAY, to>
.isBetween(java.time.Month,java.time.Month,java.time.Month) -> Boolean - checks if month is in range <fromInclusive, toInclusive>. if to < from, then checks if month is in one of ranges <from, DECEMBER> and <JANUARY, to>
.isBetween(java.time.LocalTime,java.time.LocalTime,java.time.LocalTime) -> Boolean - checks if time is in range <fromInclusive, toInclusive>. if to < from, then checks if time is in one of ranges <from, 24:00> and <00:00, to>
.localDateTime(java.time.LocalDate,java.time.LocalTime) -> java.time.LocalDateTime - returns LocalDateTime based on LocalDate and LocalTime
.now -> java.time.Instant - returns current time as an Instant
.nowAtDefaultTimeZone -> java.time.ZonedDateTime - returns current time at default time zone as a ZonedDateTime
.nowAtOffset(java.time.ZoneOffset) -> java.time.OffsetDateTime - returns current time with given time zone offset as an OffsetDateTime
.nowAtZone(java.time.ZoneId) -> java.time.ZonedDateTime - returns current time at given time zone as a ZonedDateTime
.periodBetween(java.time.LocalDate,java.time.LocalDate) -> java.time.Period - computes Period between two dates: start date inclusive and end date exclusive
.periodBetween(java.time.OffsetDateTime,java.time.OffsetDateTime) -> java.time.Period - computes Period between two dates: start date inclusive and end date exclusive
.periodBetween(java.time.ZonedDateTime,java.time.ZonedDateTime) -> java.time.Period - computes Period between two dates: start date inclusive and end date exclusive
.toEpochMilli(java.time.LocalDateTime,java.time.ZoneId) -> Long - converts LocalDateTime at given time zone into epoch (millis from 1970-01-01)
.toEpochMilli(java.time.LocalDateTime,java.time.ZoneOffset) -> Long - converts LocalDateTime with given time zone offset into epoch (millis from 1970-01-01)
.toEpochMilli(java.time.OffsetDateTime) -> Long - converts OffsetDateTime into epoch (millis from 1970-01-01)
.toEpochMilli(java.time.ZonedDateTime) -> Long - converts ZonedDateTime into epoch (millis from 1970-01-01)
.toInstant(Long) -> java.time.Instant - converts epoch (millis from 1970-01-01) into an Instant
.toInstantAtDefaultTimeZone(java.time.LocalDateTime) -> java.time.Instant - converts LocalDateTime at default time zone into an Instant
.zone(String) -> java.time.ZoneId - returns time zone with given zone id e.g. Europe/Warsaw
.zoneOffset(String) -> java.time.ZoneOffset - returns zone offset with given zone offset id e.g. +01:00
.zuluTimeZone -> java.time.ZoneId - returns Zulu time zone which has offset always equals to UTC+0
```
### DATE_FORMAT
```
.format(java.time.temporal.TemporalAccessor) -> String - render LocalTime, LocalDate, LocalDateTime, OffsetDateTime or ZonedDateTime in ISO-8601 format
.formatter(String) -> java.time.format.DateTimeFormatter - creates DateTimeFormatter using given pattern
.formatter(String,java.util.Locale) -> java.time.format.DateTimeFormatter - creates DateTimeFormatter using given pattern and locale
.lenientFormatter(String) -> java.time.format.DateTimeFormatter - creates lenient version of DateTimeFormatter using given pattern
.lenientFormatter(String,java.util.Locale) -> java.time.format.DateTimeFormatter - creates lenient version of DateTimeFormatter using given pattern and locale
.parseInstant(String) -> java.time.Instant - parse Instant in ISO-8601 format e.g. '2011-12-03T10:15:30Z'
.parseLocalDate(String) -> java.time.LocalDate - parse LocalDate in ISO-8601 format e.g. '2011-12-03'
.parseLocalDate(String,String) -> java.time.LocalDate - parse LocalDate in DateTimeFormatter format
.parseLocalDate(String,java.time.format.DateTimeFormatter) -> java.time.LocalDate - parse LocalDate using given formatter
.parseLocalDateTime(String) -> java.time.LocalDateTime - parse LocalDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30'
.parseLocalDateTime(String,String) -> java.time.LocalDateTime - parse LocalDateTime in DateTimeFormatter format
.parseLocalDateTime(String,java.time.format.DateTimeFormatter) -> java.time.LocalDateTime - parse LocalDateTime using given formatter
.parseLocalTime(String) -> java.time.LocalTime - parse LocalTime in ISO-8601 format e.g. '10:15' or '10:15:30'
.parseLocalTime(String,String) -> java.time.LocalTime - parse LocalTime in DateTimeFormatter format
.parseLocalTime(String,java.time.format.DateTimeFormatter) -> java.time.LocalTime - parse LocalTime using given formatter
.parseOffsetDateTime(String) -> java.time.OffsetDateTime - parse OffsetDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30+01:00'
.parseOffsetDateTime(String,String) -> java.time.OffsetDateTime - parse OffsetDateTime in DateTimeFormatter format
.parseOffsetDateTime(String,java.time.format.DateTimeFormatter) -> java.time.OffsetDateTime - parse OffsetDateTime using given formatter
.parseZonedDateTime(String) -> java.time.ZonedDateTime - parse ZonedDateTime in ISO-8601 format e.g. '2011-12-03T10:15:30+01:00[Europe/Paris]'
.parseZonedDateTime(String,String) -> java.time.ZonedDateTime - parse ZonedDateTime in DateTimeFormatter format
.parseZonedDateTime(String,java.time.format.DateTimeFormatter) -> java.time.ZonedDateTime - parse ZonedDateTime using given formatter
```
### GEO
```
.azimuth(Number latitude1, Number longitude1, latitude2 Number, Number longitude2)` -> Double - calculate initial bearing (azimuth) in degrees from the first point to the second point. Result in range [0, 360).
.distanceInKm(Number,Number,Number,Number) -> Double - calculate distance in km between two points (with decimal coordinates), using haversine algorithm
.closestPointOnLine(Number latitudeA, Number longitudeA, Number latitudeB, Number longitudeB, Number latitudeC, Number longitudeC)` -> List[Double] - calculate the closest point on the line segment AB to point C. Returns [latitude, longitude].
.midpoint(Number latitude1, Number longitude1, Number latitude2, Number longitude2)` -> List[Double] - calculate geographic midpoint between two points on Earth. Returns [latitude, longitude].
```
### NUMERIC
```
.abs(Number) -> Number - returns the absolute value of a value.
.ceil(Double) -> Double - returns the smallest (closest to negative infinity) double value that is greater than or equal to the argument and is equal to a mathematical integer.
.divide(Number,Number) -> Number
.equal(Number,Number) -> Boolean
.floor(Double) -> Double - returns the largest (closest to positive infinity) value that is less than or equal to the argument and is equal to a mathematical integer.
.greater(Number,Number) -> Boolean
.greaterOrEqual(Number,Number) -> Boolean
.largeSum(Number,Number) -> Number
.lesser(Number,Number) -> Boolean
.lesserOrEqual(Number,Number) -> Boolean
.max(Number,Number) -> Number
.min(Number,Number) -> Number
.minus(Number,Number) -> Number
.multiply(Number,Number) -> Number
.negate(Number) -> Number
.notEqual(Number,Number) -> Boolean
.plus(Number,Number) -> Number
.pow(Double,Double) -> Double - returns the value of the first argument raised to the power of the second argument
.remainder(Number,Number) -> Number
.round(Double) -> Double - returns the closest long to the argument. The result is rounded to an integer by adding 1/2, taking the floor of the result, and casting the result to type long.
.sum(Number,Number) -> Number
.toNumber(String or Number) -> Number - parse string to number
```
### RANDOM
```
.nextBoolean -> Boolean - returns an uniformly distributed boolean value
.nextBooleanWithSuccessRate(Double) -> Boolean - returns a boolean value with a success rate determined by the given parameter. Success rate should be between 0.0 and 1.0. For 0.0 always returns false and for 1.0 always returns true
.nextDouble -> Double - returns an uniformly distributed double value between 0.0 (inclusive) and 1.0 (exclusive)
.nextDouble(Double,Double) -> Double - returns an uniformly distributed double value between fromInclusive and toExclusive
.nextDouble(Double) -> Double - returns an uniformly distributed double value between 0.0 and toExclusive
.nextInt(Integer,Integer) -> Integer - returns an uniformly distributed integer value between fromInclusive and toExclusive
.nextInt(Integer) -> Integer - returns an uniformly distributed integer value between 0 and toExclusive
.nextLong(Long,Long) -> Long - returns an uniformly distributed long value between fromInclusive and toExclusive
.nextLong(Long) -> Long - returns an uniformly distributed long value between 0 and toExclusive
```
### UTIL
```
.split(String,String) -> java.util.List - splits given text into a list of Strings using given regular expression. Note that trailing empty strings won't be discarded; e.g. split('a|b|', '|') returns a list of three elements, the last of which is an empty string
.uuid -> String - generate unique identifier (https://en.wikipedia.org/wiki/Universally_unique_identifier)
```


## Methods

As explained, methods are directly applied to values (also variables) using the dot (".") operator. 
### Context specific methods
 

```
.canBe(String) -> Boolean - checks if a value can be converted to a given class
.canBeBigDecimal -> Boolean - check whether the value can be converted to a BigDecimal
.canBeBoolean -> Boolean - check whether the value can be converted to a Boolean
.canBeDouble -> Boolean - check whether the value can be converted to a Double
.canBeList -> Boolean - check whether can be converted to a list
.canBeLong -> Boolean - check whether the value can be converted to a Long
.canBeInteger -> Boolean - check whether the value can be converted to a Integer
.canBeMap -> Boolean - check whether can be converted to a map
.to(String) -> Unknown - convert a type to a given class or throws exception if type cannot be converted.
.toBigDecimal -> java.math.BigDecimal - convert the value to BigDecimal or throw exception in case of failure
.toBigDecimalOrNull -> java.math.BigDecimal - convert the value to BigDecimal or null in case of failure
.toBoolean -> Boolean - convert the value to Boolean or throw exception in case of failure
.toBooleanOrNull -> Boolean - convert the value to Boolean or null in case of failure
.toDouble -> Double - convert the value to Double or throw exception in case of failure
.toDoubleOrNull -> Double - convert the value to Double or null in case of failure
.toList -> java.util.List - convert to a list or throw exception in case of failure
.toListOrNull -> java.util.List - convert to a list or null in case of failure
.toLong -> Long - convert the value to Long or throw exception in case of failure
.toLongOrNull -> Long - convert the value to Long or null in case of failure
.toInteger -> Integer - convert the value to Integer or throw exception in case of failure
.toIntegerOrNull -> Integer - convert the value to Integer or null in case of failure
.toMap -> java.util.Map - convert to a map or throw exception in case of failure
.toMapOrNull -> java.util.Map - convert to a map or null in case of failure
.toOrNull(String) -> Unknown - converts a type to a given class or return null if type cannot be converted.
.toString -> String
```