# Expressions and types

Expressions in Nussknacker are written using language called SpEL (there is also possibility of extending Nussknacker with different expression languages).
It is based on Java ([reference documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions)), but 
Java knowledge is not needed to use it. 
                                 
Nussknacker uses type system for:
- validation 
- suggestions

Types of input events or data returned by enrichers are often discovered from e.g. schema registry, 
schema of SQL table or description of REST API. Nussknacker can also infer types of variables defined by user.
    
## Data types and structures

Below you can find list of most common data types. When Nussknacker is running scenario, the types used 
in execution engine are Java based. These are also type names that appear in expression suggestions. 
                                                                                                     
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
                     
In many cases Nussknacker can convert them automatically.
For the user, the most significant difference is (using Avro terminology) 
between `record` and `map`. Both can describe following structure:

input = `{ name: 'John', surname: 'Doe'}`                            

The main difference is that in case of `record` Nussknacker "knows" which fields (`name` and `surname`)
are available and suggest and validate fields and their types.
For example, `#input.name` is valid, while `#input.noname` or `#input.name > 0` as field name or type do not match.

On the other hand, `map` describes "generic" structure - Nussknacker tacitly assumes it can contain any field of any type.

### Arrays/lists

They represent JSON/Avro arrays. 
In Nussknacker (e.g. in suggestions) they are described as `List`, also in some context `Collection` can be met (it's Java API for handling lists, sets etc.)
Please note that in many cases (e.g. Avro schema discovery or literals) Nussknacker knows type of elements, which can be used for validation or suggestions. 

### Handling date/time.

Formats of date/time are pretty complex - especially in Java. There are basically three ways of storing date:
- as timestamp - absolute value, number of milliseconds since 01-01-1970T00:00:00 UTC. In Nussknacker this is 
  usually seen as `Long` or `Instant`. This format is handy for storing/sending values, a bit problematic when
  it comes to computations like adding a month or extracting date. 
- as date/time in systems time zone (this is usually handy if your system is in one timezone). 
  In Nussknacker usually represented as `LocalDate` and `LocalDateTime`. Suitable for date computations like adding a month or extracting date. 
- as date/time with stored timezone. In Nussknacker usually seen as `ZonedDateTime` or `ZonedTime`. Suitable for date computations like adding a month or extracting date. 
                                           
Conversions of different types of dates are handled either by 
- `#DATE` helper has methods for parsing and conversion
- methods on some typesm, e.g. 
  - `#instantObj.toEpochMilli` gives timestamp
  - `#localDate.atStartOfDay()` - gives `LocalDateTime` at midnight
  - `#localDateTime.toLocalDate` - truncates to date  

| Java type     | JSON    | Avro                      | Sample                  | Comment     |
| ----------    | ------- | ------------------------- | ----------------------- | ----------- |
| LocalDate     | string  | -                         | 2021-05-17              |             |
| LocalDateTime | string  | -                         | 2021-05-17T07:34:00     |             |
| ZonedDate     | string  | -                         | 2021-05-17+02[Europe/Warsaw]           |             |
| ZonedDateTime | string  | -                         | 2021-05-17T07:34:00+02[Europe/Warsaw] |             |
| Long          | number  | long                      |                         | Raw timestamp (millis since 01.01.1970.) |
| Instant       | number  | long + timestamp-millis   |                         | Raw timestamp (millis since 01.01.1970.) |
      
# SpEL syntax

## Literals

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`'Hello World'`| "Hello World" |	String |
|`true`	      |true	   | Boolean |
|`null`	      | null   | Null    |
|`{1,2,3,4}`    |  a list of integers from 1 to 4	| List<Integer> |
|`{john:300, alex:400}` |a map (key-value collection) | Map<String, Integer> |
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


## Accessing elements of a collection

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1,2,3,4}[0]` | 1 | Integer |
|`{jan:300, alex:400}[alex]` | a value of key 'alex', which is 400	| Integer |

## Filtering collections

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1,2,3,4}.?[#this ge 3]` |{3, 4}|	List<Integer> |
|`#usersList.?[#this.firstName == 'john']`	| {'john doe'}	| List<String> |

## Mapping collections

```Person: {name: String, age: Integer }
listOfPersons: List<Person>
person1 = name: "Alex"; age: 42
person2 = name: "John"; age: 24
listOfPersons = {person1, person2}
```

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1,2,3,4}.![#this * 2]` | {2, 4, 6, 8} | List<Integer> |
|`#listOfPersons.![#this.name]` | {'Alex', 'John'} | List<String> |
|`#listOfPersons.![#this.age]` | {42, 24} | List<Integer> |
|`#listOfPersons.![7]` | {7, 7}	| List<Integer> |

## Safe navigation

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`null.someField`	| java.lang.NullPointerException | java.lang.NullPointerException
|`null?.someField`	| null	| Null

## Invoking static methods
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
