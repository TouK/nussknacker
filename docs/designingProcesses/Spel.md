##Introduction

Expressions in Nussknacker are written using language called SpEL (there is also possibility of extending Nussknacker with different expression languages).
It is based on Java ([reference documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions)), but 
Java knowledge is not needed to use it. 

## Literals

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`'Hello World'`| "Hello World" |	String |
|`true`	      |true	   | Boolean |
|`null`	      | null   | Null    |
|`{1,2,3,4}`    |  a list of integers from 1 to 4	| List<Integer> |
|`{john:300, alex:400}` |a map (key-value collection) | Map<String, Integer> |
| `#input` | variable | |                                         
                                    
##Arithmetic Operators

| Expression  |	Result | Type   |
| ------------|--------|--------|
| `42 + 2`      | 44     | Integer|
| `'AA' + 'BB'` | "AABB" | String |

##Conditional Operators

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

##Safe navigation

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`null.someField`	| java.lang.NullPointerException | java.lang.NullPointerException
|`null?.someField`	| null	| Null

##Invoking static methods
| Expression  |	Result | Type   |
| ------------|--------|--------|
|`T(java.lang.Math).PI`	| 3.14159..	| Double |

##Chaining with dot
| Expression  |	Result | Type   |
| ------------|--------|--------|
|`{1, 2, 3, 4}.?[#this > 1].![#this > 2 ? #this * 2 : #this]` | {2, 6, 8} | Double |

##Type conversions

| Expression  |	Result | Type   |
| ------------|--------|--------|
|`#NUMERIC.toNumber('42')`	| 42 | Number
|`#NUMERIC.toNumber('42').toString()`	| '42'	| String |
|`'' + 42`	| '42' | String
|`#DATE.parseToTimestamp('2018-10-23T12:12:13+00:00')`	| 1540296720000	| Long
|`#DATE.parseToLocalDate('2018-10-23T12:12:13+00:00')`| 2018-10-23T12:12:13+00:00	| LocalDateTime


##Built-in helpers 

| Helper | Functions |
| -------|-----------|
| `GEO`  | Simple distance measurements | 
| `NUMERIC`| Number parsing |
| `CONV` | General conversion functions |
| `DATE` | Date operations (parsing, printing) | 
| `UTIL` | Various utilities (e.g. identifier generation) |
