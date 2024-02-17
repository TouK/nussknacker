package pl.touk.nussknacker.engine.language.tabularDataDefinition

import cats.data.Validated.Valid
import cats.data.{Validated, ValidatedNel}
import io.circe.parser.{parse => parseJson}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionParser, ExpressionTypingInfo, TypedExpression}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

object TabularDataDefinitionParser extends ExpressionParser {

  override final val languageId: String = Language.TabularDataDefinition

  override def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    parseTabularTypedData(original) match {
      case Right(data) =>
        Valid(createTabularDataDefinitionTypedExpression(data, original, expectedType))
      case Left(error) =>
        Validated.invalidNel(new ExpressionParseError {
          override def message: String = error.getMessage
        })
    }
  }

  override def parseWithoutContextValidation(
      original: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, Expression] = {
    parseTabularTypedData(original) match {
      case Right(data) =>
        Valid(createTabularDataDefinitionExpression(data, original))
      case Left(error) =>
        Validated.invalidNel(new ExpressionParseError {
          override def message: String = error.getMessage
        })
    }
  }

  private def parseTabularTypedData(value: String) = {
    for {
      json <- parseJson(value)
      data <- TabularTypedData.decoder.decodeJson(json)
    } yield data
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
    new Expression {
      override val language: String                                        = languageId
      override val original: String                                        = anOriginal
      override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = tabularTypedData.asInstanceOf[T]
    }
  }

}
