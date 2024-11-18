package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter.{TemplateExpression, TemplateExpressionPart}
import pl.touk.nussknacker.engine.api.LazyParameter.{CustomLazyParameter, Evaluate, TemplateLazyParameter}
import pl.touk.nussknacker.engine.api.definition.{Parameter => ParameterDef}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, JobData, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.compiledgraph.BaseCompiledParameter
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.EditorBasedLanguageDeterminer
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.SpelExpression
import pl.touk.nussknacker.engine.spel.SpelTemplateSubexpression.{NonTemplatedValue, TemplatedExpression}

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

class SpelTemplateEvaluableLazyParameter[T <: AnyRef](
    compiledParameter: BaseCompiledParameter,
    expressionEvaluator: ExpressionEvaluator,
    nodeId: NodeId,
    jobData: JobData
) extends TemplateLazyParameter[T] {

  override val evaluate: Evaluate[T] =
    LazyParameterEvaluator.evaluate(compiledParameter, expressionEvaluator, nodeId, jobData)

  override def templateExpression: TemplateExpression = compiledParameter.expression match {
    case expression: SpelExpression =>
      expression.templateSubexpressions match {
        case Some(subexpressions) =>
          val templateParts = subexpressions.map {
            case TemplatedExpression(expression) => {
              new TemplateExpressionPart.TemplatedPart {
                override val evaluate: Evaluate[String] = context => {
                  expressionEvaluator.evaluate[String](expression, "expressionId", nodeId.id, context)(jobData).value
                }
              }
            }
            case NonTemplatedValue(value) => TemplateExpressionPart.NonTemplatedPart(value)
          }
          TemplateExpression(templateParts)
        case None =>
          throw new IllegalStateException("Non SpEL-template expression received in SpelTemplateLazyParameter")
      }
    case _ => throw new IllegalStateException("Non SpEL expression received in SpelTemplateLazyParameter")
  }

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
      parameterDefinition: ParameterDef,
      typingResult: TypingResult
  ): LazyParameter[T] = {
    EditorBasedLanguageDeterminer.determineLanguageOf(parameterDefinition.editor) match {
      case Language.SpelTemplate =>
        new SpelTemplateEvaluableLazyParameter[T](
          compiledParameter,
          expressionEvaluator,
          nodeId,
          jobData
        )
      case _ =>
        new EvaluableLazyParameter[T](
          compiledParameter,
          expressionEvaluator,
          nodeId,
          jobData,
          typingResult
        )
    }
  }

}
