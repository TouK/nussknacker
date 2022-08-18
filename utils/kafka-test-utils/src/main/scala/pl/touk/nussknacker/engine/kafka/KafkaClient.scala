package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{NewTopic, TopicDescription}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.header.Headers

import java.time.Duration
import java.util
import java.util.{Collections, UUID}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class KafkaClient(kafkaAddress: String, id: String) extends LazyLogging {
  val rawProducer: KafkaProducer[Array[Byte], Array[Byte]] = KafkaTestUtils.createRawKafkaProducer(kafkaAddress, id + "_raw")
  val producer: KafkaProducer[String, String] = KafkaTestUtils.createKafkaProducer(kafkaAddress, id)

  private val consumers = collection.mutable.HashSet[KafkaConsumer[Array[Byte], Array[Byte]]]()

  private lazy val adminClient = KafkaUtils.createKafkaAdminClient(KafkaConfig(kafkaAddress, None, None))

  def createTopic(name: String, partitions: Int = 5): Unit = {
    adminClient.createTopics(Collections.singletonList(new NewTopic(name, partitions, 1: Short))).all().get()
  }

  def deleteTopic(name: String): Unit = {
    adminClient.deleteTopics(util.Arrays.asList(name)).all().get()
  }

  def topic(name: String): Option[TopicDescription] = Try(adminClient.describeTopics(util.Arrays.asList(name)).all().get()).toOption.map(_.get(name))

  def sendRawMessage(topic: String, key: Array[Byte], content: Array[Byte], partition: Option[Int] = None, timestamp: java.lang.Long = null, headers: Headers = ConsumerRecordUtils.emptyHeaders): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val record = partition.map(new ProducerRecord[Array[Byte], Array[Byte]](topic, _, timestamp, key, content, headers))
      .getOrElse(new ProducerRecord[Array[Byte], Array[Byte]](topic, null, timestamp, key, content, headers))
    rawProducer.send(record, producerCallback(promise))
    promise.future
  }

  def sendMessage(topic: String, key: String, content: String, partition: Option[Int] = None): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val record = partition.map(new ProducerRecord[String, String](topic, _, key, content)).getOrElse(new ProducerRecord[String, String](topic, key, content))
    producer.send(record, producerCallback(promise))
    promise.future
  }

  def sendMessage(topic: String, content: String): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    producer.send(new ProducerRecord[String, String](topic, content), producerCallback(promise))
    promise.future
  }

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

  def flush(): Unit = {
    producer.flush()
  }

  def shutdown(): Unit = {
    closeConsumers()
    producer.close()
    rawProducer.close()
    adminClient.close()
  }

  def createConsumer(groupIdOpt: Option[String] = None): KafkaConsumer[Array[Byte], Array[Byte]] = synchronized {
    // each consumer by default is in other consumer group to make sure that messages won't be stolen by other consumer consuming the same topic
    val groupId = groupIdOpt.getOrElse(s"testGroup_${UUID.randomUUID()}")
    val props = KafkaTestUtils.createConsumerConnectorProperties(kafkaAddress, groupId)
    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](props)
    consumers.add(consumer)
    consumer
  }

  def closeConsumers(): Unit = synchronized {
    // by default this close can hold the tests for up to 30 seconds
    consumers.foreach(_.close(Duration.ofSeconds(1)))
    consumers.clear()
  }

}
