package pl.touk.nussknacker.engine.api.generics

import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.ErrorDetails
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.TabularDataDefinitionParserErrorDetails.CellError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

trait ExpressionParseError {
  def message: String
  def details: Option[ErrorDetails] = None
}

object ExpressionParseError {

  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

  @ConfiguredJsonCodec sealed trait ErrorDetails

  sealed abstract class ExpressionParserCompilationErrorDetails(val language: Language) extends ErrorDetails
  final case class TabularDataDefinitionParserErrorDetails(cellErrors: List[CellError])
      extends ExpressionParserCompilationErrorDetails(Language.TabularDataDefinition)

  object TabularDataDefinitionParserErrorDetails {
    @JsonCodec final case class CellError(columnName: String, rowIdx: Int, message: String)
  }

}

case class Signature(noVarArgs: List[TypingResult], varArg: Option[TypingResult]) {
  private def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")

  def display(name: String): String = varArg match {
    case Some(x) => s"$name(${typesToString(noVarArgs :+ x)}...)"
    case None    => s"$name(${typesToString(noVarArgs)})"
  }

}
