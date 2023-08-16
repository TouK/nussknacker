package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Encoder}
import org.apache.kafka.clients.admin.{NewTopic, TopicDescription}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.api.CirceUtil

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util
import java.util.{Collections, UUID}
import scala.concurrent.{Future, Promise}
import scala.reflect.{classTag, ClassTag}
import scala.util.{Failure, Success, Try}

object KafkaClient {
  implicit class RichScalaMap(record: ConsumerRecord[Array[Byte], Array[Byte]]) {
    def keyAsStr: String =
      strOrNull(record.key())

    def valueAs[T: Decoder : ClassTag]: T = {
      val clazz = classTag[T].runtimeClass

      if (classOf[String].isAssignableFrom(clazz)) {
        strOrNull(record.value()).asInstanceOf[T]
      } else {
        CirceUtil.decodeJsonUnsafe[T](record.value())
      }
    }

    private def strOrNull(data: Array[Byte]): String =
      Option(data).map(value => new String(value, StandardCharsets.UTF_8)).orNull
  }
}

class KafkaClient(kafkaAddress: String, id: String) extends LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils._

  import scala.jdk.CollectionConverters._

  import KafkaClient._

  private val rawProducer: KafkaProducer[Array[Byte], Array[Byte]] = KafkaTestUtils.createRawKafkaProducer(kafkaAddress, id + "_raw")

  private val producer: KafkaProducer[String, String] = KafkaTestUtils.createKafkaProducer(kafkaAddress, id)

  private val consumers = collection.mutable.Map[String, KafkaConsumer[Array[Byte], Array[Byte]]]()

  private lazy val adminClient = KafkaUtils.createKafkaAdminClient(KafkaConfig(Some(Map("bootstrap.servers" -> kafkaAddress)), None))

  def createTopic(name: String, partitions: Int = 5): Unit =
    adminClient.createTopics(Collections.singletonList(new NewTopic(name, partitions, 1: Short))).all().get()

  def deleteTopic(name: String): Unit =
    adminClient.deleteTopics(util.Arrays.asList(name)).all().get()

  def topic(name: String): Option[TopicDescription] =
    Try(adminClient.describeTopics(util.Arrays.asList(name)).allTopicNames().get()).toOption.map(_.get(name))

  def sendRawMessage(topic: String, content: Array[Byte]): Future[RecordMetadata] =
    sendRawMessage(topic, null, content)

  def sendRawMessage(topic: String, key: Array[Byte], content: Array[Byte], partition: Option[Int] = None, timestamp: java.lang.Long = null, headers: Headers = KafkaRecordUtils.emptyHeaders): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val record = createRecord(topic, key, content, partition, timestamp, headers)
    rawProducer.send(record, producerCallback(promise))
    promise.future
  }

  def sendMessage[T: Encoder](topic: String, content: T): Future[RecordMetadata] =
    sendMessage(topic, null, content)

  def sendMessage[T: Encoder](topic: String, key: String, content: T, partition: Option[Int] = None, timestamp: java.lang.Long = null, headers: Headers = KafkaRecordUtils.emptyHeaders): Future[RecordMetadata] = {
    val strContent = content match {
      case str: String => str
      case _ => implicitly[Encoder[T]].apply(content).noSpaces
    }

    val promise = Promise[RecordMetadata]()
    val record = createRecord(topic, key, strContent, partition, timestamp, headers)
    producer.send(record, producerCallback(promise))
    promise.future
  }

  def consumeMessages[V: Decoder : ClassTag](topic: String, count: Int, shouldSeekToBeginning: Boolean = false): List[KeyMessage[String, V]] =
    consumeRawMessages(topic, count, shouldSeekToBeginning).map { record =>
      KeyMessage(record.keyAsStr, record.valueAs[V], record.timestamp)
    }

  def consumeRawMessages(topic: String, count: Int, shouldSeekToBeginning: Boolean = false): List[ConsumerRecord[Array[Byte], Array[Byte]]] = {
    (consumers.get(topic) match {
      case Some(consumer) =>
        if (shouldSeekToBeginning) {
          seekToBeginning(topic, consumer)
        }
        consumer
      case None =>
        createConsumer(topic)
    }).consumeWithConsumerRecord(topic)
      .take(count)
      .toList
  }

  private def createConsumer(topic: String, groupIdOpt: Option[String] = None): KafkaConsumer[Array[Byte], Array[Byte]] = synchronized {
    // each consumer by default is in other consumer group to make sure that messages won't be stolen by other consumer consuming the same topic
    val groupId = groupIdOpt.getOrElse(s"testGroup_${UUID.randomUUID()}")
    val props = KafkaTestUtils.createConsumerConnectorProperties(kafkaAddress, groupId)
    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](props)
    consumers += topic -> consumer
    consumer
  }

  private def createRecord[K, V](topic: String, key: K, content: V, partition: Option[Int] = None, timestamp: java.lang.Long = null, headers: Headers = KafkaRecordUtils.emptyHeaders) =
    partition.map(new ProducerRecord[K, V](topic, _, timestamp, key, content, headers)).getOrElse(
      new ProducerRecord[K, V](topic, null, timestamp, key, content, headers)
    )

  private def producerCallback(promise: Promise[RecordMetadata]): Callback =
    producerCallback(result => promise.complete(result))

  private def producerCallback(callback: Try[RecordMetadata] => Unit): Callback = (metadata: RecordMetadata, exception: Exception) => {
    val result =
      if (exception == null) {
        Success(metadata)
      } else {
        logger.error("Error while sending kafka message", exception)
        Failure(exception)
      }
    callback(result)
  }

  private def seekToBeginning(topic: String, consumer: KafkaConsumer[Array[Byte], Array[Byte]]): Unit = {
    val partitionsInfo = consumer.listTopics().asScala.getOrElse(topic, throw new IllegalStateException(s"Topic '$topic' does not exist"))
    val topicPartitions = partitionsInfo.asScala.map(no => new TopicPartition(topic, no.partition())).asJava
    consumer.seekToBeginning(topicPartitions)
  }

  def flush(): Unit = {
    producer.flush()
  }

  def shutdown(): Unit = {
    closeConsumers()
    producer.close()
    rawProducer.close()
    adminClient.close()
  }

  def closeConsumers(): Unit = synchronized {
    // by default this close can hold the tests for up to 30 seconds
    consumers.values.foreach(_.close(Duration.ofSeconds(1)))
    consumers.clear()
  }

}
