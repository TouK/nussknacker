package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils.readIdAndGetBuffer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentAvroPayloadDeserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde.{KeySchemaIdHeaderName, ValueSchemaIdHeaderName}
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import ConfluentUniversalKafkaSerde._

import java.nio.ByteBuffer
import scala.reflect.ClassTag
import scala.util.Try

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

  private lazy val avroPayloadDeserializer = new ConfluentAvroPayloadDeserializer(false, false, false, DecoderFactory.get())

  override def deserialize(topic: String, headers: Headers, data: Array[Byte]): T = {
    val writerSchemaId = getSchemaId(headers, data)
    val writerSchema = schemaRegistryClient.client.getSchemaById(writerSchemaId.value)

    readerSchemaDataOpt.map(_.schema.schemaType()).foreach(readerSchemaType => {
      if (readerSchemaType != writerSchema.schemaType())
        throw new MismatchReaderWriterSchemaException(readerSchemaType, writerSchema.schemaType()) //todo: test this case when supporting json schema
    })

    writerSchema match {
      //todo handle JsonSchema
      case schema: AvroSchema =>
        val writerAvroSchema = RuntimeSchemaData(schema.rawSchema(), Some(writerSchemaId.value))
        val readerAvroSchema = readerSchemaDataOpt.asInstanceOf[Option[RuntimeSchemaData[AvroSchema]]]
        avroPayloadDeserializer
          .deserialize(readerAvroSchema, writerAvroSchema, writerSchemaId.buffer, writerSchemaId.bufferStartPosition)
          .asInstanceOf[T]
      case _ => throw new IllegalArgumentException("Not supported schema type")
    }
  }

  private def getSchemaId(headers: Headers, data: Array[Byte]): SchemaIdWithPositionedBuffer =
    headers.getSchemaId(headerName) match {
      case Some(idFromHeader) => // Even if schemaId is passed through header, it still can be serialized in 'Confluent way', here we're figuring it out
        val buffer = Try(readIdAndGetBuffer(data)).map(_._2).getOrElse(ByteBuffer.wrap(data))
        SchemaIdWithPositionedBuffer(idFromHeader, buffer)
      case None => val idAndBuffer = ConfluentUtils.readIdAndGetBuffer(data)
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