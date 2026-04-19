package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.esotericsoftware.kryo.io.{Input, Output}
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumWriter}
import org.apache.avro.io.{BinaryDecoder, BinaryEncoder, DatumReader, DecoderFactory, EncoderFactory}
import org.apache.flink.types.DeserializationException
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema.DatumReaderWriterMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  GenericRecordWithSchemaId,
  IntSchemaId,
  SchemaId,
  SchemaRegistryClient,
  SchemaRegistryClientFactory,
  StringSchemaId
}

import java.util
import java.util.Objects
import scala.util.control.NonFatal

/**
 * A specialized serializer for [[GenericRecordWithSchemaId]], uses an optimized binary format that writes records
 * together with schema identifiers, unlike Flink's default Avro serializer that writes entire schemas.
 *
 * Works under the assumption that:
 * <ul>
 *   <li>every schema registry is assigned a distinct identifier, a `schemaRegistryId`
 *   <li>serializer's state is carried over to snapshots and transferred over network to other TaskManagers
 * </ul>
 */
@SerialVersionUID(1L)
class GenericRecordWithSchemaIdSerializer(
    private val schemaRegistryClientFactory: SchemaRegistryClientFactory,
    private val schemaRegistryConfigs: util.Map[Int, SchemaRegistryClientKafkaConfig]
) extends Serializer[GenericRecordWithSchemaId](false, false)
    with DatumReaderWriterMixin
    with Serializable
    with LazyLogging {

  import GenericRecordWithSchemaIdSerializer._

  def this(
      schemaRegistryClientFactory: SchemaRegistryClientFactory,
      schemaRegistryConfig: Map[Int, SchemaRegistryClientKafkaConfig]
  ) =
    this(schemaRegistryClientFactory, GenericRecordWithSchemaIdSerializer.asJavaMap(schemaRegistryConfig))

  // configuration
  @transient private lazy val schemaRegistryClients: util.Map[Int, SchemaRegistryClient] = new util.HashMap()

  // state
  @transient private var writers: util.Map[Int, util.Map[SchemaId, GenericDatumWriter[Any]]] = _
  @transient private var readers: util.Map[Int, util.Map[SchemaId, DatumReader[AnyRef]]]     = _
  @transient private var encoder: BinaryEncoder                                              = _
  @transient private var decoder: BinaryDecoder                                              = _

  def registerSchemaRegistry(schemaRegistryId: Int, schemaRegistryConfig: SchemaRegistryClientKafkaConfig): Unit = {
    val old = schemaRegistryConfigs.get(schemaRegistryId)
    if (old != null) {
      val oldUrl = old.kafkaProperties.get("schema.registry.url")
      val newUrl = schemaRegistryConfig.kafkaProperties.get("schema.registry.url")
      logger.warn(s"Overwriting registration for schemaRegistryId $schemaRegistryId: $oldUrl -> $newUrl")
    }
    schemaRegistryConfigs.put(schemaRegistryId, schemaRegistryConfig)
  }

  override def write(kryo: Kryo, output: Output, record: GenericRecordWithSchemaId): Unit = {
    // typically identifiers should be manually assigned, i.e. they should be small positive integers
    output.writeVarInt(record.getSchemaRegistryId, true)
    record.getSchemaId match {
      case IntSchemaId(value) =>
        output.writeVarInt(value, true)
      case StringSchemaId(value) =>
        output.writeVarInt(GenericRecordWithSchemaIdSerializer.stringSchemaMarker, true)
        output.writeString(value)
    }

    if (writers == null) {
      writers = new util.HashMap()
    }
    val writer = writers
      .computeIfAbsent(record.getSchemaRegistryId, _ => new util.HashMap())
      .computeIfAbsent(record.getSchemaId, { _ => createDatumWriter(record.getSchema) })

    encoder = EncoderFactory.get().directBinaryEncoder(output, null)
    writer.write(record, encoder)
  }

  override def read(
      kryo: Kryo,
      input: Input,
      clazz: Class[_ <: GenericRecordWithSchemaId]
  ): GenericRecordWithSchemaId = {
    val schemaRegistryId = input.readVarInt(true)
    val schemaIdInt      = input.readVarInt(true)
    val schemaId = if (schemaIdInt >= 0) {
      SchemaId.fromInt(schemaIdInt)
    } else if (schemaIdInt == stringSchemaMarker) {
      val schemaIdString = input.readString()
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
          val schema = getSchema(schemaRegistryId, schemaId)
          createDatumReader(schema, schema)
        }
      )

    decoder = DecoderFactory.get().directBinaryDecoder(input, null)
    val record = reader.read(null, decoder).asInstanceOf[GenericData.Record]

    new GenericRecordWithSchemaId(record, schemaRegistryId, schemaId, false)
  }

  private def getSchema(schemaRegistryId: Int, schemaId: SchemaId): Schema = {
    val client = schemaRegistryClients.computeIfAbsent(
      schemaRegistryId,
      { id =>
        schemaRegistryClientFactory.create(
          Option(schemaRegistryConfigs.get(id))
            .getOrElse(throw new IllegalArgumentException(s"Unknown schemaRegistryId: $id"))
        )
      }
    )
    try {
      val schema = client.getSchemaById(schemaId).schema
      AvroUtils.extractSchema(schema)
    } catch {
      case NonFatal(e) =>
        val url = Option(schemaRegistryConfigs.get(schemaRegistryId))
          .flatMap(_.kafkaProperties.get("schema.registry.url"))
          .getOrElse("?")
        throw new DeserializationException(
          s"Failed to fetch schema $schemaId from Schema Registry at $url (schemaRegistryId $schemaRegistryId)",
          e
        )
    }
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: GenericRecordWithSchemaIdSerializer =>
      schemaRegistryClientFactory == other.schemaRegistryClientFactory &&
      schemaRegistryConfigs == other.schemaRegistryConfigs
    case _ => false
  }

  override def hashCode(): Int = Objects.hashCode(schemaRegistryClientFactory, schemaRegistryConfigs)

}

object GenericRecordWithSchemaIdSerializer {
  private val stringSchemaMarker: Int = -1

  private def asJavaMap[K, V](map: Map[K, V]): util.Map[K, V] = {
    val jMap = new util.HashMap[K, V](map.size)
    map.foreach { case (k, v) => jMap.put(k, v) }
    jMap
  }

}
