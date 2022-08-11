# Generic functions
 


## Creating generic functions

Let's create `head` function. We will start by writing this function in scala.
```scala
def head[T](list: java.util.List[T]): T =
  list.get(0)
```
When we try to compile this we will see that it has type 
`List[Unknown] -> Unknown`. In order to have better typing information
we need to create new subclass of TypingFunction, that will calculate type of
head's result, and attach it to the function.
```scala
@GenericType(typingFunction = classOf[HeadGenericFunction])
def head[T](list: java.util.List[T]): T =
  list.get(0)
    
private case class HeadGenericFunction() extends TypingFunction {
  override def computeResultType(arguments: List[TypingResult]): 
    ValidatedNel[GenericFunctionTypingError, TypingResult] = ???
}
```
Now we need to implement `computeResultType` function that takes list of 
argument types and return type of the result or some errors.

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
