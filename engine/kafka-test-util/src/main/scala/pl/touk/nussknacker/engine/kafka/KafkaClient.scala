package pl.touk.nussknacker.engine.kafka

import java.util.Properties
import java.util.concurrent.TimeUnit

import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import kafka.zk.{AdminZkClient, KafkaZkClient}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.utils.Time

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class KafkaClient(kafkaAddress: String, zkAddress: String, id: String) {
  val rawProducer = KafkaUtils.createRawKafkaProducer(kafkaAddress, id + "_raw")
  val producer = KafkaUtils.createKafkaProducer(kafkaAddress, id)

  private val consumers = collection.mutable.HashSet[KafkaConsumer[Array[Byte], Array[Byte]]]()

  private val zkClient = KafkaZkClient(zkAddress, isSecure = false,
    sessionTimeoutMs = 10000, connectionTimeoutMs = 10000, maxInFlightRequests = 100, time = Time.SYSTEM, metricGroup = "", metricType = "")
  private val adminClient = new AdminZkClient(zkClient)

  def createTopic(name: String, partitions: Int = 5) = {
    //adminClient.createTopic(name, partitions, 1, new Properties())
    val replicaAssignment = (0 until partitions).map(_ -> Seq(0)).toMap
    adminClient.createTopicWithAssignment(name, new Properties, replicaAssignment)
  }

  def deleteTopic(name: String) = {
    adminClient.deleteTopic(name)
  }

  def sendRawMessage(topic: String, key: Array[Byte], content: Array[Byte], partition: Option[Int] = None): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val record = partition.map(new ProducerRecord[Array[Byte], Array[Byte]](topic, _, key, content))
      .getOrElse(new ProducerRecord[Array[Byte], Array[Byte]](topic, key, content))
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
    consumers.foreach(_.close())
    producer.close()
    rawProducer.close()
    zkClient.close()
  }

  def createConsumer(consumerTimeout: Long = 10000): KafkaConsumer[Array[Byte], Array[Byte]] = {
    val props = KafkaUtils.createConsumerConnectorProperties(kafkaAddress, consumerTimeout)
    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](props)
    consumers.add(consumer)
    consumer
  }

}
