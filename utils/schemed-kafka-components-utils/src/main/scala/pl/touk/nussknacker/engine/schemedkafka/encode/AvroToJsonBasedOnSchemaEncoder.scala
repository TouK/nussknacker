package pl.touk.nussknacker.engine.schemedkafka.encode

import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.util.json.{EncodeInput, EncodeOutput, ToJsonBasedOnSchemaEncoder}

import scala.jdk.CollectionConverters.asScalaBufferConverter

class AvroToJsonBasedOnSchemaEncoder extends ToJsonBasedOnSchemaEncoder {

  override def encoder(delegateEncode: EncodeInput => EncodeOutput): PartialFunction[(Any, Schema, Schema, Option[String]), EncodeOutput] = {
    case (e: org.apache.avro.generic.GenericRecord, s: Schema, ps: Schema, fieldName: Option[String]) =>
      val map = e.getSchema.getFields.asScala.map(_.name()).map(n => n -> e.get(n)).toMap
      delegateEncode(map, s, ps, fieldName)
  }
}
