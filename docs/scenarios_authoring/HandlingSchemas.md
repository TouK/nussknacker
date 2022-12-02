---
sidebar_position: `11
---`

# Handling Schemas

## Overview

Nussknacker supports two types of schema:
* [JSON Schema](https://json-schema.org/) in version: `Draft 7`
* [Avro Schema](https://avro.apache.org/) in version: `1.11.0`

Avro Schema nad JSON Schema are available on [Streaming Source/Sik](/docs/scenarios_authoring/DataSourcesAndSinks.md) 
in contrast to [RequestResponse Source/Sink](/docs/scenarios_authoring/RRDataSourcesAndSinks.md) where available is just only one - JSON Schema.

Nussknacker converts `source schema` to own static `Typing` information to create hinting on the FE Designer,
whereas `sink schema` is used to validation output data.

## Source conversion
Conversion at srouce to specific type means that behind the scene Nussknacker converts `primitive type` to `logical type`,
consequently end user has access to object's methods, e.g. let's imagine schema contains field `foo` with logical type `Date`. 
Originally event contains data with field `foo` where is stored `Long`. After conversion, field `foo` will be converted to `Date`,
and the end user on Designer will have access to `#input.foo` with type `Date` and can do `#input.foo.toInstant`. 

## Avro Schema

### Source supports & conversions

* Primitive types: all
  * String schema is by default converted to `java.lang.String`. You can change default behavior and convert 
    String schema to `java.lang.CharSequence` by set env `AVRO_USE_STRING_FOR_STRING_TYPE=false`.
* Complex types: all
* Logical types with conversion:
  * `UUID` => `java.util.UUID`
  * `Decimal` => `java.math.BigDecimal`
  * `Date` => `java.time.LocalDate`
  * `Time (millisecond precision)` => `java.time.LocalTime`
  * `Time (microsecond precision)` => `java.time.LocalTime`
  * `Timestamp (millisecond precision)` => `java.time.Instant`
  * `Timestamp (microsecond precision)` => `java.time.Instant`

[//]: # (Missing support for: Local timestamp millisecond precision, Local timestamp microsecond precision, Duration)

### Sink validation & encoding

In avro we accept on outputs all compatible types with schemas, and also we allow to pass primitives for logical type: 
* `java.lang.String` => `UUID`
* `java.lang.Int`  => `Date`
* `java.lang.Int` => `Time (millisecond precision)`
* `java.lang.Long` => `Time (microsecond precision)`
* `java.lang.Long` => `Timestamp (millisecond precision)`
* `java.lang.Long` => `Timestamp (microsecond precision)`

If any value will be not proper logical type (e.g. "str" => UUID) then exception on runtime will be thrown.

## JSON Schema

To handling JSON Schema Nussknacker uses `everit-json-schema` in version `1.14.1`, and this lib provides some 
inconsistency against JSON Schema, e.g.

* integer schema should [accept value 1.0](https://json-schema.org/understanding-json-schema/reference/numeric.html#integer) but
  everit validation throws exception. This issue occurs only on deserialization data (source), on serialization (sink) we handled it.

[//]: # (See SwaggerBasedJsonSchemaTypeDefinitionExtractor)
Nussknacker doesn't support:
* Nested relative schemas
* Recursive schemas
* Anchors

### Source supports & conversions

* Primitive types: all with some conversions
  * [Integer](https://json-schema.org/understanding-json-schema/reference/numeric.html#integer) => `java.lang.Long`
    * If we pass number bigger then `java.lang.Long.MAX_VALUE`, value will be rounded to `java.lang.Long.MAX_VALUE`
    * TODO: Support for [range](https://json-schema.org/understanding-json-schema/reference/numeric.html#range) and do proper conversion to: Int/Long/BigInteger
  * [Number](https://json-schema.org/understanding-json-schema/reference/numeric.html#number) => `java.math.BigDecimal`
    * TODO: Support for [range](https://json-schema.org/understanding-json-schema/reference/numeric.html#range) and do proper conversion to: Float/Double/BigDecimal
  * [String](https://json-schema.org/understanding-json-schema/reference/string.html#format) with [formats](https://json-schema.org/understanding-json-schema/reference/string.html#format):
    * `date-time` => `java.time.ZonedDateTime`
    * `date` => `java.time.LocalDate`
    * `time` => `java.time.OffsetTime`
* [Enum](https://json-schema.org/understanding-json-schema/reference/generic.html#enumerated-values) => `java.lang.String`
* `Object` => `java.util.HashMap`
  * Static properties (fields) typing
  * `additionalProperties` are supported, but additional fields won't be available during the hinting on the Designer. 
  To get add field you have to do `#inpute.get("additional-field")`, but remember result of this expression will be `Unknown`.
  * Object without properties and `additionalProperties` is treated as `Map[String, Type]`, where `Type` is depends on
  `additionalProperties` configuration: `true` means `Unknown`, `{"type": }` will be converted to specific type.
* Array
* [Schema Composition](https://json-schema.org/understanding-json-schema/reference/combining.html)
  * Nussknacker supports just `anyOf` and `oneOf` to build static typing information about field. 

### Sink validation & encoding

Nussknacker doesn't support `object validation` (on the Designer level):
* [Pattern properties](https://json-schema.org/understanding-json-schema/reference/object.html#pattern-properties)
* [UnevaluatedProperties](https://json-schema.org/understanding-json-schema/reference/object.html#unevaluated-properties)
* [Extending Closed Schemas](https://json-schema.org/understanding-json-schema/reference/object.html#extending-closed-schemas)
* [Property names](https://json-schema.org/understanding-json-schema/reference/object.html#property-names)
* [Size](https://json-schema.org/understanding-json-schema/reference/object.html#size)

Nussknacker doesn't support `array validation` (on the Designer level):
* [Additional Items](https://json-schema.org/understanding-json-schema/reference/array.html#additional-items)
* [Tuple validation](https://json-schema.org/understanding-json-schema/reference/array.html#tuple-validation)
* [Contains](https://json-schema.org/understanding-json-schema/reference/array.html#contains)
* [Min/Max](https://json-schema.org/understanding-json-schema/reference/array.html#mincontains-maxcontains)
* [Length](https://json-schema.org/understanding-json-schema/reference/array.html#length)
* [Uniqueness](https://json-schema.org/understanding-json-schema/reference/array.html#uniqueness)

These, configurations are supported by everit and on trying to serialize data will be blown up.

Nussknacker supports only `anyOf` from `schema composition`. We decided that option is closest to union. Of course, you can use
others compositions but at the end error will be thrown on serialization.
