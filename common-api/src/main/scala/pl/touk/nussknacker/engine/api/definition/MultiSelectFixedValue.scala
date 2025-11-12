package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec

@JsonCodec case class MultiSelectFixedValue(value: String, label: String)
