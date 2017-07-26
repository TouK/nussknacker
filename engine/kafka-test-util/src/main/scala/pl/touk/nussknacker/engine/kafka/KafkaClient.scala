package pl.touk.nussknacker.engine.kafka

import java.util.Properties

import kafka.admin.AdminUtils
import kafka.consumer.ConsumerConnector
import kafka.utils.ZkUtils
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class KafkaClient(kafkaAddress: String, zkAddress: String) {
  val producer = createKafkaProducer[String, String]

  val consumers = collection.mutable.HashSet[ConsumerConnector]()

  val zkClient = ZkUtils.createZkClient(zkAddress, 10000, 10000)
  val zkUtils = ZkUtils(zkClient, isZkSecurityEnabled = false)

  private def createKafkaProducer[T, K]: KafkaProducer[T, K] = {
    KafkaUtils.createKafkaProducer(kafkaAddress)
  }

  def createTopic(name: String, partitions: Int = 5) = {
    AdminUtils.createTopic(zkUtils, name, partitions, 1, new Properties())
    val replicaAssignment = (0 until partitions).map(_ -> Seq(0)).toMap
    AdminUtils.createOrUpdateTopicPartitionAssignmentPathInZK(zkUtils, name, replicaAssignment, new Properties, update = true)
  }

  def deleteTopic(name: String) = {
    AdminUtils.deleteTopic(zkUtils, name)
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

  def sendMessage(topic: String, content: String, callback: Callback) = {
    producer.send(new ProducerRecord[String, String](topic, content), callback)
  }

  private def producerCallback(promise: Promise[RecordMetadata]): Callback =
    producerCallback(result => promise.complete(result))

  private def producerCallback(callback: Try[RecordMetadata] => Unit): Callback = {
    new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        val result =
          if (exception == null) Success(metadata)
          else Failure(exception)
        callback(result)
      }
    }
  }

  def flush() = {
    producer.flush()
  }

  def shutdown() = {
    consumers.foreach(_.shutdown())
    producer.close()
    zkUtils.close()
    zkClient.close()
  }

  def createConsumer(consumerTimeout: Long = 10000): ConsumerConnector = {
    val consumer = KafkaUtils.createConsumerConnector(zkAddress, consumerTimeout)
    consumers.add(consumer)
    consumer
  }

}
