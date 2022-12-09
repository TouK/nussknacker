---
sidebar_position: 6
---

# Overview

![Typing architecture](./img/typing.png)

Nussknacker as a platform integrates many of the data sources, e.g. Kafka or Databases, and also allows to enrich data
using e.g. [OpenAPI](https://swagger.io/specification/) or Databases as well. These integrations can return several
types of data like JSON, Binary, and DB data. Each format of these data is described in another way:

* Kafka with JSON data is described by JSON Schema, stored in the schema registry
* Kafka with binary data is described by Avro Schema, stored in the schema registry
* [OpenAPI](https://swagger.io/specification/) returns JSON data described by OpenAPI Schema
* Database data are described by schema column information

To provide consistent and proper support for those formats Nussknacker converts meta-information about data to its
own `Typing Information`, which is used on the Designer's part to hint and verification the correctness of the data.
Each part of the diagram is statically validated and typed on an ongoing basis.

On the other hand, we also have to ensure that provided data to the sink (e.g. kafka topic with json or avro) are proper
against the schema and can be safely saved at the runtime. And this is the place where
the [validation and encoding](#validation-and-encoding) mechanisms come in.

# Validation and encoding

As we can see above finally saving data (e.g. kafka sink) is divided into two parts:

* validation data on the Designer
* encoding data at the runtime

Sometimes can happen situations that are not so obvious to handle, e.g. how we should pass and validate `Unknown`
and `Union` types. And this is the place where validation modes come in and help solve that issue.

##### type `Unknown`

A situation when Nussknacker can not detect type of data, it's similar to `java.lan.Object`.

##### type `Union`

A situation when the returning data can be any of [several representation](https://en.wikipedia.org/wiki/Union_type).

## Validation modes

|                                | Strict mode                | Lax mode                            | Comment                                                                                                                                               |
|--------------------------------|----------------------------|-------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| allow passing redundant fields | no                         | yes                                 | This option works only at Avro Schema. JSON Schema manages redundant fields itself explicitly by schema property: [`additionalProperties`](#objects). |
| require providing all fields   | yes                        | no                                  | Do we have to provide all the fields even those optional ones.                                                                                        |
| allow passing `Unknown`        | no                         | yes                                 | Be aware of it, because when data at the runtime will not match against the schema, then error be reported during encoding.                           |
| passing `Union`                | unions have to be the same | any of type from union should match | Be aware of it, because when data at the runtime will not match against the schema, then error be reported during encoding.                           |

The decision of which validation mode to choose we leave to the user. But be aware of it, and remember it only impacts
how we validate data on the Designer's part and some errors can still occur during encoding at the runtime.

# Avro Schema

We support [Avro Schema](https://avro.apache.org/) in version: `1.11.0`. Avro is available only
on [Streaming](/docs/scenarios_authoring/DataSourcesAndSinks.md). To integrate with this type of schema we
use [Schema Registry](/docs/integration/KafkaIntegration.md#schema-registry-integration).

### Source conversion mapping

#### [Primitive types](https://avro.apache.org/docs/1.11.0/spec.html#schema_primitive)

| Avro Schema | Java type     | Comment                                                                                                                                                |
|-------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| null        | null          |                                                                                                                                                        |
| string      | String / Char | String schema is by default represented by java UTF-8 String. Using advance config `AVRO_USE_STRING_FOR_STRING_TYPE=false`, you can change it to Char. |
| boolean     | Boolean       |                                                                                                                                                        |
| int         | Integer       | 32bit                                                                                                                                                  |
| long        | Long          | 64bit                                                                                                                                                  |
| float       | Float         | single precision                                                                                                                                       |
| double      | Double        | double precision                                                                                                                                       |
| bytes       | ByteBuffer    |                                                                                                                                                        |

#### [Logical types](https://avro.apache.org/docs/1.11.0/spec.html#Logical+Types)

Conversion at source to the specific type means that behind the scene Nussknacker
converts [primitive type](https://avro.apache.org/docs/1.11.0/spec.html#schema_primitive)
to [logical type](https://avro.apache.org/docs/1.11.0/spec.html#Logical+Types) - java objects, consequently, the
end-user has access to methods of these objects.

| Avro Schema                              | Java type  | Sample               | Comment                                                       |
|------------------------------------------|------------|----------------------|---------------------------------------------------------------|
| decimal (bytes or fixed)                 | BigDecimal |                      |                                                               |
| uuid (string)                            | UUID       |                      |                                                               |
| date (int)                               | LocalDate  | 2021-05-17           | Timezone is not stored.                                       |
| time - millisecond precision (int)       | LocalTime  | 07:34:00.12345       | Timezone is not stored.                                       |
| time - microsecond precision (long)      | LocalTime  | 07:34:00.12345       | Timezone is not stored.                                       |
| timestamp - millisecond precision (long) | Instant    | 2021-05-17T05:34:00Z | Timestamp (millis since 1970-01-01) in human readable format. |
| timestamp - microsecond precision (long) | Instant    | 2021-05-17T05:34:00Z | Timestamp (millis since 1970-01-01) in human readable format. |

[//]: # (Missing support for: Local timestamp millisecond precision, Local timestamp microsecond precision, Duration)

#### [Complex types](https://avro.apache.org/docs/1.11.0/spec.html#schema_complex)

| Avro Schema | Java type                                      | Comment                                                     |
|-------------|------------------------------------------------|-------------------------------------------------------------|
| array       | List[_]                                        |                                                             |
| map         | Map[String, _]                                 | Key - value map, where key is always represented by String. |
| record      | org.apache.avro.generic.GenericRecord          |                                                             |
| enums       | org.apache.avro.generic.GenericData.EnumSymbol |                                                             |
| fixed       | org.apache.avro.generic.GenericData.Fixed      |                                                             |
| union       | Any of the above types                         | It can be any of the defined type in union.                 |

### Sink validation & encoding

| Java type                                                 | Avro Schema                              | Comment                                                                                                 |
|-----------------------------------------------------------|------------------------------------------|---------------------------------------------------------------------------------------------------------|
| null                                                      | null                                     |                                                                                                         |
| String                                                    | string                                   |                                                                                                         |
| Boolean                                                   | boolean                                  |                                                                                                         |
| Integer                                                   | int                                      |                                                                                                         |
| Long                                                      | long                                     |                                                                                                         |
| Float                                                     | float                                    |                                                                                                         |
| Double                                                    | double                                   |                                                                                                         |
| ByteBuffer                                                | bytes                                    |                                                                                                         |
| List[_]                                                   | array                                    |                                                                                                         |
| Map[String, _]                                            | map                                      |                                                                                                         |
| Map[String, _]                                            | record                                   |                                                                                                         |
| org.apache.avro.generic.GenericRecord                     | record                                   |                                                                                                         |
| org.apache.avro.generic.GenericData.EnumSymbol            | enums                                    |                                                                                                         |
| String                                                    | enums                                    | On the Designer we allow to pass `Typed[String]`, but we can't verify is value proper Enum's symbol.    |
| org.apache.avro.generic.GenericData.Fixed                 | fixed                                    |                                                                                                         |
| ByteBuffer                                                | fixed                                    | On the Designer we allow to pass `Typed[ByteBuffer]`, but we can't verify is value valid Fixed element. |
| String                                                    | fixed                                    | On the Designer we allow to pass `Typed[String]`, but we can't verify is value valid Fixed element.     |
| BigDecimal                                                | decimal (bytes or fixed)                 |                                                                                                         |                                                                                                |
| ByteBuffer                                                | decimal (bytes or fixed)                 |                                                                                                         |
| UUID                                                      | uuid (string)                            |                                                                                                         |
| String                                                    | uuid (string)                            | On the Designer we allow to pass `Typed[String]`, but we can't verify is value valid UUID.              |
| LocalDate                                                 | date (int)                               |                                                                                                         |
| Integer                                                   | date (int)                               |                                                                                                         |
| LocalTime                                                 | time - millisecond precision (int)       |                                                                                                         |
| Integer                                                   | time - millisecond precision (int)       |                                                                                                         |
| LocalTime                                                 | time - microsecond precision (long)      |                                                                                                         |
| Long                                                      | time - microsecond precision (long)      |                                                                                                         |
| Instant                                                   | timestamp - millisecond precision (long) |                                                                                                         |
| Long                                                      | timestamp - millisecond precision (long) |                                                                                                         |
| Instant                                                   | timestamp - microsecond precision (long) |                                                                                                         |
| Long                                                      | timestamp - microsecond precision (long) |                                                                                                         |
| Union or any typed from the available list of the schemas | union                                    | Read more about [validation modes](#validation-and-encoding).                                           |

If at the runtime value cannot be converted to an appropriate logic schema (e.g. "notAUUID" cannot be converted to
proper UUID), then an error will be reported.

# JSON Schema

We support [JSON Schema](https://json-schema.org/) in version: `Draft 7` without:

[//]: # (See SwaggerBasedJsonSchemaTypeDefinitionExtractor)

* Numbers with a zero fractional part (e.g. `1.0`) as a proper value on decoding (deserialization)
  for [integer schema](https://json-schema.org/understanding-json-schema/reference/numeric.html#integer)
* Nested relative schemas
* Recursive schemas
* Anchors

JSON Schema is available on [Streaming](/docs/scenarios_authoring/DataSourcesAndSinks.md)
and [RequestResponse](/docs/scenarios_authoring/RRDataSourcesAndSinks.md). To integrate with JSON on
streaming we use [Schema Registry](/docs/integration/KafkaIntegration.md#schema-registry-integration), on the
other hand, we have RR where [schemas](/docs/scenarios_authoring/RRDataSourcesAndSinks.md) are stored in scenario
properties.

### Source conversion mapping

| JSON Schema                                                                                        | Java type  | Comment                                                                                                                                                            |
|----------------------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [null](https://json-schema.org/understanding-json-schema/reference/null.html)                      | null       |                                                                                                                                                                    |
| [string](https://json-schema.org/understanding-json-schema/reference/string.html)                  | String     | UTF-8                                                                                                                                                              |
| [boolean](https://json-schema.org/understanding-json-schema/reference/boolean.html)                | Boolean    |                                                                                                                                                                    |
| [integer](https://json-schema.org/understanding-json-schema/reference/numeric.html#integer)        | Long       | 64bit, TODO: Support for [range](https://json-schema.org/understanding-json-schema/reference/numeric.html#range) and do proper conversion to: Int/Long/BigInteger. |
| [number](https://json-schema.org/understanding-json-schema/reference/numeric.html#number)          | BigDecimal | TODO: Support for [range](https://json-schema.org/understanding-json-schema/reference/numeric.html#range) and do proper conversion to: Float/Double/BigDecimal.    |
| [enum](https://json-schema.org/understanding-json-schema/reference/generic.html#enumerated-values) | String     |                                                                                                                                                                    |
| [array](https://json-schema.org/understanding-json-schema/reference/array.html)                    | List[_]    |                                                                                                                                                                    |

#### [String Format](https://json-schema.org/understanding-json-schema/reference/string.html#format)

| JSON Schema | Java type     | Sample                    | Comment                |
|-------------|---------------|---------------------------|------------------------|
| date-time   | ZonedDateTime | 2021-05-17T07:34:00+01:00 |                        |
| date        | LocalDate     | 2021-05-17                | Timezone is not stored |
| time        | LocalTime     | 07:34:00.12345            | Timezone is not stored |

#### [Objects](https://json-schema.org/understanding-json-schema/reference/object.html)

| object configuration                                                      | Java type            | Comment                                             |
|---------------------------------------------------------------------------|----------------------|-----------------------------------------------------|
| object with properties                                                    | Map[String, _]       |                                                     |
| object with properties and enabled `additionalProperties`                 | Map[String, _]       | Additional properties are available at the runtime. |
| object without properties and `additionalProperties: true`                | Map[String, Unknown] | It's similar to avro map.                           |
| object without properties and `additionalProperties: {"type": "integer"}` | Map[String, Integer] | It's similar to avro map.                           |

We support `additionalProperties`, but additional fields won't be available during the hinting on the Designer. To get
an additional field you have to do `#inpute.get("additional-field")`, but remember that result of this expression is
depends on `additionalProperties` type configuration and can be `Unknown`.

#### [Schema Composition](https://json-schema.org/understanding-json-schema/reference/combining.html)

| type  | Java type                                  | Comment                       |
|-------|--------------------------------------------|-------------------------------|
| oneOf | Any from the available list of the schemas | We treat it just like a union |
| anyOf | Any from the available list of the schemas | We treat it just like a union |

### Sink validation & encoding

| Java type                                                 | JSON Schema                                                                                                    | Comment                                                                                              |
|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| null                                                      | [null](https://json-schema.org/understanding-json-schema/reference/null.html)                                  |                                                                                                      |
| String                                                    | [string](https://json-schema.org/understanding-json-schema/reference/string.html)                              |                                                                                                      |
| Boolean                                                   | [boolean](https://json-schema.org/understanding-json-schema/reference/boolean.html)                            |                                                                                                      |
| Integer                                                   | [integer](https://json-schema.org/understanding-json-schema/reference/numeric.html#integer)                    |                                                                                                      |
| Long                                                      | [integer](https://json-schema.org/understanding-json-schema/reference/numeric.html#integer)                    |                                                                                                      |
| Float                                                     | [number](https://json-schema.org/understanding-json-schema/reference/numeric.html#number)                      |                                                                                                      |
| Double                                                    | [number](https://json-schema.org/understanding-json-schema/reference/numeric.html#number)                      |                                                                                                      |
| BigDecimal                                                | [number](https://json-schema.org/understanding-json-schema/reference/numeric.html#number)                      |                                                                                                      |
| List[_]                                                   | [array](https://json-schema.org/understanding-json-schema/reference/array.html)                                |                                                                                                      |
| Map[String, _]                                            | [object](https://json-schema.org/understanding-json-schema/reference/object.html)                              |                                                                                                      |
| String                                                    | [enum](https://json-schema.org/understanding-json-schema/reference/generic.html#enumerated-values)             | On the Designer we allow to pass `Typed[String]`, but we can't verify is value proper Enum's symbol. |
| Union or any typed from the available list of the schemas | [schema composition](https://json-schema.org/understanding-json-schema/reference/combining.html): oneOf, anyOf | Read more about [validation modes](#validation-and-encoding).                                        |

#### Not supported properties on the Designer

| JSON Schema                                                                               | Properties                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Comment                                                       |
|-------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| [string](https://json-schema.org/understanding-json-schema/reference/string.html)         | [length](https://json-schema.org/understanding-json-schema/reference/string.html#length), [regular expressions](https://json-schema.org/understanding-json-schema/reference/string.html#length), [format](https://json-schema.org/understanding-json-schema/reference/string.html#length)                                                                                                                                                                                                                                                                                                                                  |                                                               |
| [numeric](https://json-schema.org/understanding-json-schema/reference/numeric.html)       | [multiples](https://json-schema.org/understanding-json-schema/reference/numeric.html#multiples), [range](https://json-schema.org/understanding-json-schema/reference/numeric.html#multiples)                                                                                                                                                                                                                                                                                                                                                                                                                               |                                                               |
| [array](https://json-schema.org/understanding-json-schema/reference/array.html)           | [additional items](https://json-schema.org/understanding-json-schema/reference/array.html#additional-items), [tuple validation](https://json-schema.org/understanding-json-schema/reference/array.html#tuple-validation), [contains](https://json-schema.org/understanding-json-schema/reference/array.html#contains), [min/max](https://json-schema.org/understanding-json-schema/reference/array.html#mincontains-maxcontains), [length](https://json-schema.org/understanding-json-schema/reference/array.html#length), [uniqueness](https://json-schema.org/understanding-json-schema/reference/array.html#uniqueness) |                                                               |
| [object](https://json-schema.org/understanding-json-schema/reference/object.html)         | [pattern properties](https://json-schema.org/understanding-json-schema/reference/object.html#pattern-properties), [unevaluatedProperties](https://json-schema.org/understanding-json-schema/reference/object.html#unevaluated-properties), [extending closed](https://json-schema.org/understanding-json-schema/reference/object.html#extending-closed-schemas), [property names](https://json-schema.org/understanding-json-schema/reference/object.html#property-names), [size](https://json-schema.org/understanding-json-schema/reference/object.html#size)                                                            |                                                               |
| [composition](https://json-schema.org/understanding-json-schema/reference/combining.html) | [allOf](https://json-schema.org/understanding-json-schema/reference/combining.html#allof), [not](https://json-schema.org/understanding-json-schema/reference/combining.html#not)                                                                                                                                                                                                                                                                                                                                                                                                                                           | Read more about [validation modes](#validation-and-encoding). |

Those properties are not supported by the Designer, but you can still use them - validation will be done at the runtime.
