package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.common.serialization.SerializationSchema
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer

class KafkaExceptionConsumer(val kafkaConfig: KafkaConfig,
                             topic: String,
                             serializationSchema: SerializationSchema[EspExceptionInfo[NonTransientException]])(implicit metaData: MetaData)
  extends FlinkEspExceptionConsumer {

  private lazy val producer = KafkaUtils.createProducer(kafkaConfig, s"exception-${metaData.id}")

  def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {
    val toSend = serializationSchema.serialize(exceptionInfo)
    KafkaUtils.sendToKafka(topic, Array.empty[Byte], toSend)(producer)
  }

  override def close(): Unit = {
    producer.close()
  }

}
