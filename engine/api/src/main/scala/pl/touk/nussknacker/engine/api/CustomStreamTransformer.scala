package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe.TypeTag

/**
  * Hook for using Apache Flink API directly.
  * See examples in pl.touk.nussknacker.engine.example.custom
  *
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.)
**/
//TODO this could be scala-trait, but we leave it as abstract class for now for java compatibility
//We should consider separate interfaces for java implementation, but right now we convert ProcessConfigCreator
//from java to scala one and is seems difficult to convert java CustomStreamTransformer, Service etc. into scala ones
abstract class CustomStreamTransformer {

  /**
    * deprecated - use ContextTransformation.definedBy(Valid(_.clearVariables)) instead
    */
  // TODO: remove after full switch to ContextTransformation API
  def clearsContext = false

  /**
    * deprecated - use ContextTransformation.join instead
    */
  // TODO: remove after full switch to ContextTransformation API
  def canHaveManyInputs: Boolean = false

  def canBeEnding: Boolean = false 
  
}

/**
  * Lazy parameter is representation of parameter of custom node which should be evaluated for each record:
  * ```def execute(@ParamName("keyBy") keyBy: LazyParameter[String], @ParamName ("length") length: String)```
  * In this case, length is computed as constant during process compilation, while keyBy is evaluated for each event
  * Cannot be evaluated directly (no method like 'evaluate',
  * as evaluation may need e.g. lazy variables and we have to take care of lifecycle, to use it see LazyParameterInterpreter
  *
  */
trait LazyParameter[+T] {

  //type of parameter, derived from expression. Can be used for dependent types, see PreviousValueTransformer
  def returnType: TypingResult

  //we provide only applicative operation, monad is tricky to implement (see CompilerLazyParameterInterpreter.createInterpreter)
  //we use product and not ap here, because it's more convenient to handle returnType computations
  def product[B](fb: LazyParameter[B])(implicit lazyParameterInterpreter: LazyParameterInterpreter): LazyParameter[(T, B)] = {
    lazyParameterInterpreter.product(this, fb)
  }

  //unfortunatelly, we cannot assert that TypingResult represents A somehow...
  def pure[A](value: A, valueTypingResult: TypingResult)(implicit lazyParameterInterpreter: LazyParameterInterpreter): LazyParameter[A]
    = lazyParameterInterpreter.pure(value, valueTypingResult)

  def pure[A:TypeTag](value: A)(implicit lazyParameterInterpreter: LazyParameterInterpreter): LazyParameter[A]
    = pure(value, Typed.fromDetailedType[A])

  def map[Y:TypeTag](fun: T => Y)(implicit lazyParameterInterpreter: LazyParameterInterpreter): LazyParameter[Y] =
    map(fun, Typed.fromDetailedType[Y])

  //unfortunatelly, we cannot assert that TypingResult represents Y somehow...
  def map[Y](fun: T => Y, outputTypingResult: TypingResult)(implicit lazyParameterInterpreter: LazyParameterInterpreter): LazyParameter[Y] =
    lazyParameterInterpreter.map(this, fun, outputTypingResult)

}



trait LazyParameterInterpreter {

  def createInterpreter[T](parameter: LazyParameter[T]): (ExecutionContext, Context) => Future[T]

  def product[A, B](fa: LazyParameter[A], fb: LazyParameter[B]): LazyParameter[(A, B)]

  def pure[T](value: T, valueTypingResult: TypingResult): LazyParameter[T]

  def map[T, Y](parameter: LazyParameter[T], fun: T => Y, outputTypingResult: TypingResult): LazyParameter[Y]

  def syncInterpretationFunction[T](parameter: LazyParameter[T]) : Context => T

  def close(): Unit

}

