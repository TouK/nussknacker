package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils.MagicByte
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentAvroPayloadDeserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde.SchemaIdHeaderName
import pl.touk.nussknacker.engine.avro.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import java.nio.ByteBuffer
import scala.reflect.ClassTag
import scala.util.Try

object ConfluentUniversalKafkaSerde {
  val SchemaIdHeaderName = "schema_id"
}

class ConfluentUniversalKafkaDeserializer[T](
                                schemaRegistryClient: ConfluentSchemaRegistryClient,
                                readerSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                isKey: Boolean
                              ) extends Deserializer[T] {
  private case class WithShiftingRequiredInfo[A](get: A, shouldShiftMagicByte: Boolean) {
    def map[B](fn: A => B): WithShiftingRequiredInfo[B] = WithShiftingRequiredInfo(fn(get), shouldShiftMagicByte)
    def bufferStartPosition: Int = if(shouldShiftMagicByte) 1 + ConfluentUtils.IdSize else 1
  }

  override def deserialize(topic: String, data: Array[Byte]): T = {
    throw new IllegalAccessException()
  }

  private lazy val avroPayloadDeserializer = new ConfluentAvroPayloadDeserializer(false, false, false, DecoderFactory.get())

  override def deserialize(topic: String, headers: Headers, data: Array[Byte]): T = {
    val writerSchema = getParsedSchema(headers, data)
    readerSchemaDataOpt.map(_.schema.schemaType()).foreach(readerSchemaType => {
      if(readerSchemaType != writerSchema.get.schemaType())
        throw new IllegalArgumentException(s"Expecting schema of type $readerSchemaType. but got payload with ${writerSchema.get.schemaType()} schema type")
    })

    writerSchema.get match {
      //todo handle JsonSchema
      case schema: AvroSchema =>
        val writerAvroSchema = RuntimeSchemaData(schema.rawSchema(), Some(schema.version().toInt))
        val readerAvroSchema = readerSchemaDataOpt.asInstanceOf[Option[RuntimeSchemaData[AvroSchema]]]
        avroPayloadDeserializer.deserialize(readerAvroSchema, writerAvroSchema, ByteBuffer.wrap(data), writerSchema.bufferStartPosition).asInstanceOf[T]
      case _ => throw new IllegalArgumentException("Not supported schema type")
    }

  }

  private def getParsedSchema(headers: Headers, data: Array[Byte]): WithShiftingRequiredInfo[ParsedSchema] = {
    val schemaId = Option(headers.lastHeader(SchemaIdHeaderName)) match {
      case Some(header) =>
        val id = Try(new String(header.value()).toInt)
          .fold(e => throw new IllegalArgumentException(s"Got header $SchemaIdHeaderName, but the value is corrupted.", e), x => x)
        WithShiftingRequiredInfo(id, ByteBuffer.wrap(data).get() == MagicByte)

      case None => WithShiftingRequiredInfo(ConfluentUtils.readId(data), shouldShiftMagicByte = true)
    }
    schemaId.map(schemaRegistryClient.client.getSchemaById)
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