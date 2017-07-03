package pl.touk.esp.engine.kafka

import org.apache.flink.streaming.util.serialization.SerializationSchema
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionConsumer

class KafkaExceptionConsumer(val kafkaConfig: KafkaConfig,
                             topic: String,
                             serializationSchema: SerializationSchema[EspExceptionInfo[NonTransientException]])
  extends FlinkEspExceptionConsumer with EspSimpleKafkaProducer {

  lazy val producer = createProducer()

  def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {
    val toSend = serializationSchema.serialize(exceptionInfo)
    sendToKafka(topic, Array.empty, toSend)(producer)
  }

  override def close(): Unit = {
    producer.close()
  }

}