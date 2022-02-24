package pl.touk.nussknacker.engine.avro.encode

import io.circe.Json
import pl.touk.nussknacker.engine.util.json.{BestEffortJsonEncoder, ToJsonEncoder}

import scala.jdk.CollectionConverters.asScalaBufferConverter

class AvroToJsonEncoder extends ToJsonEncoder {

  override def encoder(encoder: BestEffortJsonEncoder): PartialFunction[Any, Json] = {
    case e: org.apache.avro.generic.GenericRecord =>
      val map = e.getSchema.getFields.asScala.map(_.name()).map(n => n -> e.get(n)).toMap
      encoder.encode(map)
  }
}
