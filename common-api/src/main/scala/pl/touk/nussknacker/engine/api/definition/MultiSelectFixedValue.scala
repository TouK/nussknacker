package pl.touk.nussknacker.engine.api.definition

import io.circe.Json
import io.circe.generic.JsonCodec

@JsonCodec case class MultiSelectFixedValue(
    value: Json,
    label: String,
    description: Option[String] = None,
    group: Option[String] = None,
)
