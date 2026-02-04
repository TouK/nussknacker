package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.scalatest.funsuite.AnyFunSuite

import java.lang

class KafkaUtilsSpec extends AnyFunSuite {
  import KafkaUtilsSpec._

  // This test can be removed when code in Nu will start using KafkaSource
  test("test KafkaSource creation with defaults set by KafkaUtils") {
    val properties = KafkaUtils.toConsumerProperties(
      KafkaComponentsConfig(kafkaProperties = Map("bootstrap.servers" -> "localhost:9200")),
      None
    )

    def createSource(): Unit = {
      KafkaSource
        .builder()
        .setProperties(properties)
        .setTopics("topic")
        .setBootstrapServers("localhost:9200")
        .setDeserializer(new FakeSchema())
        .build()
    }

    createSource()

    // if this part stops throwing an exception we can revert to using class objects
    // - Kafka supports that in its ConfigDef.parseType
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer])
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer])
    assertThrows[NullPointerException](createSource())
  }

  // This test can be removed when code in Nu will start using KafkaSource.
  // It's here to make sure that we would be safe if Flink made KafkaSink's sanity checks as strict as in KafkaSource
  test("test KafkaSink creation with defaults set by KafkaUtils") {
    val properties = KafkaUtils.toProducerProperties(
      KafkaComponentsConfig(kafkaProperties = Map("bootstrap.servers" -> "localhost:9200")),
      clientId = "test",
      includeSerializerClassConfig = false
    )

    def createSink(): Unit = {
      KafkaSink
        .builder()
        .setKafkaProducerConfig(properties)
        .setBootstrapServers("localhost:9200")
        .setRecordSerializer(new FakeSchema())
        .build()
    }

    createSink()
  }

}

object KafkaUtilsSpec {

  private class FakeSchema
      extends KafkaRecordDeserializationSchema[Array[Byte]]
      with KafkaRecordSerializationSchema[Array[Byte]]
      with Serializable {
    override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]], out: Collector[Array[Byte]]): Unit = ???

    override def getProducedType: TypeInformation[Array[Byte]] = ???

    override def serialize(
        element: Array[Byte],
        context: KafkaRecordSerializationSchema.KafkaSinkContext,
        timestamp: lang.Long
    ): ProducerRecord[Array[Byte], Array[Byte]] = ???

  }

}
