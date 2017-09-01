package pl.touk.nussknacker.engine.kafka

import org.apache.flink.streaming.util.serialization.SerializationSchema
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer

class KafkaExceptionConsumer(val kafkaConfig: KafkaConfig,
                             topic: String,
                             serializationSchema: SerializationSchema[EspExceptionInfo[NonTransientException]])
  extends FlinkEspExceptionConsumer {

  lazy val producer = KafkaEspUtils.createProducer(kafkaConfig)

  def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {
    val toSend = serializationSchema.serialize(exceptionInfo)
    KafkaEspUtils.sendToKafka(topic, Array.empty[Byte], toSend)(producer)
  }

  override def close(): Unit = {
    producer.close()
  }

}