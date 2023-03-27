package pl.touk.nussknacker.engine.lite.kafka

import com.github.ghik.silencer.silent
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecords, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.{IsolationLevel, Metric, MetricName, TopicPartition}
import pl.touk.nussknacker.engine.kafka.KafkaUtils
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter.KafkaInterpreterConfig
import pl.touk.nussknacker.engine.util.Implicits._

import java.util
import java.util.{Properties, UUID}
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

object KafkaProducerRecordsHandler extends LazyLogging {

  def apply(engineConfig: KafkaInterpreterConfig, producerProperties: Properties, consumerGroupId: String): KafkaProducerRecordsHandler = {
    val shouldProcessRecordsInTransactions = engineConfig.kafkaTransactionsEnabled.getOrElse {
      // Event hubs doesn't support transactional producers for now - it causes error on KafkaProducer.initTransactions()
      // see https://github.com/Azure/azure-event-hubs-for-kafka/issues/209#issuecomment-1412041269
      !engineConfig.kafka.kafkaBootstrapServers.exists(isAzureEventHubsBootstrapServersUrl)
    }
    if (shouldProcessRecordsInTransactions) {
      logger.info("Kafka transactions enabled")
      TransactionalProducerRecordsHandler(producerProperties, consumerGroupId)
    } else {
      logger.info("Kafka transactions disabled")
      val producer = new KafkaProducer[Array[Byte], Array[Byte]](producerProperties)
      new ManuallyCommittingProducerRecordsHandler(producer)
    }
  }

  private def isAzureEventHubsBootstrapServersUrl(url: String) = {
    url.contains(KafkaUtils.azureEventHubsUrl)
  }

}

trait KafkaProducerRecordsHandler extends AutoCloseable {

  protected def producer: KafkaProducer[Array[Byte], Array[Byte]]

  def enrichConsumerProperties(consumerProperties: Properties): Unit

  def beforeRecordsProcessing(): Unit

  def onRecordsSuccessfullyProcessed(records: ConsumerRecords[Array[Byte], Array[Byte]], consumer: KafkaConsumer[_, _]): Unit

  def onRecordsProcessingFailure(): Unit

  final def send(record: ProducerRecord[Array[Byte], Array[Byte]]): Future[RecordMetadata] = {
    KafkaUtils.sendToKafka(record)(producer)
  }

  final def metrics(): util.Map[MetricName, _ <: Metric] =
    producer.metrics()

  override def close(): Unit = {
    producer.close()
  }

}

private class TransactionalProducerRecordsHandler private(protected val producer: KafkaProducer[Array[Byte], Array[Byte]], consumerGroupId: String)
  extends KafkaProducerRecordsHandler {

  override def enrichConsumerProperties(consumerProperties: Properties): Unit = {
    // default is read uncommitted which is not a good default for transactions
    KafkaUtils.setIsolationLevelIfAbsent(consumerProperties, IsolationLevel.READ_COMMITTED)
    // offset commit is done manually in sendOffsetsToTransaction
    consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
  }

  override def beforeRecordsProcessing(): Unit = {
    producer.beginTransaction()
  }

  @silent("deprecated")
  override def onRecordsSuccessfullyProcessed(records: ConsumerRecords[Array[Byte], Array[Byte]], consumer: KafkaConsumer[_, _]): Unit = {
    val offsetsMap: Map[TopicPartition, OffsetAndMetadata] = retrieveMaxOffsetsOffsets(records)
    // group metadata commit API requires brokers to be on version 2.5 or above so for now we use deprecated api
    producer.sendOffsetsToTransaction(offsetsMap.asJava, consumerGroupId)
    producer.commitTransaction()
  }

  override def onRecordsProcessingFailure(): Unit = {
    producer.abortTransaction()
  }

  private def retrieveMaxOffsetsOffsets(records: ConsumerRecords[Array[Byte], Array[Byte]]): Map[TopicPartition, OffsetAndMetadata] = {
    records.iterator().asScala.map { rec =>
      val upcomingOffset = rec.offset() + 1
      (new TopicPartition(rec.topic(), rec.partition()), upcomingOffset)
    }.toList.groupBy(_._1).mapValuesNow(_.map(_._2).max).mapValuesNow(new OffsetAndMetadata(_))
  }

}

private object TransactionalProducerRecordsHandler {

  def apply(producerProperties: Properties, consumerGroupId: String): TransactionalProducerRecordsHandler = {
    //FIXME generate correct id - how to connect to topic/partition??
    producerProperties.put("transactional.id", consumerGroupId + UUID.randomUUID().toString)
    val producer = new KafkaProducer[Array[Byte], Array[Byte]](producerProperties)
    producer.initTransactions()
    new TransactionalProducerRecordsHandler(producer, consumerGroupId)
  }

}

private class ManuallyCommittingProducerRecordsHandler(protected val producer: KafkaProducer[Array[Byte], Array[Byte]]) extends KafkaProducerRecordsHandler {

  override def enrichConsumerProperties(consumerProperties: Properties): Unit = {
    consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
  }

  override def beforeRecordsProcessing(): Unit = {}

  override def onRecordsSuccessfullyProcessed(records: ConsumerRecords[Array[Byte], Array[Byte]], consumer: KafkaConsumer[_, _]): Unit = {
    consumer.commitSync()
  }

  override def onRecordsProcessingFailure(): Unit = {}

}


