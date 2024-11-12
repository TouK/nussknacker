package pl.touk.nussknacker.engine.language.tabularDataDefinition

import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api.{CompiledExpression, Context}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{
  CellError,
  ColumnDefinition,
  ErrorDetails,
  TabularDataDefinitionParserErrorDetails
}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.expression.parse.{ExpressionParser, TypedExpression}
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
      override val language: Language                                      = languageId
      override val original: String                                        = anOriginal
      override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = tabularTypedData.asInstanceOf[T]
    }
  }

  private def toExpressionParseError(error: TabularTypedData.Error) = {
    new ExpressionParseError {

      override val message: String = error match {
        case Error.JsonParsingError(message) =>
          message
        case Error.ValidationError(ColumnNameUniquenessViolation(columnNames)) =>
          s"Column names should be unique. Duplicates: ${columnNames.distinct.toList.mkString(",")}"
        case Error.ValidationError(CellsCountInRowDifferentThanColumnsCount) =>
          "All rows should have the same number of cells as there are columns"
        case Error.ValidationError(InvalidCellValues(_, _)) =>
          "Typing error in some cells"
      }

      override val details: Option[ErrorDetails] = error match {
        case Error.JsonParsingError(_)                                       => None
        case Error.ValidationError(ColumnNameUniquenessViolation(_))         => None
        case Error.ValidationError(CellsCountInRowDifferentThanColumnsCount) => None
        case Error.ValidationError(InvalidCellValues(invalidCells, columnDefinitions)) =>
          Some(
            TabularDataDefinitionParserErrorDetails(
              invalidCells.map { coordinates =>
                CellError(
                  columnName = coordinates.columnName.name,
                  rowIndex = coordinates.rowIndex,
                  errorMessage =
                    s"The column '${coordinates.columnName.name}' is expected to contain '${coordinates.columnName.aType.getSimpleName}' values, but the entered value does not match this type."
                )
              }.toList,
              columnDefinitions.map(cd => ColumnDefinition(cd.name, cd.aType))
            )
          )
      }
    }
  }

}
