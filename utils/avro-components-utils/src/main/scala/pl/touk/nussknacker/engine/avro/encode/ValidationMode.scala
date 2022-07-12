package pl.touk.nussknacker.engine.avro.encode

case class ValidationMode private(name: String, label: String, acceptRedundant: Boolean)

object ValidationMode {

  val allowOptional: ValidationMode = ValidationMode("allowOptional", "Allow missing optional parameters", acceptRedundant = false)

  val allowRedundantAndOptional: ValidationMode =  ValidationMode("allowRedundantAndOptional", "Allow missing optional and redundant parameters", acceptRedundant = true)

  val values: List[ValidationMode] = List(allowOptional, allowRedundantAndOptional)

  def byName(name: String): Option[ValidationMode] = values.find(_.name == name)

}
