package pl.touk.nussknacker.engine.expression

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

final case class ExpectedType(typ: TypingResult, strictTypeMatch: Boolean)

object ExpectedType {

  def strict(expectedType: TypingResult): ExpectedType = {
    ExpectedType(typ = expectedType, strictTypeMatch = true)
  }

  def loose(expectedType: TypingResult): ExpectedType = {
    ExpectedType(typ = expectedType, strictTypeMatch = false)
  }

  def fromParameter(parameter: Parameter): ExpectedType = {
    ExpectedType(parameter.typ, parameter.strictTypeCheck)
  }

}
