package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils.readIdAndGetBuffer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentUtils, UniversalSchemaSupport}
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import java.nio.ByteBuffer
import scala.reflect.ClassTag

class MismatchReaderWriterSchemaException(expectedType: String, actualType: String) extends IllegalArgumentException(s"Expecting schema of type $expectedType. but got payload with $actualType schema type")

class ConfluentUniversalKafkaDeserializer[T](schemaRegistryClient: ConfluentSchemaRegistryClient,
                                             readerSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                             isKey: Boolean) extends Deserializer[T] {

  private case class SchemaIdWithPositionedBuffer(value: Int, buffer: ByteBuffer) {
    def bufferStartPosition: Int = buffer.position()
  }

  override def deserialize(topic: String, data: Array[Byte]): T = {
    throw new IllegalAccessException(s"Operation not supported. ${this.getClass.getSimpleName} requires kafka headers to perform deserialization.")
  }

  private val headerName = if (isKey) KeySchemaIdHeaderName else ValueSchemaIdHeaderName

  override def deserialize(topic: String, headers: Headers, data: Array[Byte]): T = {
    val writerSchemaId = getSchemaId(topic, headers, data)
    val writerSchema = schemaRegistryClient.getSchemaById(writerSchemaId.value).schema

    readerSchemaDataOpt.map(_.schema.schemaType()).foreach(readerSchemaType => {
      if (readerSchemaType != writerSchema.schemaType())
        throw new MismatchReaderWriterSchemaException(readerSchemaType, writerSchema.schemaType()) //todo: test this case when supporting json schema
    })

    val writerSchemaData = new RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](writerSchema), Some(writerSchemaId.value))

    UniversalSchemaSupport(writerSchema)
      .payloadDeserializer
      .deserialize(readerSchemaDataOpt, writerSchemaData, writerSchemaId.buffer, writerSchemaId.bufferStartPosition)
      .asInstanceOf[T]
  }

  // SchemaId can be obtain in several ways. Precedent:
  // * from kafka header
  // * from payload serialized in 'Confluent way' ([magicbyte][schemaid][payload])
  // * latest schema for topic
  private def getSchemaId(topic: String, headers: Headers, data: Array[Byte]): SchemaIdWithPositionedBuffer =
    headers.getSchemaId(headerName) match {
      case Some(idFromHeader) => // Even if schemaId is passed through header, it still can be serialized in 'Confluent way', here we're figuring it out
        val buffer = readIdAndGetBuffer(data).toOption.map(_._2).getOrElse(ByteBuffer.wrap(data))
        SchemaIdWithPositionedBuffer(idFromHeader, buffer)
      case None =>
        val idAndBuffer = ConfluentUtils.readIdAndGetBuffer(data).toOption
          .getOrElse(schemaRegistryClient
            .getLatestSchemaId(topic, isKey = isKey).map((_, ByteBuffer.wrap(data)))
            .valueOr(e => throw new RuntimeException("Missing schemaId in kafka header and in payload. Trying to fetch latest schema for this topic but it failed", e)))
        SchemaIdWithPositionedBuffer(idAndBuffer._1, buffer = idAndBuffer._2)
    }
}

trait ConfluentUniversalKafkaDeserializerFactory extends LazyLogging {

  protected def createDeserializer[T: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                kafkaConfig: KafkaConfig,
                                                schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                isKey: Boolean): Deserializer[T] = {

    val schemaRegistryClient: ConfluentSchemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    new ConfluentUniversalKafkaDeserializer[T](schemaRegistryClient, schemaDataOpt, isKey)
  }

}

class ConfluentKeyValueUniversalKafkaDeserializationFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory with ConfluentUniversalKafkaDeserializerFactory {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K] =
    createDeserializer[K](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = true)

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V] =
    createDeserializer[V](schemaRegistryClientFactory, kafkaConfig, schemaDataOpt, isKey = false)
}