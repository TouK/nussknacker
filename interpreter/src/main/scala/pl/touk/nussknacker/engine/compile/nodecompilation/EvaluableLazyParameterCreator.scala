package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.LazyParameter.Evaluate
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

// This class looks like a LazyParameter but actually it's not - it's a creator of the LazyParameter.
// It's Flink specific. And should not be evaluated directly. See the `evaluate` method implementation and
// DefaultToEvaluateFunctionConverter class.
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

  override def evaluate: Evaluate[T] =
    throw new IllegalStateException(
      s"[${classOf[EvaluableLazyParameterCreator[_]].getName}] looks like a LazyParameter, but actually it's a creator of [${classOf[EvaluableLazyParameter[T]].getName}] instance. It cannot be evaluated directly!"
    )

}

case class EvaluableLazyParameterCreatorDeps(
    val expressionCompiler: ExpressionCompiler,
    val expressionEvaluator: ExpressionEvaluator,
    val metaData: MetaData
)

class DefaultToEvaluateFunctionConverter(deps: EvaluableLazyParameterCreatorDeps) extends ToEvaluateFunctionConverter {

  override def toEvaluateFunction[T <: AnyRef](lazyParameter: LazyParameter[T]): Evaluate[T] = {
    // it's important that it's (...): (Context => T)
    // and not e.g. (...)(Context) => T as we want to be sure when body is evaluated (in particular expression compilation)!
    val newLazyParam = lazyParameter match {
      case e: EvaluableLazyParameterCreator[T] => e.create(deps)
      case _ => throw new IllegalArgumentException(s"LazyParameter $lazyParameter is not supported")
    }
    v1: Context => newLazyParam.evaluate(v1)
  }

}
