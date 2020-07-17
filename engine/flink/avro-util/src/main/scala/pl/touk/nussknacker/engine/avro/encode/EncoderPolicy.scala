package pl.touk.nussknacker.engine.avro.encode

case class EncoderPolicy private (name: String, label: String, acceptUnfilledOptional: Boolean, acceptRedundant: Boolean)

object EncoderPolicy {

  val strict: EncoderPolicy = EncoderPolicy("strict", "Strict mode", acceptUnfilledOptional = false, acceptRedundant = false)

  val allowOptional: EncoderPolicy = EncoderPolicy("allowOptional", "No optional parameters validations", acceptUnfilledOptional = true, acceptRedundant = false)

  val allowRedundantAndOptional: EncoderPolicy =  EncoderPolicy("allowRedundantAndOptional", "Allow redundant parameters", acceptUnfilledOptional = true, acceptRedundant = true)

  val values: List[EncoderPolicy] = List(strict, allowOptional, allowRedundantAndOptional)

  def byName(name: String): Option[EncoderPolicy] = values.find(_.name == name)

}