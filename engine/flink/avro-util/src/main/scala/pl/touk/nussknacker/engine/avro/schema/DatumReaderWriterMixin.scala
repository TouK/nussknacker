package pl.touk.nussknacker.engine.avro.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData.StringType
import org.apache.avro.generic.{GenericData, GenericDatumReader, GenericDatumWriter}
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


  def createDatumWriter(record: Any, schema: Schema, useSchemaReflection: Boolean, useStringForStringSchema: Boolean): GenericDatumWriter[Any] = {
    if(useStringForStringSchema) setStringType(schema, StringType.String)
    record match {
      case _: SpecificRecord => new SpecificDatumWriter[Any](schema, AvroUtils.specificData)
      case _ if useSchemaReflection => new ReflectDatumWriter[Any](schema, AvroUtils.reflectData)
      case _ => new GenericDatumWriter[Any](schema, AvroUtils.genericData)
    }
  }

  def createDatumReader(writerSchema: Schema,
                        readerSchema: Schema,
                        useSchemaReflection: Boolean,
                        useSpecificAvroReader: Boolean,
                        useStringForStringSchema: Boolean
                       ): DatumReader[AnyRef] = {
    val writerSchemaIsPrimitive = primitives.values.exists(_.equals(readerSchema))

    if(useStringForStringSchema) {
      setStringType(readerSchema, StringType.String)
      setStringType(writerSchema, StringType.String)
    }

    if (useSchemaReflection && !writerSchemaIsPrimitive) {
      new ReflectDatumReader(writerSchema, readerSchema, AvroUtils.reflectData)
    } else if (useSpecificAvroReader && !writerSchemaIsPrimitive) {
      new SpecificDatumReader(writerSchema, readerSchema, AvroUtils.specificData)
    } else {
      new GenericDatumReader(writerSchema, readerSchema, AvroUtils.genericData)
    }
  }

  private def setStringType(s: Schema, stringType: GenericData.StringType): Unit =
    s.getType match {
      case Schema.Type.STRING => GenericData.setStringType(s, stringType)
      case Schema.Type.RECORD => s.getFields.asScala.foreach(f => setStringType(f.schema(), stringType))
      case _ => ()
    }

}
