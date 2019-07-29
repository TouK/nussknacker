package pl.touk.nussknacker.engine.literal

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, ExpressionParser, TypedExpression, ValueWithLazyContext}
import pl.touk.nussknacker.engine.api.lazyy.LazyValuesProvider
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

import scala.concurrent.Future

//Mainly for samples, testing, but can be useful also in normal, production code
case class LiteralExpression(original: String) extends pl.touk.nussknacker.engine.api.expression.Expression {

  override def language: String = LiteralExpressionParser.languageId

  override def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]]
    = Future.successful(ValueWithLazyContext(original.asInstanceOf[T], ctx.lazyContext))
  
}

object LiteralExpressionParser extends ExpressionParser {

  override def languageId: String = "literal"

  override def parse(original: String, ctx: ValidationContext, expectedType: typing.TypingResult): Validated[NonEmptyList[ExpressionParseError], TypedExpression] =
    parseWithoutContextValidation(original, expectedType).map(TypedExpression(_, Typed[String]))

  override def parseWithoutContextValidation(original: String, expectedType: typing.TypingResult): Validated[NonEmptyList[ExpressionParseError],
    pl.touk.nussknacker.engine.api.expression.Expression] = Valid(LiteralExpression(original))
}
