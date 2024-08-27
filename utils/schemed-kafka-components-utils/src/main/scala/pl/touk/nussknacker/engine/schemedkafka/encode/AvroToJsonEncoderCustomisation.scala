package pl.touk.nussknacker.engine.schemedkafka.encode

import io.circe.Json
import pl.touk.nussknacker.engine.util.json.ToJsonEncoderCustomisation

import scala.jdk.CollectionConverters._

class AvroToJsonEncoderCustomisation extends ToJsonEncoderCustomisation {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = {
    case e: org.apache.avro.generic.GenericRecord =>
      val map = e.getSchema.getFields.asScala.map(_.name()).map(n => n -> e.get(n)).toMap
      encode(map)
  }

}
