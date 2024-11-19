package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter.EvaluableExpressionPart
import pl.touk.nussknacker.engine.api.LazyParameter.{CustomLazyParameter, Evaluate, TemplateLazyParameter}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, JobData, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.compiledgraph.BaseCompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.spel.{SpelExpression, SpelTemplateExpressionPart}

class EvaluableLazyParameter[T <: AnyRef](
    compiledParameter: BaseCompiledParameter,
    expressionEvaluator: ExpressionEvaluator,
    nodeId: NodeId,
    jobData: JobData,
    override val returnType: TypingResult
) extends CustomLazyParameter[T] {

  override val evaluate: Evaluate[T] =
    LazyParameterEvaluator.evaluate(compiledParameter, expressionEvaluator, nodeId, jobData)

}

class SpelTemplateEvaluableLazyParameter[T <: AnyRef] private[nodecompilation] (
    compiledParameter: BaseCompiledParameter,
    override val parts: List[EvaluableExpressionPart],
    expressionEvaluator: ExpressionEvaluator,
    nodeId: NodeId,
    jobData: JobData
) extends TemplateLazyParameter[T] {

  override val evaluate: Evaluate[T] =
    LazyParameterEvaluator.evaluate(compiledParameter, expressionEvaluator, nodeId, jobData)

  override def returnType: TypingResult = Typed[String]
}

private[this] object LazyParameterEvaluator {

  def evaluate[T <: AnyRef](
      compiledParameter: BaseCompiledParameter,
      expressionEvaluator: ExpressionEvaluator,
      nodeId: NodeId,
      jobData: JobData
  ): Evaluate[T] = { ctx: Context =>
    expressionEvaluator
      .evaluateParameter(compiledParameter, ctx)(nodeId, jobData)
      .value
      .asInstanceOf[T]
  }

}

object EvaluableLazyParameterFactory {

  def build[T <: AnyRef](
      compiledParameter: BaseCompiledParameter,
      expressionEvaluator: ExpressionEvaluator,
      nodeId: NodeId,
      jobData: JobData,
      typingResult: TypingResult
  ): LazyParameter[T] = {
    def createGenericEvaluableLazyParameter =
      new EvaluableLazyParameter(
        compiledParameter,
        expressionEvaluator,
        nodeId,
        jobData,
        typingResult
      )
    def createTemplateLazyParameter(parts: List[SpelTemplateExpressionPart]) = {
      val templateParts = parts.map {
        case SpelTemplateExpressionPart.Placeholder(expression) => {
          new EvaluableExpressionPart.Placeholder {
            override val evaluate: Evaluate[String] = context => {
              expressionEvaluator
                .evaluate[String](expression, "placeholderExpression", nodeId.id, context)(jobData)
                .value
            }
          }
        }
        case SpelTemplateExpressionPart.Literal(value) => EvaluableExpressionPart.Literal(value)
      }
      new SpelTemplateEvaluableLazyParameter[T](
        compiledParameter,
        templateParts,
        expressionEvaluator,
        nodeId,
        jobData
      )
    }

    compiledParameter.expression match {
      case expression: SpelExpression =>
        expression.templateSubexpressions match {
          case Some(parts) =>
            createTemplateLazyParameter(parts)
          case None =>
            createGenericEvaluableLazyParameter
        }
      case _ =>
        createGenericEvaluableLazyParameter
    }
  }

}
