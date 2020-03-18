package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

@JsonCodec(encodeOnly = true) case class ComplexObject(foo: Map[String, Any]) extends DisplayJsonWithEncoder[ComplexObject]

object ComplexObject {

  private val encoder = BestEffortJsonEncoder(failOnUnkown = false)

  private implicit val mapEncoder: Encoder[Map[String, Any]] = Encoder.instance[Map[String, Any]](encoder.encode)
}
