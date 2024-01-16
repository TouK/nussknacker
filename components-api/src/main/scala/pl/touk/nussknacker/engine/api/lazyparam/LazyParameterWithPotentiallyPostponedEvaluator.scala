package pl.touk.nussknacker.engine.api.lazyparam

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter}

/**
  * Purpose of this trait is to hide the fact, that the real implementation of LazyParameter can has postponed
  * evaluator's creation.
  */
trait LazyParameterWithPotentiallyPostponedEvaluator[+T <: AnyRef] extends LazyParameter[T] {

  override def evaluate(context: Context): T = prepareEvaluator(EmptyParameterDeps)(context)

  def prepareEvaluator(deps: LazyParameterDeps): Context => T

}

private[api] case class ProductLazyParameter[T <: AnyRef, Y <: AnyRef](
    arg1: LazyParameterWithPotentiallyPostponedEvaluator[T],
    arg2: LazyParameterWithPotentiallyPostponedEvaluator[Y]
) extends LazyParameterWithPotentiallyPostponedEvaluator[(T, Y)] {

  override def returnType: TypingResult = Typed.genericTypeClass[(T, Y)](List(arg1.returnType, arg2.returnType))

  override def prepareEvaluator(lpi: LazyParameterDeps): Context => (T, Y) = {
    val arg1Interpreter = arg1.prepareEvaluator(lpi)
    val arg2Interpreter = arg2.prepareEvaluator(lpi)
    ctx: Context =>
      val arg1Value = arg1Interpreter(ctx)
      val arg2Value = arg2Interpreter(ctx)
      (arg1Value, arg2Value)
  }

}

private[api] case class SequenceLazyParameter[T <: AnyRef, Y <: AnyRef](
    args: List[LazyParameterWithPotentiallyPostponedEvaluator[T]],
    wrapResult: List[T] => Y,
    wrapReturnType: List[TypingResult] => TypingResult
) extends LazyParameterWithPotentiallyPostponedEvaluator[Y] {

  override def returnType: TypingResult =
    wrapReturnType(args.map(_.returnType))

  override def prepareEvaluator(lpi: LazyParameterDeps): Context => Y = {
    val argsInterpreters = args.map(_.prepareEvaluator(lpi))
    ctx: Context => wrapResult(argsInterpreters.map(_(ctx)))
  }

}

private[api] case class MappedLazyParameter[T <: AnyRef, Y <: AnyRef](
    arg: LazyParameterWithPotentiallyPostponedEvaluator[T],
    fun: T => Y,
    transformTypingResult: TypingResult => TypingResult
) extends LazyParameterWithPotentiallyPostponedEvaluator[Y] {

  override def returnType: TypingResult = transformTypingResult(arg.returnType)

  override def prepareEvaluator(lpi: LazyParameterDeps): Context => Y = {
    val argInterpreter = arg.prepareEvaluator(lpi)
    ctx: Context => fun(argInterpreter(ctx))
  }

}

private[api] case class FixedLazyParameter[T <: AnyRef](value: T, returnType: TypingResult)
    extends LazyParameterWithPotentiallyPostponedEvaluator[T] {

  override def prepareEvaluator(deps: LazyParameterDeps): Context => T =
    _ => value

}

trait LazyParameterDeps

case object EmptyParameterDeps extends LazyParameterDeps
