package pl.touk.nussknacker.engine.expression.parse

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

trait CompiledExpression {

  def language: Language

  def original: String

  def evaluate[T](ctx: Context, globals: Map[String, Any]): T

  def toExpression: Expression = Expression(language, original)
}

case class TypedExpression(expression: CompiledExpression, typingInfo: ExpressionTypingInfo) {
  def returnType: TypingResult = typingInfo.typingResult
}

sealed trait TypedValue

case class SingleBranchTypedValue(typedExpression: TypedExpression, expressionInputValidationContext: ValidationContext)
    extends TypedValue

case class MultipleBranchesTypedValue(valueByBranchId: Map[String, SingleBranchTypedValue]) extends TypedValue
