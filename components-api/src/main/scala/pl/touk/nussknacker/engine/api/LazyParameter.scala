package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.lazyparam.{EvaluableLazyParameter, FixedLazyParameter, MappedLazyParameter, ProductLazyParameter, SequenceLazyParameter}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import scala.reflect.runtime.universe.TypeTag

/**
  * Lazy parameter is representation of parameter of custom node which should be evaluated for each record:
  * ```def execute(@ParamName("groupBy") groupBy: LazyParameter[String], @ParamName ("length") length: String)```
  * In this case, length is computed as constant during process compilation, while groupBy is evaluated for each event
  * Cannot be evaluated directly (no method like 'evaluate'),
  * as evaluation may need lifecycle handling, to use it see LazyParameterInterpreter
  *
  * @tparam T type of evaluated parameter. It has upper bound AnyRef because currently we don't support correctly extraction of
  *          primitive types from generic parameters
  */
trait LazyParameter[+T <: AnyRef] {

  //type of parameter, derived from expression. Can be used for dependent types, see PreviousValueTransformer
  def returnType: TypingResult

  //we provide only applicative operation, monad is tricky to implement (see CompilerLazyParameterInterpreter.createInterpreter)
  //we use product and not ap here, because it's more convenient to handle returnType computations
  def product[B <: AnyRef](fb: LazyParameter[B]): LazyParameter[(T, B)] = {
    ProductLazyParameter(this.asInstanceOf[EvaluableLazyParameter[T]], fb.asInstanceOf[EvaluableLazyParameter[B]])
  }

  def map[Y <: AnyRef :TypeTag](fun: T => Y): LazyParameter[Y] =
    map(fun, _ => Typed.fromDetailedType[Y])

  // unfortunately, we cannot assert that TypingResult represents Y somehow...
  def map[Y <: AnyRef](fun: T => Y, transformTypingResult: TypingResult => TypingResult): LazyParameter[Y] =
    new MappedLazyParameter[T, Y](this.asInstanceOf[EvaluableLazyParameter[T]], fun, transformTypingResult)

}

object LazyParameter {

  // Sequence requires wrapping of evaluation result and result type because we don't want to use heterogeneous lists
  def sequence[T <: AnyRef, Y <: AnyRef](fa: List[LazyParameter[T]], wrapResult: List[T] => Y, wrapReturnType: List[TypingResult] => TypingResult): LazyParameter[Y] =
    SequenceLazyParameter(fa.map(_.asInstanceOf[EvaluableLazyParameter[T]]), wrapResult, wrapReturnType)

  // Name must be other then pure because scala can't recognize which overloaded method was used
  def pureFromDetailedType[T <: AnyRef : TypeTag](value: T): LazyParameter[T] =
    FixedLazyParameter(value, Typed.fromDetailedType[T])

  def pure[T <: AnyRef](value: T, valueTypingResult: TypingResult): LazyParameter[T] =
    FixedLazyParameter(value, valueTypingResult)

}

trait LazyParameterInterpreter {

  def syncInterpretationFunction[T <: AnyRef](parameter: LazyParameter[T]) : Context => T

  def close(): Unit

}
