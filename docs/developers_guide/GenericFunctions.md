# Generic functions
 


## Creating generic functions

## Specifying parameters

## Using varargs

## Working with typed maps

## Using static arguments

## Overloading

## Working from Java

```scala
val scenario = 
  ScenarioBuilder
    .streaming("openapi-test")
    .parallelism(1)
    .source("start", "source")
    .enricher("customer", "customer", "getCustomer", ("customer_id", "#input"))
    .processorEnd("end", "invocationCollector", "value" -> "#customer")
```
