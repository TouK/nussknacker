package pl.touk.nussknacker.engine.api.generics

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ExpressionParseError {
  def message: String
  def templateValues: Option[TemplateValues] = None
  def errorCode: Option[String] = None
}

case class Signature(noVarArgs: List[TypingResult], varArg: Option[TypingResult]) {
  private def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")

  def display(name: String): String = varArg match {
    case Some(x) => s"$name(${typesToString(noVarArgs :+ x)}...)"
    case None => s"$name(${typesToString(noVarArgs)})"
  }
}

case class TemplateValues(
    stringValues: Map[String, String] = Map.empty,
    typingResultValues: Map[String, TypingResult] = Map.empty
)
