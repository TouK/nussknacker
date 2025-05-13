package pl.touk.nussknacker.engine.schemedkafka.encode

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.util.json.{EncodeInput, EncodeOutput, ToJsonSchemaBasedEncoderCustomisation}

import scala.jdk.CollectionConverters._

class AvroToJsonSchemaBasedEncoderCustomisation extends ToJsonSchemaBasedEncoderCustomisation {

  override def encoder(
      delegateEncode: EncodeInput => EncodeOutput
  ): PartialFunction[(Any, Schema, Option[String]), EncodeOutput] = {
    case (e: org.apache.avro.generic.GenericRecord, s: Schema, fieldName: Option[String]) =>
      val map = e.getSchema.getFields.asScala.map(_.name()).map(n => n -> e.get(n)).toMap
      delegateEncode(map, s, fieldName)
  }

}
