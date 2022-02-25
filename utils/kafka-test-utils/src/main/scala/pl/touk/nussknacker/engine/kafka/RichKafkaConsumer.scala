package pl.touk.nussknacker.engine.kafka

import io.circe.Json

import java.time.Duration
import java.util.concurrent.TimeoutException
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecord, ConsumerRecords}
import org.apache.kafka.common.TopicPartition
import org.scalatest.concurrent.Eventually.{eventually, _}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.CirceUtil

class RichKafkaConsumer[K, M](consumer: Consumer[K, M]) {

  import scala.collection.JavaConverters._

  def consume(topic: String, secondsToWait: Int = 20): Stream[KeyMessage[K, M]] =
    consumeWithConsumerRecord(topic, secondsToWait)
      .map(record => KeyMessage(record.key(), record.value(), record.timestamp()))

  def consumeWithString(topic: String, secondsToWait: Int = 20)(implicit ev: M =:= Array[Byte]): Stream[String] =
    consumeWithConsumerRecord(topic, secondsToWait)
      .map(record => new String(record.value()))

  def consumeWithJson(topic: String, secondsToWait: Int = 20)(implicit ev: M =:= Array[Byte]): Stream[Json] =
    consumeWithConsumerRecord(topic, secondsToWait)
      .map(record => CirceUtil.decodeJsonUnsafe[Json](record.value()))

  def consumeWithConsumerRecord(topic: String, secondsToWait: Int = 20): Stream[ConsumerRecord[K, M]] = {
    implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(secondsToWait, Seconds), Span(100, Millis))

    val partitionsInfo = eventually {
      consumer.listTopics.asScala.getOrElse(topic, throw new IllegalStateException(s"Topic: $topic not exists"))
    }

    val partitions = partitionsInfo.asScala.map(no => new TopicPartition(topic, no.partition()))
    consumer.assign(partitions.asJava)

    Stream.continually(()).flatMap(new Poller(secondsToWait))
  }

  //If we do just _ => consumer.poll(...).asScala.toStream, the stream will block indefinitely when no messages are sent
  class Poller(secondsToWait: Int) extends Function1[Unit, Stream[ConsumerRecord[K, M]]] {
    private var timeoutCount = 0

    override def apply(v1: Unit): Stream[ConsumerRecord[K, M]] = {
      val polled = consumer.poll(Duration.ofSeconds(1))
      checkIfEmpty(polled)
      polled.asScala.toStream
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

case class KeyMessage[K, V](k: K, msg: V, timestamp: Long) {
  def message(): V = msg
  def key(): K = k
}
