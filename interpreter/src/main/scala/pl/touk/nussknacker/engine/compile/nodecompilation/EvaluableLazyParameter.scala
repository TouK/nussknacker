package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.{Context, LazyParameter, MetaData, NodeId}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

class EvaluableLazyParameter[T <: AnyRef](
    compiledParameter: CompiledParameter,
    expressionEvaluator: ExpressionEvaluator,
    nodeId: NodeId,
    metaData: MetaData
) extends LazyParameter[T] {

  override val returnType: TypingResult = compiledParameter.typingInfo.typingResult

  override val evaluate: Context => T = { ctx: Context =>
    expressionEvaluator
      .evaluateParameter(compiledParameter, ctx)(nodeId, metaData)
      .value
      .asInstanceOf[T]
  }

}
