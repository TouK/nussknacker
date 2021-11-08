package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.kafka.sharedproducer.WithSharedKafkaProducer
import pl.touk.nussknacker.engine.kafka.{DefaultProducerCreator, KafkaConfig, KafkaProducerCreator, KafkaUtils}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

class KafkaExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer = {
    val kafkaConfig = KafkaConfig.parseConfig(additionalConfig)
    val consumerConfig = additionalConfig.rootAs[KafkaExceptionConsumerConfig]
    val producerCreator = kafkaProducerCreator(kafkaConfig)
    val serialization = KafkaJsonExceptionSerializationSchema(metaData, consumerConfig)
    if (consumerConfig.useSharedProducer) {
      SharedProducerKafkaExceptionConsumer(metaData, serialization, producerCreator)
    } else {
      TempProducerKafkaExceptionConsumer(serialization, producerCreator)
    }
  }

  //visible for testing
  private[exception] def kafkaProducerCreator(kafkaConfig: KafkaConfig): KafkaProducerCreator.Binary = DefaultProducerCreator(kafkaConfig)

  override def name: String = "Kafka"

}

case class TempProducerKafkaExceptionConsumer(serializationSchema: KafkaJsonExceptionSerializationSchema,
                                              kafkaProducerCreator: KafkaProducerCreator.Binary) extends FlinkEspExceptionConsumer {

  override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {
    KafkaUtils.sendToKafkaWithTempProducer(serializationSchema.serialize(exceptionInfo))(kafkaProducerCreator)
  }

}

case class SharedProducerKafkaExceptionConsumer(metaData: MetaData,
                                                serializationSchema: KafkaJsonExceptionSerializationSchema,
                                                kafkaProducerCreator: KafkaProducerCreator.Binary) extends FlinkEspExceptionConsumer with WithSharedKafkaProducer {
  override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {
    sendToKafka(serializationSchema.serialize(exceptionInfo))(SynchronousExecutionContext.ctx)
  }
  
}
