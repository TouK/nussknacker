package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec

@JsonCodec case class SelectOption(value: String, label: String)
