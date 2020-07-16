package pl.touk.nussknacker.engine.avro.encode

sealed trait EncoderPolicy extends Serializable {
  def acceptUnfilledOptional: Boolean
  def acceptRedundant: Boolean
}

case object CheckMandatoryEncoderPolicy extends EncoderPolicy {

  override def acceptUnfilledOptional: Boolean = true

  override def acceptRedundant: Boolean = true

}

case object CheckMandatoryAndRedundantEncoderPolicy extends EncoderPolicy {

  override def acceptUnfilledOptional: Boolean = true

  override def acceptRedundant: Boolean = false

}

case object CheckAllEncoderPolicy extends EncoderPolicy {

  override def acceptUnfilledOptional: Boolean = false

  override def acceptRedundant: Boolean = false

}