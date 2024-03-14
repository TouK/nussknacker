package pl.touk.nussknacker.engine.api.validation

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException

final case class ValidationMode private (name: String, label: String)

object ValidationMode {

  // Requires providing all fields, including optional fields, without redundant fields
  val strict: ValidationMode = ValidationMode("strict", "Strict mode")

  // Requires providing only required fields, without optional fields, with redundant fields
  val lax: ValidationMode = ValidationMode("lax", "Lax mode")

  val values: List[ValidationMode] = List(strict, lax)

  def fromString(value: String, paramName: ParameterName): ValidationMode =
    values
      .find(_.name == value)
      .getOrElse(throw CustomNodeValidationException(s"Unknown validation mode: $value", Some(paramName)))

}
