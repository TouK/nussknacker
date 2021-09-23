package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder

@JsonCodec case class SampleProduct(id: Long, name: String, someProperty: String) extends DisplayJsonWithEncoder[SampleProduct]
