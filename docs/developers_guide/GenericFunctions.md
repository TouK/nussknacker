# Generic functions
 


## Creating generic functions

Let's create `get` function. We will start by writing this function in scala.
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
    
private class HeadGenericFunction extends TypingFunction {
  override def computeResultType(arguments: List[TypingResult]): 
    ValidatedNel[GenericFunctionTypingError, TypingResult] = ???
}
```
Now we need to implement `computeResultType` function that takes list of 
argument types and return type of the result or some errors. Our function
gets exactly one parameter - list of elements of type `T` and returns type `T`
otherwise we will return validation error that signals we expected other
arguments.
```scala
private val listClass = classOf[java.util.List[_]]

override def computeResultType(arguments: List[TypingResult]): 
  ValidatedNel[GenericFunctionTypingError, TypingResult] = {
  arguments match {
    case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
    case _ => ArgumentTypeError.invalidNel
  }
}
```
Now head function is ready, and we can run nussknacker and see that
`head(List[Int]) = Int` or `head(List[String]) = String`.

## Specifying parameters

Sometimes we want to use parameters that are more specific than what scala or
java can offer us. In such cases we need to manually specify types of
parameters and result. We will write custom `plus` that will work with
integers, floats and strings.



## Using varargs

## Working with typed maps

## Using static arguments

## Overloading

## Working from Java
