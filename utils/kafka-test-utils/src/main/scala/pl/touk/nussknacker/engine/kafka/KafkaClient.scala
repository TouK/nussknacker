package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import odelay.Timer
import org.apache.kafka.clients.admin.{NewTopic, TopicDescription}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.header.Headers
import retry.When

import java.time.Duration
import java.util
import java.util.{Collections, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

class KafkaClient(kafkaAddress: String, id: String) extends LazyLogging {

  private val rawProducer: KafkaProducer[Array[Byte], Array[Byte]] =
    KafkaTestUtils.createRawKafkaProducer(kafkaAddress, id + "_raw")

  private val producer: KafkaProducer[String, String] = KafkaTestUtils.createKafkaProducer(kafkaAddress, id)

  private val consumers = collection.mutable.HashSet[KafkaConsumer[Array[Byte], Array[Byte]]]()

  private lazy val adminClient =
    KafkaUtils.createKafkaAdminClient(KafkaConfig(Some(Map("bootstrap.servers" -> kafkaAddress)), None))

  def createTopic(name: String, partitions: Int = 5): Unit = {
    adminClient.createTopics(Collections.singletonList(new NewTopic(name, partitions, 1: Short))).all().get()
    // When kraft enabled, topics doesn't appear instantly after createTopic
    retry.Pause(10, 1.second)(Timer.default)(
      Future {
        topic(name)
      }
    )

  }

  def deleteTopic(name: String): Unit =
    adminClient.deleteTopics(util.Arrays.asList(name)).all().get()

  def topic(name: String): Option[TopicDescription] =
    Try(adminClient.describeTopics(util.Arrays.asList(name)).allTopicNames().get()).toOption.map(_.get(name))

  def sendRawMessage(topic: String, content: Array[Byte]): Future[RecordMetadata] =
    sendRawMessage(topic, null, content)

  def sendRawMessage(
      topic: String,
      key: Array[Byte],
      content: Array[Byte],
      partition: Option[Int] = None,
      timestamp: java.lang.Long = null,
      headers: Headers = KafkaRecordUtils.emptyHeaders
  ): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val record  = createRecord(topic, key, content, partition, timestamp, headers)
    rawProducer.send(record, producerCallback(promise))
    promise.future
  }

  def sendMessage[T: Encoder](topic: String, content: T): Future[RecordMetadata] =
    sendMessage(topic, null, content)

  def sendMessage[T: Encoder](
      topic: String,
      key: String,
      content: T,
      partition: Option[Int] = None,
      timestamp: java.lang.Long = null,
      headers: Headers = KafkaRecordUtils.emptyHeaders
  ): Future[RecordMetadata] = {
    val strContent = ConsumerRecordHelper.asString(content)
    val promise    = Promise[RecordMetadata]()
    val record     = createRecord(topic, key, strContent, partition, timestamp, headers)
    producer.send(record, producerCallback(promise))
    promise.future
  }

  def createConsumer(groupIdOpt: Option[String] = None): KafkaConsumer[Array[Byte], Array[Byte]] = synchronized {
    // each consumer by default is in other consumer group to make sure that messages won't be stolen by other consumer consuming the same topic
    val groupId  = groupIdOpt.getOrElse(s"$id-${UUID.randomUUID()}")
    val props    = KafkaTestUtils.createConsumerConnectorProperties(kafkaAddress, groupId)
    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](props)
    consumers.add(consumer)
    consumer
  }

  private def createRecord[K, V](
      topic: String,
      key: K,
      content: V,
      partition: Option[Int] = None,
      timestamp: java.lang.Long = null,
      headers: Headers = KafkaRecordUtils.emptyHeaders
  ) =
    partition
      .map(new ProducerRecord[K, V](topic, _, timestamp, key, content, headers))
      .getOrElse(
        new ProducerRecord[K, V](topic, null, timestamp, key, content, headers)
      )

  private def producerCallback(promise: Promise[RecordMetadata]): Callback =
    producerCallback(result => promise.complete(result))

  private def producerCallback(callback: Try[RecordMetadata] => Unit): Callback =
    (metadata: RecordMetadata, exception: Exception) => {
      val result =
        if (exception == null) {
          Success(metadata)
        } else {
          logger.error("Error while sending kafka message", exception)
          Failure(exception)
        }
      callback(result)
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
    consumers.foreach(_.close(Duration.ofSeconds(1)))
    consumers.clear()
  }

}
