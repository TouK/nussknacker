package pl.touk.nussknacker.engine.schemedkafka.encode

import io.circe.Json
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.util.json.{ToJsonBasedOnSchemaEncoder, ToJsonEncoder}

import scala.jdk.CollectionConverters.asScalaBufferConverter

class AvroToJsonBasedOnSchemaEncoder extends ToJsonBasedOnSchemaEncoder {

  override def encoder(delegateEncode: (Any, Schema, Option[String]) => WithError[Json]): PartialFunction[(Any, Schema, Option[String]), WithError[Json]] = {
    case (e: org.apache.avro.generic.GenericRecord, s: Schema, fieldName: Option[String]) =>
      val map = e.getSchema.getFields.asScala.map(_.name()).map(n => n -> e.get(n)).toMap
      delegateEncode(map, s, fieldName)
  }
}
