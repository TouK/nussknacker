package pl.touk.nussknacker.engine.api.lazyparam

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Purpose of this trait is to hide evaluation details from LazyParameter api to make sure that only
  * interpreter manage how to evaluate them. It causes down casting in a few places but it is very isolated
  * and hidden from public developer of extensions api
  *
  * Ideally it should be visible only by interpreters but to not extract additional modules it is accessible from api module.
  */
trait EvaluableLazyParameter[+T <: AnyRef] extends LazyParameter[T] {

  //TODO: get rid of Future[_] as we evaluate parameters synchronously...
  def prepareEvaluator(deps: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T]

}

private[api] case class ProductLazyParameter[T <: AnyRef, Y <: AnyRef](arg1: EvaluableLazyParameter[T],
                                                                       arg2: EvaluableLazyParameter[Y])
  extends EvaluableLazyParameter[(T, Y)] {

  override def returnType: TypingResult = Typed.genericTypeClass[(T, Y)](List(arg1.returnType, arg2.returnType))

  override def prepareEvaluator(lpi: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[(T, Y)] = {
    val arg1Interpreter = arg1.prepareEvaluator(lpi)
    val arg2Interpreter = arg2.prepareEvaluator(lpi)
    ctx: Context =>
      val arg1Value = arg1Interpreter(ctx)
      val arg2Value = arg2Interpreter(ctx)
      arg1Value.flatMap(left => arg2Value.map((left, _)))
  }
}

private[api] case class SequenceLazyParameter[T <: AnyRef, Y <: AnyRef](args: List[EvaluableLazyParameter[T]],
                                                                        wrapResult: List[T] => Y,
                                                                        wrapReturnType: List[TypingResult] => TypingResult) extends EvaluableLazyParameter[Y] {

  override def returnType: TypingResult =
    wrapReturnType(args.map(_.returnType))

  override def prepareEvaluator(lpi: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Y] = {
    val argsInterpreters = args.map(_.prepareEvaluator(lpi))
    ctx: Context =>
      Future.sequence(argsInterpreters.map(_(ctx))).map(wrapResult)
  }

}

private[api] case class MappedLazyParameter[T <: AnyRef, Y <: AnyRef](arg: EvaluableLazyParameter[T],
                                                                      fun: T => Y,
                                                                      transformTypingResult: TypingResult => TypingResult)
  extends EvaluableLazyParameter[Y] {

  override def returnType: TypingResult = transformTypingResult(arg.returnType)

  override def prepareEvaluator(lpi: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Y] = {
    val argInterpreter = arg.prepareEvaluator(lpi)
    ctx: Context => argInterpreter(ctx).map(fun)
  }
}

private[api] case class FixedLazyParameter[T <: AnyRef](value: T, returnType: TypingResult) extends EvaluableLazyParameter[T] {

  override def prepareEvaluator(deps: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T] = _ => Future.successful(value)

}
