package pl.touk.nussknacker.engine.avro.encode

final case class ValidationMode private(name: String, label: String)

object ValidationMode {

  //Requires providing all fields, including optional fields, without redundant fields
  val strict: ValidationMode = ValidationMode("strict", "Strict mode")

  //Requires providing only required fields, without optional fields, with redundant fields
  val lax: ValidationMode = ValidationMode("lax", "Lax mode")

  val values: List[ValidationMode] = List(strict, lax)

  def byName(name: String): Option[ValidationMode] = values.find(_.name == name)

}
