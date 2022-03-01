package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.lazyparam.EvaluableLazyParameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.collection.immutable.ListMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

// This class is public for tests purpose. Be aware that its interface can be changed in the future
case class ExpressionLazyParameter[T <: AnyRef](nodeId: NodeId,
                                                parameterDef: definition.Parameter,
                                                expression: Expression,
                                                returnType: TypingResult) extends EvaluableLazyParameter[T] {
  override def prepareEvaluator(compilerInterpreter: LazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[T] = {
    val compilerLazyInterpreter = compilerInterpreter.asInstanceOf[CompilerLazyParameterInterpreter]
    val compiledExpression = compilerLazyInterpreter.deps.expressionCompiler
      .compileWithoutContextValidation(expression, parameterDef.name, parameterDef.typ)(nodeId)
      .valueOr(err => throw new IllegalArgumentException(s"Compilation failed with errors: ${err.toList.mkString(", ")}"))
    val evaluator = compilerLazyInterpreter.deps.expressionEvaluator
    val compiledParameter = compiledgraph.evaluatedparam.Parameter(TypedExpression(compiledExpression, Unknown, null), parameterDef)
    context: Context => Future.successful(evaluator.evaluateParameter(compiledParameter, context)(nodeId, compilerLazyInterpreter.metaData)).map(_.value.asInstanceOf[T])(ec)
  }
}

trait CompilerLazyParameterInterpreter extends LazyParameterInterpreter {

  def deps: LazyInterpreterDependencies

  def metaData: MetaData

  //it's important that it's (...): (Context => Future[T])
  //and not e.g. (...)(Context) => Future[T] as we want to be sure when body is evaluated (in particular expression compilation)!
  private[definition] def createInterpreter[T <: AnyRef](ec: ExecutionContext, definition: LazyParameter[T]): Context => Future[T] = {
    definition match {
      case e: EvaluableLazyParameter[T] => e.prepareEvaluator(this)(ec)
      case _ => throw new IllegalArgumentException(s"LazyParameter $definition is not supported")
    }
  }

  override def syncInterpretationFunction[T <: AnyRef](lazyInterpreter: LazyParameter[T]): Context => T = {

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

  override protected val additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[NodeId], classOf[MetaData], classOf[ComponentUseCase])

}