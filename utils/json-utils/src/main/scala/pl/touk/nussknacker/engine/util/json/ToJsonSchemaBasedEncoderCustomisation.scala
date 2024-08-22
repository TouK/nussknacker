package pl.touk.nussknacker.engine.util.json

trait ToJsonSchemaBasedEncoderCustomisation {

  def encoder(delegateEncode: EncodeInput => EncodeOutput): PartialFunction[EncodeInput, EncodeOutput]
}
