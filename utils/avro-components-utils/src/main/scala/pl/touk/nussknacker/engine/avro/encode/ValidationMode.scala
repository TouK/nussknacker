package pl.touk.nussknacker.engine.avro.encode

case class ValidationMode private(name: String, label: String, strict: Boolean)

object ValidationMode {

  val strict: ValidationMode = ValidationMode("strict", "Strict mode", strict = true)

  val loose: ValidationMode = ValidationMode("flexible", "Loose mode", strict = false)

  val values: List[ValidationMode] = List(strict, loose)

  def byName(name: String): Option[ValidationMode] = values.find(_.name == name)

}
