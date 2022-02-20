package pl.touk.nussknacker.engine.api.expression

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait Expression {

  def language: String

  def original: String

  def evaluate[T](ctx: Context, globals: Map[String, Any]): T
}

sealed trait TypedValue

case class TypedExpression(expression: Expression, returnType: TypingResult, typingInfo: ExpressionTypingInfo) extends TypedValue

case class TypedExpressionMap(valueByKey: Map[String, TypedExpression]) extends TypedValue

/**
  * It contains information about intermediate result of typing of expression. Can be used for further processing of expression
  * like some substitutions base on type...
  */
trait ExpressionTypingInfo {

  def typingResult: TypingResult
}