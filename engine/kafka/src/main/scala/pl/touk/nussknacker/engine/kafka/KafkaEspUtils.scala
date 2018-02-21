package pl.touk.nussknacker.engine.kafka

import java.util.concurrent.TimeUnit
import java.util.{Collections, Properties}

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.KafkaClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import pl.touk.nussknacker.engine.util.ThreadUtils

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success}

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
            val temp = consumer.poll(100).records(tp)
            // if some of the messages were removed due to retention then in temp we will have zero records
            if(temp.isEmpty) {
              consumer.seekToBeginning(tp)
              consumer.poll(100).records(tp)
            } else {
              temp
            }
          }.toList.map(_.value()).take(size)
          consumer.unsubscribe()
          result
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
    val partitions = consumer.partitionsFor(topic).map { partition => new TopicPartition(partition.topic(), partition.partition()) }
    consumer.assign(partitions)
    consumer.seekToEnd(partitions: _*)
    partitions.foreach(p => consumer.position(p)) //`seekToEnd` is lazy, we have to invoke `position` to change offset
    consumer.commitSync()
  }

  def sendToKafkaWithTempProducer(topic: String, key: Array[Byte], value: Array[Byte])(kafkaConfig: KafkaConfig): Future[RecordMetadata] = {
    var producer: KafkaProducer[Array[Byte], Array[Byte]] = null
    try {
      producer = createProducer(kafkaConfig)
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

  def createProducer(kafkaConfig: KafkaConfig): KafkaProducer[Array[Byte], Array[Byte]] = {
    new KafkaProducer[Array[Byte], Array[Byte]](KafkaEspUtils.toProducerProperties(kafkaConfig))
  }

  def producerCallback(promise: Promise[RecordMetadata]): Callback =
    new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        val result = if (exception == null) Success(metadata) else Failure(exception)
        promise.complete(result)
      }
    }

}