package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

@JsonCodec(encodeOnly = true) case class ComplexObject(foo: java.util.Map[String, Any]) extends DisplayJsonWithEncoder[ComplexObject]

object ComplexObject {

  private implicit val mapEncoder: Encoder[java.util.Map[String, Any]] = Encoder.instance[java.util.Map[String, Any]](BestEffortJsonEncoder.defaultForTests.encode)
}
