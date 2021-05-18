# Expressions and types

Expressions used in Nussknacker are primarily written using SpEL (Spring Expression language) - simple, yet powerful expression language. 
SpEL is based on Java ([reference documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions)), but 
no prior Java knowledge is needed to use it. 

The easiest way to learn SpEL is looking at examples which are further down this page. Some attention should be paid to data types, described in more detail 
in next section, as they are used to describe data from different sources and can provide help in writing and validating expressions.

## Data types and structures

The data types are used primarily for:                            
 - validation - e.g. detecting using number field instead of string one, or checking if field used in expression exists at all.
 - code completion - suggestions appearing in UI when editing expressions.
                         
Types of input events or data returned by enrichers are often discovered from e.g. schema registry, 
 schema of SQL table or description of REST API. Nussknacker can also infer types of variables defined by user.

The types used in execution engine and in SpEL expressions and data structures are Java based. 
These are also the type names that appear in code completion. 
In most cases Nussknacker can automatically convert between Java data and JSON and AVRO formats. JSON will be used e.g. for REST API enrichers, while AVRO should be first choice
for format of Kafka messages.

Below you can find list of the most common data types, with their JSON and Avro counterparts. 
In Java types column package names are omitted for brevity, 
they are usually `java.lang` (primitives), `java.util` (List, Map) and `java.time`

### Basic (primitive data types)

| Java type  | JSON    | Avro    | Comment     |
| ---------- | ------- | ------- | ----------- |
| null       | null    | null    |             |
| String     | string  | string  | UTF-8       |
| Integer    | number  | int     | 32bit       |
| Long       | number  | long    | 64bit       |
| Float      | number  | float   | single precision |
| Double     | number  | double  | double precision |
| BigDecimal | number  | double  | enable computation without rounding errors |
| Boolean    | boolean | boolean |             |

### Records/objects
         
There are common traits for following types of data:
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

They represent JSON/Avro arrays. 
In Nussknacker (e.g. in code completion) they are described as `List`, 
also in some context `Collection` can be met (it's Java API for handling lists, sets etc.).
Please note that in many cases (e.g. Avro schema discovery or literals) Nussknacker knows type of elements, 
which can be used for validation or in code completion. 

### Handling date/time.

Formats of date/time are pretty complex - especially in Java. There are basically three ways of storing date:
- as timestamp - absolute value, number of milliseconds since 01-01-1970T00:00:00 UTC. In Nussknacker this is 
  usually seen as `Long` or `Instant`. This format is handy for storing/sending values, a bit problematic when
  it comes to computations like adding a month or extracting date. 
- as date/time without timezone information (this is usually handy if your system is in one timezone).
  Converting to timestamp is done using Nussknacker server timezone.
  In Nussknacker they are usually represented as `LocalDate` and `LocalDateTime`. Suitable for date computations like adding a month or extracting date. 
- as date/time with stored timezone. In Nussknacker usually seen as `ZonedDateTime` or `ZonedTime`. Suitable for date computations like adding a month or extracting date. 
                                           
Conversions of different types of dates are handled either by 
- `#DATE` helper has methods for parsing and conversion
- methods on some types of objects, e.g.  
  - `#instantObj.toEpochMilli` returns timestamp for `#instantObj` of type `Instant`
  - `#localDate.atStartOfDay()` - returns `LocalDateTime` at midnight for `#localDate` of type `LocalDate`
  - `#localDateTime.toLocalDate` - truncates to date for `#localDateTime` of type `LocalDateTime` 
    
The following table mapping of types, possible JSON representation (no standard here though) and 
mapping of AVRO types (`int + date` means `int` type with `date` logical type):

| Java type     | JSON    | Avro                      | Sample                  | Comment     |
| ----------    | ------- | ------------------------- | ----------------------- | ----------- |
| LocalDate     | string  | int + date                | 2021-05-17              | Timezone is not stored            |
| LocalDateTime | string  | -                         | 2021-05-17T07:34:00     | Timezone is not stored            |
| ZonedDate     | string  | -                         | 2021-05-17+02[Europe/Warsaw]           |             |
| ZonedDateTime | string  | -                         | 2021-05-17T07:34:00+02[Europe/Warsaw] |             |
| Long          | number  | long, long + local-timestamp-millis, long + local-timestamp-micros                       |                         | Raw timestamp (millis since 01.01.1970.) |
| Instant       | number  | long + timestamp-millis, long + timestamp-micros)   |                         | Raw timestamp (millis since 01.01.1970.) |
      
# SpEL syntax

## Literals
                                  
Most of the literals are similar to JSON ones, in fact in many case JSON structure is valid SpEL. 
There are a few notable exceptions:
- Lists are written using curly braces: `{"firstElement", "secondElement"}`, as `[]` is used to access elements in array 
- Empty record is `{:}`, to distinguish it from empty list: `{}`
- Strings can be quoted with either `'` or `"`
- Field names in records do not to be quoted (e.g. `{name: "John"}` is valid SpEL, but not valid JSON)

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`'Hello World'`| "Hello World" |	String |
|`true`	      |true	   | Boolean |
|`null`	      | null   | Null    |
|`{1,2,3,4}`    |  a list of integers from 1 to 4	| List[Integer] |
|`{john:300, alex:400}` |a map (key-value collection) | Map[String, Integer] |
| `#input` | variable | |                                         
                                    
## Arithmetic Operators

| Expression  |	Result | Type   |
| ------------|--------|--------|
| `42 + 2`      | 44     | Integer|
| `'AA' + 'BB'` | "AABB" | String |

## Conditional Operators

| Expression  |	Result | Type   |
| ------------|--------|--------|
| `2 > 1 ? 'a' : 'b'` |	"a"	| String |
| `2 < 1 ? 'a' : 'b'` |	"b"	String |
| `#nonNullVar == null ? 'Success'` : 'Unknown' |	"Success" | String |
| `#nullVar == null ? 'Success'` : 'Unknown' | "Unknown" | String |
| `#nullVar?:'Unknown` | "Unknown" | String |
| `'john'?:'Unknown'` | "john" | String |

## Method invocations

As Nussknacker uses Java types, some objects are more than data containers - there are additional methods 
that can be invoked on them. Method parameters are passed in parentheses, usually parameter details 
are shown in code completion on FE.

| Expression                 | Result     | Type |
| -------------------------- | ---------- | ---- |
| `'someValue.substring(4)`  | "Value"  | String |
| `'someValue'.length()`     | 9        | Integer|

## Accessing elements of a list or a record

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1,2,3,4}[0]` | 1 | Integer |
|`{jan:300, alex:400}[alex]` | a value of key 'alex', which is 400	| Integer |

## Filtering lists
                          
Special variable `#this` is used to operate on single element of list.

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1,2,3,4}.?[#this ge 3]` |{3, 4}|	List[Integer] |
|`#usersList.?[#this.firstName == 'john']`	| {'john doe'}	| List[String] |

## Mapping lists

Special variable `#this` is used to operate on single element of list.
            
Examples below assume following structure:
```Person: {name: String, age: Integer }
listOfPersons: List[Person]
person1 = name: "Alex"; age: 42
person2 = name: "John"; age: 24
listOfPersons = {person1, person2}
```

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1,2,3,4}.![#this * 2]` | {2, 4, 6, 8} | List[Integer] |
|`#listOfPersons.![#this.name]` | {'Alex', 'John'} | List[String] |
|`#listOfPersons.![#this.age]` | {42, 24} | List[Integer] |
|`#listOfPersons.![7]` | {7, 7}	| List[Integer] |

## Safe navigation

When you access nested structure, you have to take care of null fields, otherwise you'll end up with 
error. SpEL provides helpful safe navigation operator, it's basically shorthand for conditional operator:
`#someVar?.b` means `#someVar != null ? #someVar.b : null`

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`null.someField`	| java.lang.NullPointerException | java.lang.NullPointerException
|`null?.someField`	| null	| Null

## Invoking static methods

It is possible to invoke Java static methods directly with SpEL. Nussknacker can prevent invocations
of some of them due to security reasons. Invoking static methods is advanced functionality, which can lead
to incomprehensible expressions, also code completions will not work with many of them. 
If you need to invoke the same method in many places, probably the best solution is to create additional helper.

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`T(java.lang.Math).PI`	| 3.14159..	| Double |

## Chaining with dot
| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1, 2, 3, 4}.?[#this > 1].![#this > 2 ? #this * 2 : #this]` | {2, 6, 8} | Double |

## Type conversions

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`#NUMERIC.toNumber('42')`	| 42 | Number
|`#NUMERIC.toNumber('42').toString()`	| '42'	| String |
|`'' + 42`	| '42' | String
|`#DATE.parseToTimestamp('2018-10-23T12:12:13+00:00')`	| 1540296720000	| Long
|`#DATE.parseToLocalDate('2018-10-23T12:12:13+00:00')`| 2018-10-23T12:12:13+00:00	| LocalDateTime


## Built-in helpers 

| Helper | Functions |
| -------|-----------|
| `GEO`  | Simple distance measurements | 
| `NUMERIC`| Number parsing |
| `CONV` | General conversion functions |
| `DATE` | Date operations (parsing, printing) | 
| `UTIL` | Various utilities (e.g. identifier generation) |
