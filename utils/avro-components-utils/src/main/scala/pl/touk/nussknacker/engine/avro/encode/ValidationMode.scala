package pl.touk.nussknacker.engine.avro.encode

case class ValidationMode private(name: String, label: String, acceptRedundant: Boolean)

object ValidationMode {

  val strict: ValidationMode = ValidationMode("strict", "Strict mode", acceptRedundant = false)

  val allowRedundant: ValidationMode =  ValidationMode("allowRedundant", "Allow redundant parameters", acceptRedundant = true)

  val values: List[ValidationMode] = List(strict, allowRedundant)

  def byName(name: String): Option[ValidationMode] = values.find(_.name == name)

}
