package pl.touk.nussknacker.engine.expression.parse

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait CompiledExpression {

  def language: String

  def original: String

  def evaluate[T](ctx: Context, globals: Map[String, Any]): T
}

sealed trait TypedValue

case class TypedExpression(expression: CompiledExpression, typingInfo: ExpressionTypingInfo) extends TypedValue {
  def returnType: TypingResult = typingInfo.typingResult
}

case class TypedExpressionMap(valueByKey: Map[String, TypedExpression]) extends TypedValue
