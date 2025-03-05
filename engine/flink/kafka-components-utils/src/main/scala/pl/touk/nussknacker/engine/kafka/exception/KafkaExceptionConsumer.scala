package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.RecordTooLargeException
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.kafka.{DefaultProducerCreator, KafkaConfig, KafkaProducerCreator, KafkaUtils}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.sharedproducer.WithSharedKafkaProducer
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

import scala.concurrent.{ExecutionContext, Future}

class KafkaExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer = {
    val kafkaConfig           = KafkaConfig.parseConfig(exceptionHandlerConfig)
    val consumerConfig        = exceptionHandlerConfig.rootAs[KafkaExceptionConsumerConfig]
    val producerCreator       = kafkaProducerCreator(kafkaConfig)
    val serializationSchema   = createSerializationSchema(metaData, consumerConfig)
    val errorTopicInitializer = new DefaultKafkaErrorTopicInitializer(kafkaConfig, consumerConfig)
    if (consumerConfig.useSharedProducer) {
      SharedProducerKafkaExceptionConsumer(metaData, serializationSchema, producerCreator, errorTopicInitializer)
    } else {
      TempProducerKafkaExceptionConsumer(metaData, serializationSchema, producerCreator, errorTopicInitializer)
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

trait BaseKafkaExceptionConsumer extends FlinkEspExceptionConsumer with LazyLogging {
  protected val serializationSchema: KafkaSerializationSchema[NuExceptionInfo[NonTransientException]]
  protected val kafkaErrorTopicInitializer: KafkaErrorTopicInitializer
  protected val metaData: MetaData

  private val topic: String = kafkaErrorTopicInitializer.topicName

  protected def sendKafkaMessage(record: ProducerRecord[Array[Byte], Array[Byte]]): Future[_]

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    kafkaErrorTopicInitializer.init()
  }

  override final def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit = {
    sendKafkaMessage(serializationSchema.serialize(exceptionInfo, System.currentTimeMillis()))
      .recoverWith { case e: RecordTooLargeException =>
        // Kafka message size should be kept within acceptable size by serializer, but it may be incorrectly configured.
        // We may also encounter this exception due to producer's 'buffer.memory' being set too low.
        //
        // Try to reduce Kafka message size in hope that it will fit configured limits. It's practically impossible
        // to correctly detect and handle this limit preemptively, because:
        // * Kafka limits are imposed on compressed message sizes (with headers and protocol overhead)
        // * there are limits on multiple levels: producer, topic, broker (access requires additional permissions)

        val scenario = metaData.id
        val node     = exceptionInfo.nodeComponentInfo.map(_.nodeId).getOrElse("-")
        val error    = exceptionInfo.throwable.message
        logger.warn(
          s"Cannot write to $topic, retrying with stripped context (scenario: $scenario, node: $node, error: $error)." +
            s"Verify your configuration of Kafka producer, error logging and errors topic and set correct limits. ${e.getMessage}"
        )

        val lightExceptionInfo = exceptionInfo.copy(
          context = exceptionInfo.context.copy(
            variables = Map(
              "!warning" -> s"variables truncated, they didn't fit within max allowed size of a Kafka message: ${e.getMessage}",
            ),
            parentContext = None
          )
        )

        sendKafkaMessage(serializationSchema.serialize(lightExceptionInfo, System.currentTimeMillis()))
      }(ExecutionContext.Implicits.global)
      .recover { case e: Throwable =>
        val scenario = metaData.id
        val node     = exceptionInfo.nodeComponentInfo.map(_.nodeId).getOrElse("-")
        val error    = exceptionInfo.throwable.message

        logger.warn(
          s"Failed to write to $topic (scenario: $scenario, node: $node, error: $error): ${e.getMessage}",
          e
        )
      }(ExecutionContext.Implicits.global)
  }

}

case class TempProducerKafkaExceptionConsumer(
    metaData: MetaData,
    serializationSchema: KafkaSerializationSchema[NuExceptionInfo[NonTransientException]],
    kafkaProducerCreator: KafkaProducerCreator.Binary,
    kafkaErrorTopicInitializer: KafkaErrorTopicInitializer
) extends BaseKafkaExceptionConsumer {

  override protected def sendKafkaMessage(record: ProducerRecord[Array[Byte], Array[Byte]]): Future[_] = {
    KafkaUtils
      .sendToKafkaWithTempProducer(record)(kafkaProducerCreator)
  }

}

case class SharedProducerKafkaExceptionConsumer(
    metaData: MetaData,
    serializationSchema: KafkaSerializationSchema[NuExceptionInfo[NonTransientException]],
    kafkaProducerCreator: KafkaProducerCreator.Binary,
    kafkaErrorTopicInitializer: KafkaErrorTopicInitializer
) extends BaseKafkaExceptionConsumer
    with WithSharedKafkaProducer {

  override protected def sendKafkaMessage(record: ProducerRecord[Array[Byte], Array[Byte]]): Future[_] = {
    sendToKafka(record)(SynchronousExecutionContextAndIORuntime.syncEc)
  }

}
