package pl.touk.nussknacker.engine.util.json

import io.circe.Json

trait ToJsonEncoderCustomisation {
  def encoder(delegateEncode: Any => Json): PartialFunction[Any, Json]
}
