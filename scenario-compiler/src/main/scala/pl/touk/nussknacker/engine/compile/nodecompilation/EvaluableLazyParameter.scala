package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter.{TemplateExpression, TemplateExpressionPart}
import pl.touk.nussknacker.engine.api.LazyParameter.{CustomLazyParameter, Evaluate, TemplateLazyParameter}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, JobData, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.compiledgraph.BaseCompiledParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.SpelTemplateExpressionPart.{Literal, Placeholder}
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

class SpelTemplateEvaluableLazyParameter[T <: AnyRef] private (
    compiledParameter: BaseCompiledParameter,
    templateSubexpressions: List[SpelTemplateExpressionPart],
    expressionEvaluator: ExpressionEvaluator,
    nodeId: NodeId,
    jobData: JobData
) extends TemplateLazyParameter[T] {

  override val evaluate: Evaluate[T] =
    LazyParameterEvaluator.evaluate(compiledParameter, expressionEvaluator, nodeId, jobData)

  override lazy val templateExpression: TemplateExpression = {
    val templateParts = templateSubexpressions.map {
      case Placeholder(expression) => {
        new TemplateExpressionPart.Placeholder {
          override val evaluate: Evaluate[String] = context => {
            expressionEvaluator.evaluate[String](expression, "placeholderExpression", nodeId.id, context)(jobData).value
          }
        }
      }
      case Literal(value) => TemplateExpressionPart.Literal(value)
    }
    TemplateExpression(templateParts)
  }

  override def returnType: TypingResult = Typed[String]
}

object SpelTemplateEvaluableLazyParameter {

  def build[T <: AnyRef](
      compiledParameter: BaseCompiledParameter,
      expressionEvaluator: ExpressionEvaluator,
      nodeId: NodeId,
      jobData: JobData
  ): SpelTemplateEvaluableLazyParameter[T] = {
    compiledParameter.expression match {
      case expression: SpelExpression => {
        val subexpressions = expression.templateSubexpressions.getOrElse(
          throw new IllegalStateException("Non SpEL-template expression received in SpelTemplateLazyParameter")
        )
        new SpelTemplateEvaluableLazyParameter[T](
          compiledParameter,
          subexpressions,
          expressionEvaluator,
          nodeId,
          jobData
        )
      }
      case _ => throw new IllegalStateException("Non SpEL expression received in SpelTemplateLazyParameter")
    }
  }

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
    compiledParameter.expression.language match {
      case Language.SpelTemplate =>
        SpelTemplateEvaluableLazyParameter.build(
          compiledParameter,
          expressionEvaluator,
          nodeId,
          jobData
        )
      case _ =>
        new EvaluableLazyParameter(
          compiledParameter,
          expressionEvaluator,
          nodeId,
          jobData,
          typingResult
        )
    }
  }

}
