package pl.touk.nussknacker.engine.expression.parse

import pl.touk.nussknacker.engine.api.{CompiledExpression, Context}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

sealed trait TypedValue

case class TypedExpression(expression: CompiledExpression, typingInfo: ExpressionTypingInfo) extends TypedValue {
  def returnType: TypingResult = typingInfo.typingResult
}

case class TypedExpressionMap(valueByKey: Map[String, TypedExpression]) extends TypedValue
