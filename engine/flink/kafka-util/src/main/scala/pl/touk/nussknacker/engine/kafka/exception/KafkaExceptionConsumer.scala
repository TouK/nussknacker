package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.sharedproducer.WithSharedKafkaProducer
import pl.touk.nussknacker.engine.kafka.{DefaultProducerCreator, KafkaConfig, KafkaProducerCreator, KafkaUtils}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

class KafkaExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer = {
    val kafkaConfig = KafkaConfig.parseConfig(additionalConfig)
    val consumerConfig = additionalConfig.rootAs[KafkaExceptionConsumerConfig]
    val producerCreator = kafkaProducerCreator(kafkaConfig)
    val serializationSchema = createSerializationSchema(metaData, consumerConfig)
    if (consumerConfig.useSharedProducer) {
      SharedProducerKafkaExceptionConsumer(metaData, serializationSchema, producerCreator)
    } else {
      TempProducerKafkaExceptionConsumer(serializationSchema, producerCreator)
    }
  }

  protected def createSerializationSchema(metaData: MetaData, consumerConfig: KafkaExceptionConsumerConfig): KafkaSerializationSchema[NuExceptionInfo[NonTransientException]] =
    new KafkaJsonExceptionSerializationSchema(metaData, consumerConfig)

  //visible for testing
  private[exception] def kafkaProducerCreator(kafkaConfig: KafkaConfig): KafkaProducerCreator.Binary = DefaultProducerCreator(kafkaConfig)

  override def name: String = "Kafka"

}

case class TempProducerKafkaExceptionConsumer(serializationSchema: KafkaSerializationSchema[NuExceptionInfo[NonTransientException]],
                                              kafkaProducerCreator: KafkaProducerCreator.Binary) extends FlinkEspExceptionConsumer {

  override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit = {
    KafkaUtils.sendToKafkaWithTempProducer(serializationSchema.serialize(exceptionInfo, System.currentTimeMillis()))(kafkaProducerCreator)
  }

}

case class SharedProducerKafkaExceptionConsumer(metaData: MetaData,
                                                serializationSchema: KafkaSerializationSchema[NuExceptionInfo[NonTransientException]],
                                                kafkaProducerCreator: KafkaProducerCreator.Binary) extends FlinkEspExceptionConsumer with WithSharedKafkaProducer {
  override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit = {
    sendToKafka(serializationSchema.serialize(exceptionInfo, System.currentTimeMillis()))(SynchronousExecutionContext.ctx)
  }
  
}
