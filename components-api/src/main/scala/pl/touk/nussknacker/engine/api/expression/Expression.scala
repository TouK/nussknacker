package pl.touk.nussknacker.engine.api.expression

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

// TODO: rename to CompiledExpression
// It is used in two places:
// - In extension-api as a return type of ExpressionParser which can be provided as an extension
// - In ValidationExpressionParameterValidator
// TODO: In the second place, in the api should be defined Validator based on a common-api Expression
//       and in the compiler logic we should postprocess this validator to validator that uses CompiledExpression.
//       Thanks to that this future will be available not only for fragments, but for components and we could
//       move this class to the extensions-api and rename it
trait Expression {

  def language: String

  def original: String

  def evaluate[T](ctx: Context, globals: Map[String, Any]): T
}

sealed trait TypedValue

case class TypedExpression(expression: Expression, typingInfo: ExpressionTypingInfo) extends TypedValue {
  def returnType: TypingResult = typingInfo.typingResult
}

case class TypedExpressionMap(valueByKey: Map[String, TypedExpression]) extends TypedValue
