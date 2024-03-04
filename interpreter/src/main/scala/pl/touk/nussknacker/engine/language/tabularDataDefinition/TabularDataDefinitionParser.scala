package pl.touk.nussknacker.engine.language.tabularDataDefinition

import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionParser, ExpressionTypingInfo, TypedExpression}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{
  ErrorDetails,
  TabularDataDefinitionParserErrorDetails
}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.CreationError.{
  CellsCountInRowDifferentThanColumnsCount,
  ColumnNameUniquenessViolation,
  InvalidCellValues
}
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Error

object TabularDataDefinitionParser extends ExpressionParser {

  override final val languageId: Language = Language.TabularDataDefinition

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
  ): ValidatedNel[ExpressionParseError, Expression] = {
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
      override val language: Language                                      = languageId
      override val original: String                                        = anOriginal
      override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = tabularTypedData.asInstanceOf[T]
    }
  }

  private def toExpressionParseError(error: TabularTypedData.Error) = {
    new ExpressionParseError {

      override val message: String = error match {
        case Error.CannotParseError(message) =>
          message
        case Error.CannotCreateError(ColumnNameUniquenessViolation(columnNames)) =>
          s"Column names should be unique. Duplicates: ${columnNames.distinct.toList.mkString(",")}"
        case Error.CannotCreateError(CellsCountInRowDifferentThanColumnsCount) =>
          "All rows should have the same number of cells as there are columns"
        case Error.CannotCreateError(InvalidCellValues(_)) =>
          "Typing error in some cells"
      }

      override val details: Option[ErrorDetails] = error match {
        case Error.CannotParseError(_)                                         => None
        case Error.CannotCreateError(ColumnNameUniquenessViolation(_))         => None
        case Error.CannotCreateError(CellsCountInRowDifferentThanColumnsCount) => None
        case Error.CannotCreateError(InvalidCellValues(invalidCells)) =>
          Some(
            TabularDataDefinitionParserErrorDetails(
              invalidCells.toList.map { coordinates =>
                TabularDataDefinitionParserErrorDetails.CellError(
                  columnName = coordinates.columnName.name,
                  rowIdx = coordinates.rowIndex,
                  message =
                    s"Column has a '${coordinates.columnName.aType.getSimpleName}' type but its value cannot be converted to the type."
                )
              }
            )
          )
      }
    }
  }

}
