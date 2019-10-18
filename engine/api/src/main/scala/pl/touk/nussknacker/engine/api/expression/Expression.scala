package pl.touk.nussknacker.engine.api.expression

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.concurrent.Future

trait Expression {

  def language: String

  def original: String

  def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]]
}

trait ExpressionParser {

  def languageId: String

  def parse(original: String, ctx: ValidationContext, expectedType: TypingResult):
  ValidatedNel[ExpressionParseError, TypedExpression]

  def parseWithoutContextValidation(original: String): ValidatedNel[ExpressionParseError, Expression]

}

case class ExpressionParseError(message: String)

case class ValueWithLazyContext[T](value: T, lazyContext: LazyContext)


sealed trait TypedValue

case class TypedExpression(expression: Expression, returnType: TypingResult) extends TypedValue

case class TypedExpressionMap(valueByKey: Map[String, TypedExpression]) extends TypedValue
