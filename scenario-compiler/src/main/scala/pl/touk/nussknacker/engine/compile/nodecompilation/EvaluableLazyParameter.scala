package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.LazyParameter.{CustomLazyParameter, Evaluate}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, JobData, MetaData, NodeId}
import pl.touk.nussknacker.engine.compiledgraph.{BaseCompiledParameter, CompiledParameter}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

class EvaluableLazyParameter[T <: AnyRef](
    compiledParameter: BaseCompiledParameter,
    expressionEvaluator: ExpressionEvaluator,
    nodeId: NodeId,
    jobData: JobData,
    override val returnType: TypingResult
) extends CustomLazyParameter[T] {

  def this(
      compiledParameter: CompiledParameter,
      expressionEvaluator: ExpressionEvaluator,
      nodeId: NodeId,
      jobData: JobData
  ) =
    this(compiledParameter, expressionEvaluator, nodeId, jobData, compiledParameter.typingInfo.typingResult)

  override val evaluate: Evaluate[T] = { ctx: Context =>
    expressionEvaluator
      .evaluateParameter(compiledParameter, ctx)(nodeId, jobData)
      .value
      .asInstanceOf[T]
  }

}
