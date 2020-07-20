package pl.touk.nussknacker.engine.avro.encode

case class ValidationMode private(name: String, label: String, acceptUnfilledOptional: Boolean, acceptRedundant: Boolean)

object ValidationMode {

  val strict: ValidationMode = ValidationMode("strict", "Strict mode", acceptUnfilledOptional = false, acceptRedundant = false)

  val allowOptional: ValidationMode = ValidationMode("allowOptional", "No optional parameters validations", acceptUnfilledOptional = true, acceptRedundant = false)

  val allowRedundantAndOptional: ValidationMode =  ValidationMode("allowRedundantAndOptional", "Allow redundant parameters", acceptUnfilledOptional = true, acceptRedundant = true)

  val values: List[ValidationMode] = List(strict, allowOptional, allowRedundantAndOptional)

  def byName(name: String): Option[ValidationMode] = values.find(_.name == name)

}