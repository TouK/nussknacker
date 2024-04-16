package pl.touk.nussknacker.engine.api.generics

import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.ErrorDetails
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ExpressionParseError {
  def message: String
  def details: Option[ErrorDetails] = None
}

object ExpressionParseError {

  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

  @ConfiguredJsonCodec sealed trait ErrorDetails

  final case class TabularDataDefinitionParserErrorDetails(cellErrors: List[CellError]) extends ErrorDetails

  @JsonCodec final case class CellError(columnName: String, rowIndex: Int, errorMessage: String)

}

case class Signature(noVarArgs: List[TypingResult], varArg: Option[TypingResult]) {
  private def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")

  def display(name: String): String = varArg match {
    case Some(x) => s"$name(${typesToString(noVarArgs :+ x)}...)"
    case None    => s"$name(${typesToString(noVarArgs)})"
  }

}
