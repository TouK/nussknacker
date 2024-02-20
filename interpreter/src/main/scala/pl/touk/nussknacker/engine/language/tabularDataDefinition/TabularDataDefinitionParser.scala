package pl.touk.nussknacker.engine.language.tabularDataDefinition

import cats.data.ValidatedNel
import cats.implicits._
import io.circe.DecodingFailure
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData

object TabularDataDefinitionParser extends ExpressionParser {

  override final val languageId: String = Language.TabularDataDefinition

  override def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    parse(original, fromTabularDataToT = createTabularDataDefinitionTypedExpression(_, original, expectedType))
  }

  override def parseWithoutContextValidation(
      original: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression] = {
    parse(original, fromTabularDataToT = createTabularDataDefinitionExpression(_, original))
  }

  private def parse[T](original: String, fromTabularDataToT: TabularTypedData => T) = {
    TabularTypedData
      .fromString(original)
      .map(fromTabularDataToT)
      .left
      .map(toExpressionParseError)
      .toValidatedNel
  }

  private def createTabularDataDefinitionTypedExpression(
      tabularTypedData: TabularTypedData,
      anOriginal: String,
      expectedType: typing.TypingResult
  ) = TypedExpression(
    createTabularDataDefinitionExpression(tabularTypedData, anOriginal),
    new ExpressionTypingInfo {
      override def typingResult: typing.TypingResult = expectedType
    }
  )

  private def createTabularDataDefinitionExpression(tabularTypedData: TabularTypedData, anOriginal: String) = {
    new CompiledExpression {
      override val language: String                                        = languageId
      override val original: String                                        = anOriginal
      override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = tabularTypedData.asInstanceOf[T]
    }
  }

  private def toExpressionParseError(error: Throwable) = {
    new ExpressionParseError {
      override val message: String = error match {
        case DecodingFailure((message, _)) => message
        case other                         => other.getMessage
      }
    }
  }

}
