package pl.touk.nussknacker.engine.avro.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.ReflectDatumWriter
import org.apache.avro.specific.SpecificDatumWriter
import pl.touk.nussknacker.engine.avro.AvroUtils

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * Mixin for DatumReader and DatumWriter. It collects factory methods for Datums.
  */
trait DatumReaderWriterMixin {

  /**
    * We use it on checking writerSchema is primitive - on creating DatumReader (createDatumReader).
    */
  protected val primitives: mutable.Map[String, Schema] = AvroSchemaUtils.getPrimitiveSchemas.asScala

  def createDatumWriter(schema: Schema, useSchemaReflection: Boolean, useSpecificAvroReader: Boolean): GenericDatumWriter[Any] = {
    if (useSpecificAvroReader) {
      new SpecificDatumWriter[Any](schema, AvroUtils.specificData)
    } else if (useSchemaReflection) {
      new ReflectDatumWriter[Any](schema, AvroUtils.reflectData)
    } else {
      new GenericDatumWriter[Any](schema, AvroUtils.genericData)
    }
  }

  def createDatumReader(writerSchema: Schema, readerSchema: Schema, useSchemaReflection: Boolean, useSpecificAvroWriter: Boolean): DatumReader[AnyRef] = {
    val writerSchemaIsPrimitive = primitives.values.exists(_.equals(readerSchema))

    if (useSchemaReflection && !writerSchemaIsPrimitive) {
      StringForcingDatumReaderProvider.reflectDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.reflectData)
    } else if (useSpecificAvroWriter && !writerSchemaIsPrimitive) {
      StringForcingDatumReaderProvider.specificDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.specificData)
    } else {
      StringForcingDatumReaderProvider.genericDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.genericData)
    }
  }
}
