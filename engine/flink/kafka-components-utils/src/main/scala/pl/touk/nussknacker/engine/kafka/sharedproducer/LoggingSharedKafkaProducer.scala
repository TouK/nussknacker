package pl.touk.nussknacker.engine.kafka.sharedproducer

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.kafka.{KafkaProducerCreator, KafkaUtils}
import pl.touk.nussknacker.engine.util.sharedservice.{SharedService, SharedServiceHolder}

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Use this [[SharedKafkaProducer]] when explicit access to `flush()` and `close()` is required.
 */
final case class LoggingSharedKafkaProducer(creationData: KafkaProducerCreator.Binary,
                                            producer: Producer[Array[Byte], Array[Byte]]) extends SharedService[KafkaProducerCreator.Binary]
  with SharedKafkaProducer with LazyLogging {

  import LoggingSharedKafkaProducer._

  override def sendToKafka(producerRecord: ProducerRecord[Array[Byte], Array[Byte]])(implicit ec: ExecutionContext): Future[Unit] = {
    val promise = Promise[RecordMetadata]()
    ensureValidRecordSize(producerRecord.value())
    producer.send(producerRecord, errorLoggingCallback(producerRecord.topic(), KafkaUtils.producerCallback(promise)))
    promise.future.map(_ => ())
  }

  override protected def sharedServiceHolder: SharedServiceHolder[KafkaProducerCreator.Binary, _] = LoggingSharedKafkaProducerHolder

  override def internalClose(): Unit = {
    producer.close()
  }

  override def flush(): Unit = {
    producer.flush()
  }

  /**
   * Validate that the record size isn't too large
   * This is a copy o KafkaProducer.ensureValidRecordSize with logger instead of throw.
   */
  private def ensureValidRecordSize(value: Array[Byte]): Unit = {
    if (value.length > maxRequestSize) {
      val logValue = new String(value.take(10000), StandardCharsets.UTF_8)
      logger.warn(s"The message is ${value.length} bytes when serialized which is larger than $maxRequestSize: $logValue")
    }
  }

}

object LoggingSharedKafkaProducer extends LazyLogging {

  // Here goes an assumption that messages bigger than 1M are highly suspicious and should not happen in the nature.
  val maxRequestSize: Int = ProducerConfig.configDef()
    .defaultValues()
    .get(ProducerConfig.MAX_REQUEST_SIZE_CONFIG)
    .asInstanceOf[Integer].intValue()

  private def errorLoggingCallback(topic: String, delegate: Callback): Callback = new Callback with Serializable {
    override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
      if (exception != null) {
        logError(topic, exception, Option(metadata))
      }
      delegate.onCompletion(metadata, exception)
    }
  }

  private def logError(topic: String, exception: Exception, metadata: Option[RecordMetadata] = None): Unit = {
    logger.error(s"Sending record to $topic failed", exception)
  }

}

object LoggingSharedKafkaProducerHolder extends SharedServiceHolder[KafkaProducerCreator[Array[Byte], Array[Byte]], LoggingSharedKafkaProducer] {

  override protected def createService(creator: KafkaProducerCreator[Array[Byte], Array[Byte]], metaData: MetaData): LoggingSharedKafkaProducer = {
    val clientId = s"$name-${metaData.id}-${creator.hashCode()}"
    LoggingSharedKafkaProducer(creator, creator.createProducer(clientId))
  }

}
