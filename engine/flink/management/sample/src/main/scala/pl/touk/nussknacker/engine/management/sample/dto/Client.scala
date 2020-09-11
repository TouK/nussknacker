package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder

@JsonCodec case class Client(id: String, name: String) extends DisplayJsonWithEncoder[Client]
