package pl.touk.esp.engine.kafka

import java.util.Properties

import kafka.admin.AdminUtils
import kafka.consumer.ConsumerConnector
import kafka.utils.ZkUtils
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord}

class KafkaClient(kafkaAddress: String, zkAddress: String) {
  val producer = createKafkaProducer[String, String]

  val consumers = collection.mutable.HashSet[ConsumerConnector]()

  val zkClient = ZkUtils.createZkClient(zkAddress, 10000, 10000)
  val zkUtils = ZkUtils(zkClient, isZkSecurityEnabled = false)

  private def createKafkaProducer[T, K]: KafkaProducer[T, K] = {
    KafkaUtils.createKafkaProducer(kafkaAddress)
  }

  def createTopic(name: String) = {
    AdminUtils.createTopic(zkUtils, name, 5, 1, new Properties())
    AdminUtils.createOrUpdateTopicPartitionAssignmentPathInZK(zkUtils, name,
      Map(0 -> Seq(0), 1 -> Seq(0), 2 -> Seq(0), 3 -> Seq(0), 4 -> Seq(0)
    ), new Properties, update = true)
  }

  def deleteTopic(name: String) = {
    AdminUtils.deleteTopic(zkUtils, name)
  }

  def sendMessage(topic: String, content: String) = {
    producer.send(new ProducerRecord[String, String](topic, content))
  }

  def sendMessage(topic: String, content: String, callback: Callback) = {
    producer.send(new ProducerRecord[String, String](topic, content), callback)
  }

  def shutdown() = {
    consumers.foreach(_.shutdown())
    producer.close()
    zkUtils.close()
    zkClient.close()
  }

  def createConsumer(): ConsumerConnector = {
    val consumer = KafkaUtils.createConsumerConnector(zkAddress)
    consumers.add(consumer)
    consumer
  }

}
