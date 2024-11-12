package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.LazyParameter.{
  CustomLazyParameter,
  CustomLazyParamterWithCustomizableEvaluationLogic,
  Evaluate
}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{BaseCompiledParameter, BaseExpressionEvaluator, Context, JobData, NodeId}
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

class EvaluableLazyParameter[T <: AnyRef](
    val compiledParameter: BaseCompiledParameter,
    val expressionEvaluator: ExpressionEvaluator,
    nodeId: NodeId,
    val jobData: JobData,
    override val returnType: TypingResult,
    val customEvaluate: Option[(BaseCompiledParameter, ExpressionEvaluator, NodeId, JobData, Context) => T] = None
) extends CustomLazyParamterWithCustomizableEvaluationLogic[T] {

  def this(
      compiledParameter: CompiledParameter,
      expressionEvaluator: ExpressionEvaluator,
      nodeId: NodeId,
      jobData: JobData
  ) =
    this(compiledParameter, expressionEvaluator, nodeId, jobData, compiledParameter.typingInfo.typingResult)

  override val evaluate: Evaluate[T] = { ctx: Context =>
    customEvaluate
      .map(evaluate => evaluate(compiledParameter, expressionEvaluator, nodeId, jobData, ctx))
      .getOrElse {
        expressionEvaluator
          .evaluateParameter(compiledParameter, ctx)(nodeId, jobData)
          .value
          .asInstanceOf[T]
      }
  }

  def withCustomEvaluationLogic(
      customEvaluate: (BaseCompiledParameter, BaseExpressionEvaluator, NodeId, JobData, Context) => T
  ): EvaluableLazyParameter[T] = {
    new EvaluableLazyParameter(
      compiledParameter,
      expressionEvaluator,
      nodeId,
      jobData,
      returnType,
      Some(customEvaluate)
    )
  }

}
