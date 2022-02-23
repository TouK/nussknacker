package pl.touk.nussknacker.engine.kafka.sharedproducer

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Lifecycle, MetaData}
import pl.touk.nussknacker.engine.kafka.{KafkaProducerCreator, KafkaUtils}
import pl.touk.nussknacker.engine.util.sharedservice.{SharedService, SharedServiceHolder}

import scala.concurrent.{ExecutionContext, Future}

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

  override def internalClose(): Unit = {
    producer.close()
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


  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    sharedProducer = SharedKafkaProducerHolder.retrieveService(kafkaProducerCreator)(context.jobData.metaData)
  }

  override def close(): Unit = {
    super.close()
    closeSharedProducer()
  }

  protected def closeSharedProducer(): Unit = {
    super.close()
    if (sharedProducer != null) {
      sharedProducer.close()
    }
  }
}

