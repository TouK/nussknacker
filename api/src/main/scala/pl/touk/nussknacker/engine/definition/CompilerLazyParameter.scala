package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter}

import scala.concurrent.{ExecutionContext, Future}

// TODO: change filename and package
case class ProductLazyParameter[T <: AnyRef, Y <: AnyRef](arg1: LazyParameter[T], arg2: LazyParameter[Y]) extends LazyParameter[(T, Y)] {

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

case class SequenceLazyParameter[T <: AnyRef, Y <: AnyRef](args: Seq[LazyParameter[T]],
                                                           wrapResult: Seq[T] => Y,
                                                           wrapReturnType: List[TypingResult] => TypingResult) extends LazyParameter[Y] {

  override def returnType: TypingResult =
    wrapReturnType(args.toList.map(_.returnType))

  override def prepareEvaluator(lpi: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Y] = {
    val argsInterpreters = args.map(_.prepareEvaluator(lpi))
    ctx: Context =>
      argsInterpreters.map(_(ctx)).foldLeft(Future.successful(List.empty[T])) { (acc, future) =>
        acc.flatMap(m => future.map(v => v :: m))
      }.map(_.reverse).map(wrapResult)
  }

}

case class MappedLazyParameter[T <: AnyRef, Y <: AnyRef](arg: LazyParameter[T], fun: T => Y, transformTypingResult: TypingResult => TypingResult) extends LazyParameter[Y] {

  override def returnType: TypingResult = transformTypingResult(arg.returnType)

  override def prepareEvaluator(lpi: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Y] = {
    val argInterpreter = arg.prepareEvaluator(lpi)
    ctx: Context => argInterpreter(ctx).map(fun)
  }
}

// This class is public for tests purpose. Be aware that its interface can be changed in the future
case class FixedLazyParameter[T <: AnyRef](value: T, returnType: TypingResult) extends LazyParameter[T] {

  override def prepareEvaluator(deps: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T] = _ => Future.successful(value)

}
