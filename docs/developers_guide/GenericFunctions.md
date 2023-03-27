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

Sometimes we want to use parameters that are more specific than what Scala or
Java can offer. In such cases we need to manually specify types of
the parameters and the result. We will write a custom `plus` that will work with
integers, floats and strings.

We will start implementing the function itself and declaring typing function
for it.
```scala
@GenericType(typingFunction = classOf[PlusGenericFunction])
def plus(left: Any, right: Any): Any = (left, right) match {
  case (left: Int, right: Int) => left + right
  case (left: Double, right: Double) => left + right
  case (left: String, right: String) => left + right
  case _ => throw new AssertionError("should not be reached")
}

private class PlusGenericFunction() extends TypingFunction {
  ???
}
```

Next we add function that calculates type of result. It will check number
of parameters and if there are exactly two then it will remove their values
and match them with supported types.
```scala
private val numberType = Typed.typedClass[Number]
private val intType = Typed.typedClass[Int]
private val doubleType = Typed.typedClass[Double]
private val stringType = Typed.typedClass[String]

override def computeResultType(arguments: List[TypingResult]): 
  ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
  case left :: right :: Nil =>
    (left.withoutValue, right.withoutValue) match {
      case (`intType`, `intType`) => 
        intType.validNel
      case (`doubleType`, `doubleType`) => 
        doubleType.validNel
      case (l, r) if List(l, r).forall(_.canBeSubclassOf(numberType)) =>
        OtherError(s"Addition of ${l.display} and ${r.display} is not supported").invalidNel
      case (`stringType`, `stringType`) => 
        stringType.validNel
      case _ => 
        ArgumentTypeError.invalidNel
    }
  case _ => ArgumentTypeError.invalidNel
}
```

This function will work as long as we provide it with correct arguments,
but when we try to use it with types int and string we get error
"expected (Object, Object), found (Int, String)", which doesn't look
like a real error. To fix it we have to specify what types this function
can accept. We will add two possible signatures `(Number, Number) -> Number`
and `(String, String) -> String`.
```scala
override def signatures: Option[NonEmptyList[MethodTypeInfo]] = 
  Some(NonEmptyList.of(
    MethodTypeInfo.withoutVarargs(
      Parameter("left", numberType) :: Parameter("right", numberType) :: Nil, 
      numberType
    ),
    MethodTypeInfo.withoutVarargs(
      Parameter("left", stringType) :: Parameter("right", stringType) :: Nil, 
      stringType
    )
))
```
Now when we try to use this function with string and int we will get much
more reasonable error saying that two numbers or two strings were expected.

## Working with objects
Now let's create a function that processes typed objects instead of simpler
types. We will make a function that takes two objects and merges them,
creating one object with fields from both arguments. Again, let's start
with implementation. Here it's worth noting that objects are represented
as maps with string keys, so our function needs to accept this type.
```scala
@GenericType(typingFunction = classOf[UnionGenericFunction])
def union(x: java.util.Map[String, Any], y: java.util.Map[String, Any]): 
  java.util.Map[String, Any] = {
  val res = new java.util.HashMap[String, Any](x)
  res.putAll(y)
  res
}

private class UnionGenericFunction extends TypingFunction {
  ???
}
```
Then we create typing function. It should check if it got two typed objects
as arguments and if it did, then it checks if they have common file and 
either returns an error or calculates their union.

```scala
override def computeResultType(arguments: List[TypingResult]): 
  ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
  case TypedObjectTypingResult(x, _, infoX) :: TypedObjectTypingResult(y, _, infoY) :: Nil =>
    if (x.keys.exists(y.keys.toSet.contains))
      OtherError("Argument maps have common field").invalidNel
    else
      TypedObjectTypingResult(x ++ y, Typed.typedClass[java.util.HashMap[_, _]], infoX ++ infoY).validNel
  case _ =>
    ArgumentTypeError.invalidNel
}
```

## Using static arguments

Let's continue working with typed objects. Now we will create function
that takes an object, name of a field and returns values of this field.

Here we make use of types that hold their value. We need to know the value
of second argument during compilation so that we can calculate type of
result, therefore we match second argument with `TypedObjectWithValue`
and return error when it is a simple string.

```scala
@GenericType(typingFunction = classOf[GetFieldGenericFunction])
def getField(obj: java.util.Map[String, Any], name: String): Any =
  obj.get(name)

private class GetFieldGenericFunction extends TypingFunction {
  private val stringType = Typed.typedClass[String]

  override def computeResultType(arguments: List[TypingResult]): 
    ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
    case TypedObjectTypingResult(fields, _, _) :: TypedObjectWithValue(`stringType`, name: String) :: Nil =>
      fields.get(name) match {
        case Some(v) => v.validNel
        case None => OtherError("No field with given name").invalidNel
      }
    case TypedObjectTypingResult(_, _, _) :: x :: Nil if x.canBeSubclassOf(stringType) =>
      OtherError("Expected string with known value").invalidNel
    case _ =>
      ArgumentTypeError.invalidNel
  }
}
```

## Using varargs
Function with varargs can be used just like regular functions. While in many
cases they do not need typing function to work properly, it sometimes can
still be useful. Let's create a function that takes multiple varargs and
returns list of all of them.

```scala
@GenericType(typingFunction = classOf[ToListGenericFunction])
@varargs
def toList[T](elems: T*): java.util.List[T] =
  elems.asJava

private class ToListGenericFunction extends TypingFunction {
  override def computeResultType(arguments: List[TypingResult]): 
    ValidatedNel[GenericFunctionTypingError, TypingResult] = {
    val supertypeFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.AnySuperclass, true)
    val commonSupertype = arguments
      .reduceOption(supertypeFinder.commonSupertype(_, _)(NumberTypesPromotionStrategy.ToSupertype))
      .getOrElse(Unknown)
    Typed.genericTypeClass[java.util.List[_]](commonSupertype :: Nil).validNel
  }
}
```

Here we use `supertypeFinder` to get best type for elements of result list.
We specify supertype class resolution strategy `AnySuperclass` and 
number types promotion strategy `ToSupertype` as they are the closest to
expected behaviour of types in list. In other circumstances we could freely
use any other strategy.
