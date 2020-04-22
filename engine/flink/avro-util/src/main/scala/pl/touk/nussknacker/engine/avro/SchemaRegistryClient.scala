package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}

trait SchemaRegistryClient {

  def schemaById(id: Int): Schema

  def schemaBySubjectAndVersion(subject: String, version: Int): String

  def latestSchema(name: String): String
}
