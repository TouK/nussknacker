package pl.touk.nussknacker.engine.util.json

trait ToJsonBasedOnSchemaEncoder {

  def encoder(delegateEncode: EncodeInput => EncodeOutput): PartialFunction[EncodeInput, EncodeOutput]
}
