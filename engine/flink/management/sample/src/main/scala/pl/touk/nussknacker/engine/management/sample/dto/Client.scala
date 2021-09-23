package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder

@JsonCodec case class Client(id: String, name: String) extends DisplayJsonWithEncoder[Client]
