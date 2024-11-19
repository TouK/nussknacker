package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter.EvaluableExpressionPart
import pl.touk.nussknacker.engine.api.LazyParameter.{Evaluate, MappedLazyParameter, ProductLazyParameter}
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
// TODO: rename to TypedFunction
trait LazyParameter[+T <: AnyRef] extends Serializable {

  def evaluate: Evaluate[T]

  // type of parameter, derived from expression. Can be used for dependent types, see PreviousValueTransformer
  def returnType: TypingResult

  // we provide only applicative operation, monad is tricky to implement (see CompilerLazyParameterInterpreter.createInterpreter)
  // we use product and not ap here, because it's more convenient to handle returnType computations
  def product[B <: AnyRef](fb: LazyParameter[B]): LazyParameter[(T, B)] =
    new ProductLazyParameter(this, fb)

  def map[Y <: AnyRef: TypeTag](fun: T => Y): LazyParameter[Y] =
    map(fun, _ => Typed.fromDetailedType[Y])

  // unfortunately, we cannot assert that TypingResult represents Y somehow...
  def map[Y <: AnyRef](fun: T => Y, transformTypingResult: TypingResult => TypingResult): LazyParameter[Y] =
    new MappedLazyParameter[T, Y](this, fun, transformTypingResult)

}

object LazyParameter {

  type Evaluate[+T] = Context => T

  // Sequence requires wrapping of evaluation result and result type because we don't want to use heterogeneous lists
  def sequence[T <: AnyRef, Y <: AnyRef](
      fa: List[LazyParameter[T]],
      wrapResult: List[T] => Y,
      wrapReturnType: List[TypingResult] => TypingResult
  ): LazyParameter[Y] =
    new SequenceLazyParameter(fa, wrapResult, wrapReturnType)

  // Name must be other then pure because scala can't recognize which overloaded method was used
  def pureFromDetailedType[T <: AnyRef: TypeTag](value: T): LazyParameter[T] =
    new FixedLazyParameter(value, Typed.fromDetailedType[T])

  def pure[T <: AnyRef](value: T, valueTypingResult: TypingResult): LazyParameter[T] =
    new FixedLazyParameter(value, valueTypingResult)

  def product[T <: AnyRef, Y <: AnyRef](arg1: LazyParameter[T], arg2: LazyParameter[Y]): LazyParameter[(T, Y)] =
    new ProductLazyParameter(arg1, arg2)

  def mapped[T <: AnyRef, Y <: AnyRef](
      lazyParameter: LazyParameter[T],
      fun: T => Y,
      transformTypingResult: TypingResult => TypingResult
  ): LazyParameter[Y] =
    new MappedLazyParameter[T, Y](lazyParameter, fun, transformTypingResult)

  trait CustomLazyParameter[+T <: AnyRef] extends LazyParameter[T]

  trait TemplateLazyParameter[T <: AnyRef] extends LazyParameter[T] {
    def parts: List[EvaluableExpressionPart]
  }

  object TemplateLazyParameter {
    sealed trait EvaluableExpressionPart

    object EvaluableExpressionPart {
      case class Literal(value: String) extends EvaluableExpressionPart

      trait Placeholder extends EvaluableExpressionPart {
        val evaluate: Evaluate[String]
      }

    }

  }

  final class ProductLazyParameter[T <: AnyRef, Y <: AnyRef](
      val arg1: LazyParameter[T],
      val arg2: LazyParameter[Y]
  ) extends LazyParameter[(T, Y)] {

    override val returnType: TypingResult = Typed.genericTypeClass[(T, Y)](List(arg1.returnType, arg2.returnType))

    override val evaluate: Evaluate[(T, Y)] = {
      val arg1Evaluator = arg1.evaluate
      val arg2Evaluator = arg2.evaluate
      ctx: Context => (arg1Evaluator(ctx), arg2Evaluator(ctx))
    }

  }

  final class SequenceLazyParameter[T <: AnyRef, Y <: AnyRef](
      val args: List[LazyParameter[T]],
      val wrapResult: List[T] => Y,
      val wrapReturnType: List[TypingResult] => TypingResult
  ) extends LazyParameter[Y] {

    override val returnType: TypingResult =
      wrapReturnType(args.map(_.returnType))

    override val evaluate: Evaluate[Y] = {
      val argsEvaluators = args.map(_.evaluate)
      ctx: Context => wrapResult(argsEvaluators.map(_.apply(ctx)))
    }

  }

  final class MappedLazyParameter[T <: AnyRef, Y <: AnyRef](
      val arg: LazyParameter[T],
      val fun: T => Y,
      val transformTypingResult: TypingResult => TypingResult
  ) extends LazyParameter[Y] {

    override val returnType: TypingResult = transformTypingResult(arg.returnType)

    override val evaluate: Evaluate[Y] = {
      val argEvaluator = arg.evaluate
      ctx: Context => fun(argEvaluator.apply(ctx))
    }

  }

  final class FixedLazyParameter[T <: AnyRef](value: T, override val returnType: TypingResult)
      extends LazyParameter[T] {

    override val evaluate: Evaluate[T] = _ => value
  }

}

// This class is Flink-specific. It allows to evaluate value of lazy parameter in case when LazyParameter isn't
// a ready to evaluation function. In Flink case, LazyParameters are passed into Flink's operators so they
// need to be Serializable. Because of that they can't hold heavy objects like ExpressionCompiler or ExpressionEvaluator
trait ToEvaluateFunctionConverter {

  def toEvaluateFunction[T <: AnyRef](parameter: LazyParameter[T]): Evaluate[T]

}
