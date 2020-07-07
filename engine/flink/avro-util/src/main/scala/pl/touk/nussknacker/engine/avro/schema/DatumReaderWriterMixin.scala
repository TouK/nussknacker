package pl.touk.nussknacker.engine.avro.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericContainer, GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.{ReflectDatumReader, ReflectDatumWriter}
import org.apache.avro.specific.{SpecificDatumReader, SpecificDatumWriter, SpecificRecord}
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

  /**
    * Detecting DatumWriter
    *
    * @param record
    * @param schema
    * @param useSchemaReflection
    * @return
    */
  def createDatumWriter(record: Any, schema: Schema, useSchemaReflection: Boolean): GenericDatumWriter[Any] = record match {
    case _: SpecificRecord => new SpecificDatumWriter[Any](schema, AvroUtils.SpecificData)
    case _ if useSchemaReflection => new ReflectDatumWriter[Any](schema, AvroUtils.ReflectData)
    case _ => new GenericDatumWriter[Any](schema, AvroUtils.GenericData)
  }

  /**
    * Detecting DatumReader is based on record type and varying writerSchema is primitive
    */
  def createDatumReader(record: GenericContainer, writerSchema: Schema, readerSchema: Schema, useSchemaReflection: Boolean): DatumReader[Any] = {
    val writerSchemaIsPrimitive = primitives.values.exists(_.equals(writerSchema))

    record match {
      case _: SpecificRecord if !writerSchemaIsPrimitive => new SpecificDatumReader(writerSchema, readerSchema, AvroUtils.SpecificData)
      case _ if useSchemaReflection && !writerSchemaIsPrimitive => new ReflectDatumReader(writerSchema, readerSchema, AvroUtils.ReflectData)
      case _ => new GenericDatumReader(writerSchema, readerSchema, AvroUtils.GenericData)
    }
  }

  def createDatumReader(writerSchema: Schema, readerSchema: Schema, useSchemaReflection: Boolean, useSpecificAvroReader: Boolean): DatumReader[Any] = {
    val writerSchemaIsPrimitive = primitives.values.exists(_.equals(readerSchema))

    if (useSchemaReflection && !writerSchemaIsPrimitive) {
      new ReflectDatumReader(writerSchema, readerSchema, AvroUtils.ReflectData)
    } else if (useSpecificAvroReader && !writerSchemaIsPrimitive) {
      new SpecificDatumReader(writerSchema, readerSchema, AvroUtils.SpecificData)
    } else {
      new GenericDatumReader(writerSchema, readerSchema, AvroUtils.GenericData)
    }
  }
}
