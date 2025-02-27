package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecord, ConsumerRecords}
import org.apache.kafka.common.TopicPartition
import org.scalatest.concurrent.Eventually.{eventually, _}
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Duration
import java.util.concurrent.TimeoutException
import scala.collection.compat.immutable.LazyList
import scala.reflect.ClassTag

class RichKafkaConsumer[K, M](consumer: Consumer[K, M]) extends LazyLogging {

  import scala.jdk.CollectionConverters._

  import RichKafkaConsumer._

  def consumeWithJson[V: Decoder: ClassTag](
      topic: String,
      secondsToWait: Int = DefaultSecondsToWait
  )(implicit ek: K =:= Array[Byte], em: M =:= Array[Byte]): LazyList[KeyMessage[String, V]] =
    consumeWithConsumerRecord(topic, secondsToWait).map { record =>
      val key     = ConsumerRecordHelper.asJson[String](record.key())
      val message = ConsumerRecordHelper.asJson[V](record.value())
      KeyMessage(key, message, record.timestamp)
    }

  def consumeWithConsumerRecord(
      topic: String,
      secondsToWait: Int = DefaultSecondsToWait
  ): LazyList[ConsumerRecord[K, M]] = {
    val partitions = fetchTopicPartitions(topic, secondsToWait)
    consumer.assign(partitions.asJava)
    logger.debug(s"Consumer assigment: ${consumer.assignment().asScala}")
    logger.debug(s"Consumer offsets: beginning: ${consumer.beginningOffsets(consumer.assignment())}, end: ${consumer
        .endOffsets(consumer.assignment())}")

    LazyList.continually(()).flatMap(new Poller(secondsToWait))
  }

  def getEndOffsets(topic: String, secondsToWait: Int = DefaultSecondsToWait) = {
    val partitions = fetchTopicPartitions(topic, secondsToWait)
    consumer.assign(partitions.asJava)
    consumer.endOffsets(partitions.asJava)
  }

  private def fetchTopicPartitions(topic: String, secondsToWait: Int) = {
    implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(secondsToWait, Seconds), Span(100, Millis))
    // We have to repeat it in eventually - partitionsFor with duration parameter sometimes just returns empty list
    val partitionsInfo = eventually {
      consumer.listTopics.asScala.getOrElse(topic, throw new IllegalStateException(s"Topic: $topic not exists"))
    }
    partitionsInfo.asScala.map(no => new TopicPartition(topic, no.partition()))
  }

  // If we do just _ => consumer.poll(...).asScala.toStream, the stream will block indefinitely when no messages are sent
  class Poller(secondsToWait: Int) extends Function1[Unit, LazyList[ConsumerRecord[K, M]]] {
    private var timeoutCount = 0

    override def apply(v1: Unit): LazyList[ConsumerRecord[K, M]] = {
      val polled = consumer.poll(Duration.ofSeconds(1))
      checkIfEmpty(polled)
      polled.asScala.to(LazyList)
    }

    def checkIfEmpty(records: ConsumerRecords[_, _]): Unit = {
      if (records.isEmpty) {
        timeoutCount += 1
        if (timeoutCount >= secondsToWait) {
          throw new TimeoutException(s"Exceeded waiting time in poll ${timeoutCount}s")
        }
      } else {
        timeoutCount = 0
      }
    }

  }

}

object RichKafkaConsumer {
  private val DefaultSecondsToWait = 30
}

case class KeyMessage[K, V](k: K, msg: V, timestamp: Long) {
  def message(): V = msg
  def key(): K     = k
}
