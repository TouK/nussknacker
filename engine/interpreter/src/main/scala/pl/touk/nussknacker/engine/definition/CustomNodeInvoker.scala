package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

private[definition] case class CompilerLazyParameter[T](nodeId: NodeId,
                                                        parameter: evaluatedparam.Parameter,
                                                        returnType: TypingResult) extends LazyParameter[T] {


}

trait CompilerLazyParameterInterpreter extends LazyParameterInterpreter {

  def deps: LazyInterpreterDependencies

  def metaData: MetaData

  override def createInterpreter[T](lazyInterpreter: LazyParameter[T]): (ExecutionContext, Context) => Future[T]
    = (ec: ExecutionContext, context: Context) => createInterpreter(ec, lazyInterpreter)(context)

  private[definition] def createInterpreter[T](ec: ExecutionContext, definition: LazyParameter[T]): Context => Future[T] = {

    val castedDefinition = definition.asInstanceOf[CompilerLazyParameter[T]]

    val compiledExpression = deps.expressionCompiler
      .compile(castedDefinition.parameter.expression, Some(castedDefinition.parameter.name), None, Unknown)(castedDefinition.nodeId)
      .getOrElse(throw new IllegalArgumentException(s"Cannot compile ${castedDefinition.parameter.name}")).expression

    val evaluator = deps.expressionEvaluator

    context: Context => evaluator.evaluate[T](compiledExpression, castedDefinition.parameter.name,
      castedDefinition.nodeId.id, context)(ec, metaData).map(_.value)(ec)
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

  override protected def extractParameterType(p: java.lang.reflect.Parameter): Class[_] =
    EspTypeUtils.extractParameterType(p, classOf[LazyParameter[_]])


}



