package pl.touk.nussknacker.engine.kafka

import java.util.concurrent.TimeUnit
import java.util.{Collections, Properties}

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.KafkaClient
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import pl.touk.nussknacker.engine.util.ThreadUtils

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success}

object KafkaEspUtils extends LazyLogging {

  import scala.collection.JavaConverters._
  import scala.concurrent.ExecutionContext.Implicits.global

  val defaultTimeoutMillis = 10000

  def setClientId(props: Properties, id: String): Unit = {
    props.setProperty("client.id", sanitizeClientId(id))
  }

  def sanitizeClientId(originalId: String): String =
    //https://github.com/apache/kafka/blob/trunk/core/src/main/scala/kafka/common/Config.scala#L25-L35
    originalId.replaceAll("[^a-zA-Z0-9\\._\\-]", "_")

  def setToLatestOffsetIfNeeded(config: KafkaConfig, topic: String, consumerGroupId: String): Unit = {
    val setToLatestOffset =
      config.kafkaEspProperties.flatMap(_.get("forceLatestRead")).exists(java.lang.Boolean.parseBoolean)
    if (setToLatestOffset) {
      KafkaEspUtils.setOffsetToLatest(topic, consumerGroupId, config)
    }
  }

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

  def toProperties(config: KafkaConfig, groupId: Option[String]): Properties = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", config.kafkaAddress)
    props.setProperty("auto.offset.reset", "earliest")
    groupId.foreach(props.setProperty("group.id", _))
    config.kafkaProperties.map(_.asJava).foreach(props.putAll)
    props
  }

  def toProducerProperties(config: KafkaConfig, clientId: String): Properties = {
    val props: Properties = new Properties
    props.setProperty("bootstrap.servers", config.kafkaAddress)
    props.setProperty("key.serializer", classOf[ByteArraySerializer].getCanonicalName)
    props.setProperty("value.serializer", classOf[ByteArraySerializer].getCanonicalName)
    props.setProperty("acks", "all")
    props.setProperty("retries", "0")
    props.setProperty("batch.size", "16384")
    props.setProperty("linger.ms", "1")
    props.setProperty("buffer.memory", "33554432")
    setClientId(props, clientId)
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


  def readLastMessages(topic: String, size: Int, config: KafkaConfig) : List[ConsumerRecord[Array[Byte], Array[Byte]]] = {
    doWithTempKafkaConsumer(config, None) { consumer =>
      try {
        consumer.partitionsFor(topic).asScala.map(no => new TopicPartition(topic, no.partition())).view.flatMap { tp =>
          val partitions = Collections.singletonList(tp)
          consumer.assign(partitions)
          consumer.seekToEnd(partitions)
          val lastOffset = consumer.position(tp)
          val offsetToSearch = Math.max(0, lastOffset - size)
          consumer.seek(tp, offsetToSearch)
          val result = new ArrayBuffer[ConsumerRecord[Array[Byte], Array[Byte]]](size)
          result.appendAll(consumer.poll(java.time.Duration.ofMillis(100)).records(tp).asScala)
          // result might be empty if we shift offset to far and there will be
          // no messages on the topic due to retention
          if(result.isEmpty){
            consumer.seekToBeginning(partitions)
          }
          var currentOffset = consumer.position(tp)
          // Trying to poll records until desired size OR till the end of the topic.
          // So when trying to read 70 msgs from topic with only 50, we will return 50 immediately
          // instead of waiting for another 20 to be written to the topic.
          while(result.size < size && currentOffset < lastOffset) {
            result.appendAll(consumer.poll(java.time.Duration.ofMillis(100)).records(tp).asScala)
            currentOffset = consumer.position(tp)
          }
          consumer.unsubscribe()
          result.take(size)
        }.take(size).toList
      } finally {
        consumer.unsubscribe()
      }
    }
  }

  private def doWithTempKafkaConsumer[T](config: KafkaConfig, groupId: Option[String])(fun: KafkaConsumer[Array[Byte], Array[Byte]] => T) = {
    // there has to be Kafka's classloader
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
    val partitions = consumer.partitionsFor(topic).asScala.map { partition => new TopicPartition(partition.topic(), partition.partition()) }
    consumer.assign(partitions.asJava)
    consumer.seekToEnd(partitions.asJava)
    partitions.foreach(p => consumer.position(p)) //`seekToEnd` is lazy, we have to invoke `position` to change offset
    consumer.commitSync()
  }

  def sendToKafkaWithTempProducer(topic: String, key: Array[Byte], value: Array[Byte])(kafkaConfig: KafkaConfig): Future[RecordMetadata] = {
    var producer: KafkaProducer[Array[Byte], Array[Byte]] = null
    try {
      producer = createProducer(kafkaConfig, "temp-"+topic)
      sendToKafka(topic, key, value)(producer)
    } finally {
      if (producer != null) {
        producer.close()
      }
    }
  }

  def sendToKafka[K, V](topic: String, key: K, value: V)(producer: KafkaProducer[K, V]): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    producer.send(new ProducerRecord(topic, key, value), KafkaEspUtils.producerCallback(promise))
    promise.future
  }

  def createProducer(kafkaConfig: KafkaConfig, clientId: String): KafkaProducer[Array[Byte], Array[Byte]] = {
    new KafkaProducer[Array[Byte], Array[Byte]](KafkaEspUtils.toProducerProperties(kafkaConfig, clientId))
  }

  def producerCallback(promise: Promise[RecordMetadata]): Callback =
    new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        val result = if (exception == null) Success(metadata) else Failure(exception)
        promise.complete(result)
      }
    }

}