package pl.touk.nussknacker.engine.avro.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.ReflectDatumWriter
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import pl.touk.nussknacker.engine.avro.AvroUtils

import scala.collection.mutable
import scala.jdk.CollectionConverters.mapAsScalaMapConverter

/**
  * Mixin for DatumReader and DatumWriter. It collects factory methods for Datums.
  */
trait DatumReaderWriterMixin {

  /**
    * We use it on checking writerSchema is primitive - on creating DatumReader (createDatumReader).
    */
  protected val primitives: mutable.Map[String, Schema] = AvroSchemaUtils.getPrimitiveSchemas.asScala

  def createDatumWriter(record: Any, schema: Schema, useSchemaReflection: Boolean): GenericDatumWriter[Any] = record match {
    case _: SpecificRecord => new SpecificDatumWriter[Any](schema, AvroUtils.specificData)
    case _ if useSchemaReflection => new ReflectDatumWriter[Any](schema, AvroUtils.reflectData)
    case _ => new GenericDatumWriter[Any](schema, AvroUtils.genericData)
  }

  def createDatumReader(writerSchema: Schema, readerSchema: Schema, useSchemaReflection: Boolean, useSpecificAvroReader: Boolean): DatumReader[AnyRef] = {
    val writerSchemaIsPrimitive = primitives.values.exists(_.equals(readerSchema))

    if (useSchemaReflection && !writerSchemaIsPrimitive) {
      StringForcingDatumReaderProvider.reflectDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.reflectData)
    } else if (useSpecificAvroReader && !writerSchemaIsPrimitive) {
      StringForcingDatumReaderProvider.specificDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.specificData)
    } else {
      StringForcingDatumReaderProvider.genericDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.genericData)
    }
  }
}
