package pl.touk.nussknacker.engine.expression.parse

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

trait CompiledExpression {

  def language: Language

  def original: String

  def evaluate[T](ctx: Context, globals: Map[String, Any]): T
}

sealed trait TypedValue

case class TypedExpression(expression: CompiledExpression, typingInfo: ExpressionTypingInfo) extends TypedValue {
  def returnType: TypingResult = typingInfo.typingResult
}

case class TypedExpressionMap(valueByKey: Map[String, TypedExpression]) extends TypedValue
