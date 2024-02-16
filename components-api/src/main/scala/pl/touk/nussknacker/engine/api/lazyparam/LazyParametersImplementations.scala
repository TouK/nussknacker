package pl.touk.nussknacker.engine.api.lazyparam

import pl.touk.nussknacker.engine.api.LazyParameter.Evaluate
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter}

private[api] class ProductLazyParameter[T <: AnyRef, Y <: AnyRef](
    arg1: LazyParameter[T],
    arg2: LazyParameter[Y]
) extends LazyParameter[(T, Y)] {

  override val returnType: TypingResult = Typed.genericTypeClass[(T, Y)](List(arg1.returnType, arg2.returnType))

  override val evaluate: Evaluate[(T, Y)] = {
    val arg1Evaluator = arg1.evaluate
    val arg2Evaluator = arg2.evaluate
    ctx: Context => (arg1Evaluator(ctx), arg2Evaluator(ctx))
  }

}

private[api] class SequenceLazyParameter[T <: AnyRef, Y <: AnyRef](
    args: List[LazyParameter[T]],
    wrapResult: List[T] => Y,
    wrapReturnType: List[TypingResult] => TypingResult
) extends LazyParameter[Y] {

  override val returnType: TypingResult =
    wrapReturnType(args.map(_.returnType))

  override val evaluate: Evaluate[Y] = {
    val argsEvaluators = args.map(_.evaluate)
    ctx: Context => wrapResult(argsEvaluators.map(_.apply(ctx)))
  }

}

private[api] class MappedLazyParameter[T <: AnyRef, Y <: AnyRef](
    arg: LazyParameter[T],
    fun: T => Y,
    transformTypingResult: TypingResult => TypingResult
) extends LazyParameter[Y] {

  override val returnType: TypingResult = transformTypingResult(arg.returnType)

  override val evaluate: Evaluate[Y] = {
    val argEvaluator = arg.evaluate
    ctx: Context => fun(argEvaluator.apply(ctx))
  }

}

private[api] class FixedLazyParameter[T <: AnyRef](value: T, override val returnType: TypingResult)
    extends LazyParameter[T] {

  override val evaluate: Evaluate[T] = _ => value
}
