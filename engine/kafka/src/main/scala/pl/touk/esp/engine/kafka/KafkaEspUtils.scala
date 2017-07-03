package pl.touk.esp.engine.kafka

import java.util.{Collections, Properties}
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.KafkaClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import pl.touk.esp.engine.util.ThreadUtils

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._

object KafkaEspUtils extends LazyLogging {

  import scala.collection.JavaConversions._
  import scala.concurrent.ExecutionContext.Implicits.global

  val defaultTimeoutMillis = 10000

  def setOffsetToLatest(topic: String, groupId: String, config: KafkaConfig): Unit = {
    val timeoutMillis = readTimeout(config)
    logger.info(s"Setting offset to latest for topic: $topic, groupId: $groupId")
    val consumerAfterWork = Future {
      doWithTempKafkaConsumer(config, Some(groupId)) { consumer =>
        setOffsetToLatest(topic, consumer)
      }
    }
    Await.result(consumerAfterWork, Duration.apply(timeoutMillis, TimeUnit.MILLISECONDS))
  }

  def toProperties(config: KafkaConfig, groupId: Option[String]) = {
    val props = new Properties()
    props.setProperty("zookeeper.connect", config.zkAddress)
    props.setProperty("bootstrap.servers", config.kafkaAddress)
    props.setProperty("auto.offset.reset", "earliest")
    groupId.foreach(props.setProperty("group.id", _))
    config.kafkaProperties.map(_.asJava).foreach(props.putAll)
    props
  }

  def toProducerProperties(config: KafkaConfig) = {
    val props: Properties = new Properties
    props.setProperty("bootstrap.servers", config.kafkaAddress)
    props.setProperty("key.serializer", classOf[ByteArraySerializer].getCanonicalName)
    props.setProperty("value.serializer", classOf[ByteArraySerializer].getCanonicalName)
    props.setProperty("acks", "all")
    props.setProperty("retries", "0")
    props.setProperty("batch.size", "16384")
    props.setProperty("linger.ms", "1")
    props.setProperty("buffer.memory", "33554432")
    config.kafkaProperties.getOrElse(Map.empty).foreach { case (k, v) =>
      props.setProperty(k, v)
    }
    props
  }

  private def toPropertiesForTempConsumer(config: KafkaConfig, group: Option[String]) = {
    val props = toProperties(config, group)
    props.put("value.deserializer", classOf[ByteArrayDeserializer])
    props.put("key.deserializer", classOf[ByteArrayDeserializer])
    props.setProperty("session.timeout.ms", readTimeout(config).toString)
    props
  }

  def readLastMessages(topic: String, size: Int, config: KafkaConfig) : List[Array[Byte]] = {
    doWithTempKafkaConsumer(config, None) { consumer =>
      try {
        consumer.partitionsFor(topic).map(no => new TopicPartition(topic, no.partition())).view.flatMap { tp =>
          consumer.assign(Collections.singletonList(tp))
          consumer.seekToEnd(tp)
          val lastOffset = consumer.position(tp)
          val result = if (lastOffset == 0) {
            List()
          } else {
            val offsetToSearch = Math.max(0, lastOffset - size)
            consumer.seek(tp, offsetToSearch)
            consumer.poll(100).records(tp).toList.map(_.value())
          }
          consumer.unsubscribe()
          result
        }.take(size).toList
      } finally {
        consumer.unsubscribe()
      }
    }
  }

  private def doWithTempKafkaConsumer[T](config: KafkaConfig, groupId: Option[String])(fun: KafkaConsumer[Array[Byte], Array[Byte]] => T) = {
    // musimy tutaj ustawic classloader z kafki, bo inaczej nie dziala
    // http://stackoverflow.com/questions/40037857/intermittent-exception-in-tests-using-the-java-kafka-client
    ThreadUtils.withThisAsContextClassLoader(classOf[KafkaClient].getClassLoader) {
      val consumer: KafkaConsumer[Array[Byte], Array[Byte]] = new KafkaConsumer(toPropertiesForTempConsumer(config, groupId))
      try {
        fun(consumer)
      } finally {
        consumer.close()
      }
    }
  }

  private def readTimeout(config: KafkaConfig): Long =
    config.kafkaProperties.flatMap(props => props.get("session.timeout.ms").map(_.toLong)).getOrElse(defaultTimeoutMillis)

  private def setOffsetToLatest(topic: String, consumer: KafkaConsumer[_, _]): Unit = {
    val partitions = consumer.partitionsFor(topic).map { partition => new TopicPartition(partition.topic(), partition.partition()) }
    consumer.assign(partitions)
    consumer.seekToEnd(partitions: _*)
    partitions.foreach(p => consumer.position(p)) //wolamy `position` bo seekToEnd jest domyslnie leniwe i ta metoda to ewaluuje
    consumer.commitSync()
  }

}