package pl.touk.nussknacker.engine.schemedkafka.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.ReflectDatumWriter
import org.apache.avro.specific.{SpecificDatumWriter, SpecificRecord}
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
  * Mixin for DatumReader and DatumWriter. It collects factory methods for Datums.
  */
trait DatumReaderWriterMixin {

  /**
    * We use it on checking writerSchema is primitive - on creating DatumReader (createDatumReader).
    */
  protected val primitives: mutable.Map[String, Schema] = AvroSchemaUtils.getPrimitiveSchemas.asScala

  @transient private lazy val datumReaderCache =
    new ConcurrentHashMap[(Schema, Schema, Boolean, Boolean), DatumReader[AnyRef]]()

  @transient private lazy val datumWriterCache =
    new ConcurrentHashMap[(RecordType, Schema), GenericDatumWriter[Any]]()

  def getDatumWriter(record: Any, schema: Schema, useSchemaReflection: Boolean): GenericDatumWriter[Any] = {
    val recordType = RecordType(record, useSchemaReflection)
    datumWriterCache.computeIfAbsent(
      (recordType, schema),
      _ => {
        recordType match {
          case Specific   => new SpecificDatumWriter[Any](schema, AvroUtils.specificData)
          case Reflection => new ReflectDatumWriter[Any](schema, AvroUtils.reflectData)
          case _          => new GenericDatumWriter[Any](schema, AvroUtils.genericData)
        }
      }
    )
  }

  def getDatumReader(
      writerSchema: Schema,
      readerSchema: Schema,
      useSchemaReflection: Boolean,
      useSpecificAvroReader: Boolean
  ): DatumReader[AnyRef] = {
    datumReaderCache.computeIfAbsent(
      (writerSchema, readerSchema, useSchemaReflection, useSpecificAvroReader),
      _ => {
        val writerSchemaIsPrimitive = primitives.values.exists(_.equals(readerSchema))

        if (useSchemaReflection && !writerSchemaIsPrimitive) {
          StringForcingDatumReaderProvider.reflectDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.reflectData)
        } else if (useSpecificAvroReader && !writerSchemaIsPrimitive) {
          StringForcingDatumReaderProvider
            .specificDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.specificData)
        } else {
          StringForcingDatumReaderProvider.genericDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.genericData)
        }
      }
    )
  }

  private sealed trait RecordType extends Serializable
  private object Specific         extends RecordType
  private object Reflection       extends RecordType
  private object Generic          extends RecordType

  private object RecordType {

    def apply(record: Any, useSchemaReflection: Boolean): RecordType =
      record match {
        case _: SpecificRecord        => Specific
        case _ if useSchemaReflection => Reflection
        case _                        => Generic
      }

  }

}
