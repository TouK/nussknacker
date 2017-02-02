package pl.touk.esp.engine.kafka

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.KafkaClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import pl.touk.esp.engine.util.ThreadUtils

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object KafkaEspUtils extends LazyLogging {

  import scala.collection.JavaConversions._
  import scala.concurrent.ExecutionContext.Implicits.global

  val defaultTimeoutMillis = 10000

  def setOffsetToLatest(topic: String, groupId: String, config: KafkaConfig): Unit = {
    val timeoutMillis = readTimeout(config)
    logger.info(s"Setting offset to latest for topic: $topic, groupId: $groupId")
    val props = setKafkaProps(groupId, config)
    //komunikujemy sie z konsumerem kafkowym w nowym watku, po to zeby konsumer nie dowiedzial sie o InterruptedException
    //kiedy to flink probuje np. restartowac proces, bo inaczej robi sie deadlock
    val consumerAfterWork = Future {
      // musimy tutaj ustawic classloader z kafki, bo inaczej nie dziala
      // http://stackoverflow.com/questions/40037857/intermittent-exception-in-tests-using-the-java-kafka-client
      ThreadUtils.withThisAsContextClassLoader(classOf[KafkaClient].getClassLoader) {
        var consumer: KafkaConsumer[String, String] = null
        try {
          consumer = new KafkaConsumer[String, String](props)
          setOffsetToLatest(topic, consumer)
        } finally {
          if (consumer != null) {
            consumer.close()
          }
        }
      }
    }
    Await.result(consumerAfterWork, Duration.apply(timeoutMillis, TimeUnit.MILLISECONDS))
  }

  private def readTimeout(config: KafkaConfig): Long = {
    config.kafkaProperties.flatMap(props =>
      props.get("session.timeout.ms").map(_.toLong)).getOrElse(defaultTimeoutMillis)
  }

  private def setOffsetToLatest(topic: String, consumer: KafkaConsumer[String, String]): Unit = {
    val partitions = consumer.partitionsFor(topic).map { partition => new TopicPartition(partition.topic(), partition.partition()) }
    consumer.assign(partitions)
    consumer.seekToEnd(partitions: _*)
    partitions.foreach(p => consumer.position(p)) //wolamy `position` bo seekToEnd jest domyslnie leniwe i ta metoda to ewaluuje
    consumer.commitSync()
  }

  private def setKafkaProps(groupId: String, config: KafkaConfig): Properties = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", config.kafkaAddress)
    props.setProperty("group.id", groupId)
    props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.setProperty("session.timeout.ms", readTimeout(config).toString)
    props
  }
}