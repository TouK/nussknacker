package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

final case class EvaluableLazyParameterCreator[T <: AnyRef](
    nodeId: NodeId,
    parameterDef: definition.Parameter,
    typedExpression: TypedExpression,
) extends LazyParameter[T] {

  def create(deps: EvaluableLazyParameterCreatorDeps): EvaluableLazyParameter[T] = {
    new EvaluableLazyParameter[T](
      CompiledParameter(typedExpression, parameterDef),
      deps.expressionEvaluator,
      nodeId,
      deps.metaData
    )
  }

  override val returnType: TypingResult = typedExpression.returnType

  override def evaluator: Context => T =
    throw new IllegalStateException(
      s"[${classOf[EvaluableLazyParameterCreator[_]].getName}] doesn't provide evaluator"
    ) // todo: better msg

}

class EvaluableLazyParameterCreatorDeps(
    val expressionCompiler: ExpressionCompiler,
    val expressionEvaluator: ExpressionEvaluator,
    val metaData: MetaData
)

class DefaultLazyParameterInterpreter(deps: EvaluableLazyParameterCreatorDeps) extends LazyParameterInterpreter {

  override def syncInterpretationFunction[T <: AnyRef](lazyParameter: LazyParameter[T]): Context => T = {
    // it's important that it's (...): (Context => T)
    // and not e.g. (...)(Context) => T as we want to be sure when body is evaluated (in particular expression compilation)!
    val newLazyParam = lazyParameter match {
      case e: EvaluableLazyParameterCreator[T] => e.create(deps)
      case _ => throw new IllegalArgumentException(s"LazyParameter $lazyParameter is not supported")
    }
    v1: Context => newLazyParam.evaluator.apply(v1)
  }

}
