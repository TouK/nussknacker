package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericContainer, GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.{ReflectDatumReader, ReflectDatumWriter}
import org.apache.avro.specific.{SpecificDatumReader, SpecificDatumWriter, SpecificRecord}

/**
  * Mixin for DatumReader and DatumWriter. It collects factory methods for Datums.
  */
trait DatumReaderWriterMixin {

  /**
    * It's copy paste from AvroSchemaUtils, because there this is private
    * We use it on checking writerSchema is primitive - on creating DatumReader (createDatumReader).
    */
  lazy protected val primitives: Map[String, Schema] = {
    val parser = new Schema.Parser
    parser.setValidateDefaults(false)

    Map(
      "Null" -> createPrimitiveSchema(parser, "null"),
      "Boolean" -> createPrimitiveSchema(parser, "boolean"),
      "Integer" -> createPrimitiveSchema(parser, "int"),
      "Long" -> createPrimitiveSchema(parser, "long"),
      "Float" -> createPrimitiveSchema(parser, "float"),
      "Double" -> createPrimitiveSchema(parser, "double"),
      "String" -> createPrimitiveSchema(parser, "string"),
      "Bytes" -> createPrimitiveSchema(parser, "bytes")
    )
  }

  def createDatumWriter(record: Any, schema: Schema, useSchemaReflection: Boolean): GenericDatumWriter[Any] = record match {
    case _: SpecificRecord => new SpecificDatumWriter[Any](schema)
    case _ if useSchemaReflection => new ReflectDatumWriter[Any](schema)
    case _ => new GenericDatumWriter[Any](schema)
  }

  /**
    * Detecting DatumReader is based on record type and varying writerSchema is primitive
    *
    * @param record
    * @param writerSchema
    * @param readerSchema
    * @return
    */
  def createDatumReader(record: GenericContainer, writerSchema: Schema, readerSchema: Schema, useSchemaReflection: Boolean): DatumReader[Any] = {
    val writerSchemaIsPrimitive = primitives.values.exists(_.equals(writerSchema))

    record match {
      case _: SpecificRecord if !writerSchemaIsPrimitive => new SpecificDatumReader(writerSchema, readerSchema)
      case _ if useSchemaReflection && !writerSchemaIsPrimitive => new ReflectDatumReader(writerSchema, readerSchema)
      case _ => new GenericDatumReader(writerSchema, readerSchema)
    }
  }

  /**
    * It's copy paste from AvroSchemaUtils.createPrimitiveSchema, because there this method is private and
    * we use it on checking writerSchema is primitive - on creating DatumReader (createDatumReader).
    *
    * @param parser
    * @param `type`
    * @return
    */
  protected def createPrimitiveSchema(parser: Schema.Parser, `type`: String): Schema = {
    val schemaString = String.format("{\"type\" : \"%s\"}", `type`)
    parser.parse(schemaString)
  }
}
