package pl.touk.nussknacker.engine.kafka.sharedproducer

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{Callback, Producer, ProducerRecord, RecordMetadata}
import pl.touk.nussknacker.engine.api.{JobData, Lifecycle, MetaData}
import pl.touk.nussknacker.engine.flink.util.sharedservice.{SharedService, SharedServiceHolder}
import pl.touk.nussknacker.engine.kafka.{KafkaProducerCreator, KafkaUtils}

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/*
  In certain cases (e.g. exception handling) we want to use single Kafka producer for all tasks and operators, to avoid
  create excessive number of KafkaProducer threads. Please note that in normal cases (sinks etc.) it's better to use
  Flink Kafka connector. In the future probably also exception handling would be refactored to use Flink connectors (and e.g.
  benefit from exactly-once guarantees etc.)
 */
object SharedKafkaProducerHolder extends SharedServiceHolder[KafkaProducerCreator[Array[Byte], Array[Byte]], DefaultSharedKafkaProducer] {

  override protected def createService(creator: KafkaProducerCreator[Array[Byte], Array[Byte]], metaData: MetaData): DefaultSharedKafkaProducer = {
    val clientId = s"$name-${metaData.id}-${creator.hashCode()}"
    DefaultSharedKafkaProducer(creator, creator.createProducer(clientId))
  }

}

final case class DefaultSharedKafkaProducer(creationData: KafkaProducerCreator.Binary,
                                            producer: Producer[Array[Byte], Array[Byte]]) extends SharedService[KafkaProducerCreator.Binary] with SharedKafkaProducer with LazyLogging {

  override def sendToKafka(producerRecord: ProducerRecord[Array[Byte], Array[Byte]])(implicit ec: ExecutionContext): Future[Unit] = {
    KafkaUtils.sendToKafka(producerRecord)(producer).map(_ => ())
  }

  override protected def sharedServiceHolder: SharedServiceHolder[KafkaProducerCreator.Binary, _] = SharedKafkaProducerHolder

  // See FlinkKafkaProducer.close. Producer is closed again in case of an exception.
  override def internalClose(): Unit = {
    try {
      logger.debug("Flushing producer")
      producer.flush()
      logger.debug("Closing producer")
      producer.close(Duration.ZERO)
    } catch {
      case NonFatal(e) =>
        logger.error("Exception while flushing or closing", e)
        try {
          logger.warn("Closing producer again")
          producer.close(Duration.ZERO)
        } catch {
          case NonFatal(e) =>
            logger.error("Exception while closing producer second time", e)
        }
    }
  }

  private class CallbackWithFailureLogging(promise: Promise[Unit]) extends Callback {
    override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
      if (exception != null) {
        //can be e.g. redirected to specific logger
        logger.error(s"Sending record $metadata failed", exception)
        promise.complete(Failure(exception))
      } else {
        promise.complete(Success(()))
      }
    }
  }
}

trait SharedKafkaProducer {

  def close(): Unit

  def sendToKafka(producerRecord: ProducerRecord[Array[Byte], Array[Byte]])(implicit ec: ExecutionContext): Future[Unit]

}

trait WithSharedKafkaProducer extends Lifecycle {

  def kafkaProducerCreator: KafkaProducerCreator.Binary

  private var sharedProducer: SharedKafkaProducer = _

  def sendToKafka(producerRecord: ProducerRecord[Array[Byte], Array[Byte]])
                 (implicit ec: ExecutionContext): Future[Unit] = {
    sharedProducer.sendToKafka(producerRecord)
  }

  override def open(jobData: JobData): Unit = {
    sharedProducer = SharedKafkaProducerHolder.retrieveService(kafkaProducerCreator)(jobData.metaData)
  }

  override def close(): Unit = {
    closeSharedProducer()
  }

  protected def closeSharedProducer(): Unit = {
    super.close()
    if (sharedProducer != null) {
      sharedProducer.close()
    }
  }
}

