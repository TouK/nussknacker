package pl.touk.nussknacker.engine.avro.encode

final case class ValidationMode private(name: String, label: String)

object ValidationMode {

  val strict: ValidationMode = ValidationMode("strict", "Strict mode")

  val loose: ValidationMode = ValidationMode("loose", "Loose mode")

  val values: List[ValidationMode] = List(strict, loose)

  def byName(name: String): Option[ValidationMode] = values.find(_.name == name)

}
