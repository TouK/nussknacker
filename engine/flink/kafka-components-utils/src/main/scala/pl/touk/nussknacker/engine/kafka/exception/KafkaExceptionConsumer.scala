package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.sharedproducer.WithSharedKafkaProducer
import pl.touk.nussknacker.engine.kafka.{DefaultProducerCreator, KafkaConfig, KafkaProducerCreator, KafkaUtils}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

class KafkaExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer = {
    val kafkaConfig           = KafkaConfig.parseConfig(exceptionHandlerConfig)
    val consumerConfig        = exceptionHandlerConfig.as[KafkaExceptionConsumerConfig]
    val producerCreator       = kafkaProducerCreator(kafkaConfig)
    val serializationSchema   = createSerializationSchema(metaData, consumerConfig)
    val errorTopicInitializer = new KafkaErrorTopicInitializer(kafkaConfig, consumerConfig)
    if (consumerConfig.useSharedProducer) {
      SharedProducerKafkaExceptionConsumer(metaData, serializationSchema, producerCreator, errorTopicInitializer)
    } else {
      TempProducerKafkaExceptionConsumer(serializationSchema, producerCreator, errorTopicInitializer)
    }
  }

  protected def createSerializationSchema(
      metaData: MetaData,
      consumerConfig: KafkaExceptionConsumerConfig
  ): KafkaSerializationSchema[NuExceptionInfo[NonTransientException]] =
    new KafkaJsonExceptionSerializationSchema(metaData, consumerConfig)

  // visible for testing
  private[exception] def kafkaProducerCreator(kafkaConfig: KafkaConfig): KafkaProducerCreator.Binary =
    DefaultProducerCreator(kafkaConfig)

  override def name: String = "Kafka"

}

case class TempProducerKafkaExceptionConsumer(
    serializationSchema: KafkaSerializationSchema[NuExceptionInfo[NonTransientException]],
    kafkaProducerCreator: KafkaProducerCreator.Binary,
    kafkaErrorTopicInitializer: KafkaErrorTopicInitializer
) extends FlinkEspExceptionConsumer {

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    kafkaErrorTopicInitializer.init()
  }

  override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit = {
    KafkaUtils.sendToKafkaWithTempProducer(serializationSchema.serialize(exceptionInfo, System.currentTimeMillis()))(
      kafkaProducerCreator
    )
  }

}

case class SharedProducerKafkaExceptionConsumer(
    metaData: MetaData,
    serializationSchema: KafkaSerializationSchema[NuExceptionInfo[NonTransientException]],
    kafkaProducerCreator: KafkaProducerCreator.Binary,
    kafkaErrorTopicInitializer: KafkaErrorTopicInitializer
) extends FlinkEspExceptionConsumer
    with WithSharedKafkaProducer {

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    kafkaErrorTopicInitializer.init()
  }

  override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit = {
    sendToKafka(serializationSchema.serialize(exceptionInfo, System.currentTimeMillis()))(
      SynchronousExecutionContextAndIORuntime.syncEc
    )
  }

}
