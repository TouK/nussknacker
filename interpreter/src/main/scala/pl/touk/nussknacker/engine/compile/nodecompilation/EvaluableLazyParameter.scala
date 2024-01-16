package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.{Context, MetaData, NodeId}
import pl.touk.nussknacker.engine.api.lazyparam.{LazyParameterDeps, LazyParameterWithPotentiallyPostponedEvaluator}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

class EvaluableLazyParameter[T <: AnyRef](
    compiledParameter: CompiledParameter,
    evaluator: ExpressionEvaluator,
    nodeId: NodeId,
    metaData: MetaData
) extends LazyParameterWithPotentiallyPostponedEvaluator[T] {

  override def prepareEvaluator(deps: LazyParameterDeps): Context => T = { context: Context =>
    evaluator
      .evaluateParameter(compiledParameter, context)(nodeId, metaData)
      .value
      .asInstanceOf[T]
  }

  override def returnType: TypingResult = compiledParameter.typingInfo.typingResult

}
