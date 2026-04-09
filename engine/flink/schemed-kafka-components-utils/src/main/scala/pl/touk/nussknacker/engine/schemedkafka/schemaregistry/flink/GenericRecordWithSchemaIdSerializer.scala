package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumWriter}
import org.apache.avro.io.{BinaryDecoder, BinaryEncoder, DatumReader, DecoderFactory, EncoderFactory}
import org.apache.flink.annotation.VisibleForTesting
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.api.java.typeutils.runtime.{DataInputViewStream, DataOutputViewStream}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  GenericRecordWithSchemaId,
  IntSchemaId,
  SchemaId,
  StringSchemaId
}

import java.util

/**
 * A specialized serializer for [[GenericRecordWithSchemaId]], uses an optimized binary format that writes records
 * together with schema identifiers, unlike Flink's default Avro serializer that writes entire schemas.
 *
 * Works under the assumption that:
 * <ul>
 *   <li>every schema registry is assigned a distinct identifier, a `schemaRegistryId`
 *   <li>schemas are immutable - a single `schemaId` value will always map to the same schema
 * </ul>
 *
 * State is additive, old schemas are never expired. Serializer state compaction would be possible if we tracked usage
 * between [[snapshotConfiguration]] and restorations from [[GenericRecordWithSchemaIdSerializerSnapshot]].
 */
@SerialVersionUID(1L)
class GenericRecordWithSchemaIdSerializer(
    private var schemas: util.Map[Int, util.Map[SchemaId, Schema]]
) extends TypeSerializer[GenericRecordWithSchemaId]
    with DatumReaderWriterMixin {

  def this() = this(null)

  @transient private var writers: util.Map[Int, util.Map[SchemaId, GenericDatumWriter[Any]]] = _
  @transient private var readers: util.Map[Int, util.Map[SchemaId, DatumReader[AnyRef]]]     = _
  // Specialized Avro encoder/decoder that handles compact number encoding
  // - Flink's DataOutputEncoder/DataInputDecoder doesn't optimize its output for size
  @transient private var encoder: BinaryEncoder = _
  @transient private var decoder: BinaryDecoder = _

  override def isImmutableType: Boolean = false

  override def getLength: Int = -1

  override def createInstance(): GenericRecordWithSchemaId =
    new GenericRecordWithSchemaId(Schema.create(Schema.Type.RECORD), 0, SchemaId.fromInt(0))

  override def serialize(record: GenericRecordWithSchemaId, target: DataOutputView): Unit = {
    encoder = EncoderFactory.get().directBinaryEncoder(new DataOutputViewStream(target), encoder)

    encoder.writeInt(record.getSchemaRegistryId)
    record.getSchemaId match {
      case IntSchemaId(value) =>
        encoder.writeInt(value)
      case StringSchemaId(value) =>
        encoder.writeInt(GenericRecordWithSchemaIdSerializer.stringSchemaMarker)
        encoder.writeString(value)
    }

    if (writers == null) {
      writers = new util.HashMap()
    }
    val writer = writers
      .computeIfAbsent(record.getSchemaRegistryId, _ => new util.HashMap())
      .computeIfAbsent(
        record.getSchemaId,
        { schemaId =>
          if (schemas == null) {
            schemas = new util.HashMap()
          }
          schemas
            .computeIfAbsent(record.getSchemaRegistryId, _ => new util.HashMap())
            .put(schemaId, record.getSchema)
          createDatumWriter(record.getSchema)
        }
      )

    writer.write(record, encoder)
  }

  override def deserialize(source: DataInputView): GenericRecordWithSchemaId = {
    decoder = DecoderFactory.get().directBinaryDecoder(new DataInputViewStream(source), decoder)

    val schemaRegistryId = decoder.readInt()
    val schemaIdInt      = decoder.readInt()
    val schemaId = if (schemaIdInt >= 0) {
      SchemaId.fromInt(schemaIdInt)
    } else if (schemaIdInt == GenericRecordWithSchemaIdSerializer.stringSchemaMarker) {
      val schemaIdString = decoder.readString()
      SchemaId.fromString(schemaIdString)
    } else {
      throw new IllegalArgumentException(
        s"Unsupported schemaId format: $schemaIdInt. Should be non-negative integer or -1 for string schemas"
      )
    }

    if (readers == null) {
      readers = new util.HashMap()
    }
    val reader = readers
      .computeIfAbsent(schemaRegistryId, _ => new util.HashMap())
      .computeIfAbsent(
        schemaId,
        { schemaId =>
          if (schemas == null) {
            throw new IllegalStateException(
              s"Serializer has not been initialized, cannot deserialize object schemaRegistryId/schemaId pair: $schemaRegistryId/$schemaId"
            )
          }
          val srSchemas = schemas.get(schemaRegistryId)
          if (srSchemas == null) {
            throw new IllegalStateException(s"Unknown schemaRegistryId: $schemaRegistryId")
          }
          val schema = srSchemas.get(schemaId)
          if (schema == null) {
            throw new IllegalStateException(s"Unknown schemaRegistryId/schemaId pair: $schemaRegistryId/$schemaId")
          }
          createDatumReader(schema, schema)
        }
      )

    val record = reader.read(null, decoder).asInstanceOf[GenericData.Record]

    new GenericRecordWithSchemaId(record, schemaRegistryId, schemaId, false)
  }

  override def deserialize(reuse: GenericRecordWithSchemaId, source: DataInputView): GenericRecordWithSchemaId =
    deserialize(source)

  override def copy(from: GenericRecordWithSchemaId): GenericRecordWithSchemaId =
    AvroUtils.genericData.deepCopy(from.getSchema, from)

  override def copy(from: GenericRecordWithSchemaId, reuse: GenericRecordWithSchemaId): GenericRecordWithSchemaId =
    copy(from)

  override def copy(source: DataInputView, target: DataOutputView): Unit = serialize(deserialize(source), target)

  override def snapshotConfiguration(): TypeSerializerSnapshot[GenericRecordWithSchemaId] =
    new GenericRecordWithSchemaIdSerializerSnapshot(schemas)

  override def duplicate(): TypeSerializer[GenericRecordWithSchemaId] =
    new GenericRecordWithSchemaIdSerializer(GenericRecordWithSchemaIdSerializer.cloneSchemas(schemas))

  override def equals(obj: Any): Boolean = obj match {
    case other: GenericRecordWithSchemaIdSerializer => schemas == other.schemas
    case _                                          => false
  }

  // noinspection HashCodeUsesVar
  override def hashCode(): Int = if (schemas == null) 0 else schemas.hashCode()

  @VisibleForTesting
  private[flink] def getSchemas: util.Map[Int, util.Map[SchemaId, Schema]] = schemas

}

object GenericRecordWithSchemaIdSerializer {
  private val stringSchemaMarker: Int = -1

  private[flink] def cloneSchemas(
      schemas: util.Map[Int, util.Map[SchemaId, Schema]]
  ): util.Map[Int, util.Map[SchemaId, Schema]] = {
    if (schemas == null) {
      null
    } else {
      val copy = new util.HashMap[Int, util.Map[SchemaId, Schema]]()
      schemas.forEach((k, v) => copy.put(k, new util.HashMap(v)))
      copy
    }
  }

}
