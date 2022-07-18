package pl.touk.nussknacker.engine.api.function

import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

case class Parameter(typ: TypingResult, name: String, description: Option[String])

case class Signature(parameters: List[Parameter],
                     varParameter: Option[Parameter],
                     result: TypingResult,
                     description: Option[String])

trait ExtendedFunction {
  def name: String
  def description: Option[String]
  def signatures: Option[NonEmptyList[Signature]]

  def applyTypes(arguments: List[TypingResult]): ValidatedNel[String, TypingResult]

  def applyValues(arguments: List[Any]): Any
}
