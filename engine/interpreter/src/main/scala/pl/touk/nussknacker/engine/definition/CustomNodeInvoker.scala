package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.{LazyParameter, _}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

private[definition] case class ExpressionLazyParameter[T](nodeId: NodeId,
                                                          parameter: evaluatedparam.Parameter,
                                                          returnType: TypingResult) extends LazyParameter[T] {
}

private[definition] trait ComposedLazyParameter[Y] extends LazyParameter[Y] {

  type InType

  def arg: LazyParameter[InType]

  def fun: LazyParameter[InType => Y]

  override def returnType: TypingResult = Unknown

  def compose(lpi: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Y] = {
    val argInterpreter = lpi.createInterpreter(arg)
    val funInterpreter = lpi.createInterpreter(fun)
    (ctx: Context) =>
      val argValue = argInterpreter(ec, ctx)
      val funValue = funInterpreter(ec, ctx)
      argValue.flatMap(par => funValue.map(_.apply(par)))

  }
}

private[definition] case class FixedLazyParameter[T](value: T) extends LazyParameter[T] {
  override def returnType: TypingResult = Unknown
}


trait CompilerLazyParameterInterpreter extends LazyParameterInterpreter {

  def deps: LazyInterpreterDependencies

  def metaData: MetaData

  override def createInterpreter[T](lazyInterpreter: LazyParameter[T]): (ExecutionContext, Context) => Future[T]
    = (ec: ExecutionContext, context: Context) => createInterpreter(ec, lazyInterpreter)(context)

  override def ap[T, Y](argA: LazyParameter[T], funA: LazyParameter[T => Y]): LazyParameter[Y] = new ComposedLazyParameter[Y] {

    override type InType = T

    override def arg: LazyParameter[T] = argA

    override def fun: LazyParameter[T => Y] = funA
  }

  override def unit[T](value: T): LazyParameter[T] = FixedLazyParameter(value)

  //it's important that it's (...): (Context => Future[T])
  //and not e.g. (...)(Context) => Future[T] as we want to be sure when body is evaluated (in particular expression compilation)!
  private[definition] def createInterpreter[T](ec: ExecutionContext, definition: LazyParameter[T]): Context => Future[T] = {
    definition match {
      case ExpressionLazyParameter(nodeId, parameter, _) =>
        val compiledExpression = deps.expressionCompiler
          .compile(parameter.expression, Some(parameter.name), None, Unknown)(nodeId)
          .getOrElse(throw new IllegalArgumentException(s"Cannot compile ${parameter.name}")).expression
        val evaluator = deps.expressionEvaluator
        context: Context => evaluator.evaluate[T](compiledExpression, parameter.name, nodeId.id, context)(ec, metaData).map(_.value)(ec)
      case cli:ComposedLazyParameter[T] =>
        cli.compose(this)(ec: ExecutionContext)
      case FixedLazyParameter(value) =>
        _ => Future.successful(value)
    }
  }

  override def syncInterpretationFunction[T](lazyInterpreter: LazyParameter[T]): Context => T = {

    implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
    val interpreter = createInterpreter(ec, lazyInterpreter)
    v1: Context => Await.result(interpreter(v1), deps.processTimeout)
  }


}

case class LazyInterpreterDependencies(expressionEvaluator: ExpressionEvaluator,
                                       expressionCompiler: ExpressionCompiler,
                                       processTimeout: FiniteDuration) extends Serializable

object CustomStreamTransformerExtractor extends AbstractMethodDefinitionExtractor[CustomStreamTransformer] {

  override protected val expectedReturnType: Option[Class[_]] = None

  override protected val additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[NodeId])

}



