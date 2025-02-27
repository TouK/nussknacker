package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.KafkaClient
import org.apache.kafka.clients.admin.{Admin, AdminClient}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.{Callback, Producer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.{IsolationLevel, TopicPartition}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.time
import java.util.{Collections, Properties}
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Using}
import scala.util.Using.Releasable

object KafkaUtils extends KafkaUtils

trait KafkaUtils extends LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.jdk.CollectionConverters._

  val defaultTimeoutMillis = 10000

  def setClientId(props: Properties, id: String): Unit = {
    props.setProperty("client.id", sanitizeClientId(id))
  }

  def createKafkaAdminClient(kafkaConfig: KafkaConfig): Admin = {
    AdminClient.create(withPropertiesFromConfig(new Properties, kafkaConfig))
  }

  def usingAdminClient[T](kafkaConfig: KafkaConfig)(adminClientOperation: Admin => T): T = {
    // we don't use default close not to block indefinitely
    val releasable = new Releasable[Admin] {
      override def release(resource: Admin): Unit = resource.close(time.Duration.ofMillis(defaultTimeoutMillis))
    }
    Using.resource(createKafkaAdminClient(kafkaConfig))(adminClientOperation)(releasable)
  }

  def sanitizeClientId(originalId: String): String =
    // https://github.com/apache/kafka/blob/trunk/core/src/main/scala/kafka/common/Config.scala#L25-L35
    originalId.replaceAll("[^a-zA-Z0-9\\._\\-]", "_")

  def setOffsetToLatest(topic: String, groupId: String, config: KafkaConfig): Unit = {
    val timeoutMillis = readTimeoutForTempConsumer(config)
    logger.info(s"Setting offset to latest for topic: $topic, groupId: $groupId")
    val consumerAfterWork = Future {
      doWithTempKafkaConsumer(config, Some(groupId)) { consumer =>
        setOffsetToLatest(topic, consumer)
      }
    }
    Await.result(consumerAfterWork, Duration.apply(timeoutMillis, TimeUnit.MILLISECONDS))
  }

  def setOffsetToEarliest(topic: String, groupId: String, config: KafkaConfig): Unit = {
    val timeoutMillis = readTimeoutForTempConsumer(config)
    logger.info(s"Setting offset to latest for topic: $topic, groupId: $groupId")
    val consumerAfterWork = Future {
      doWithTempKafkaConsumer(config, Some(groupId)) { consumer =>
        setOffsetToEarliest(topic, consumer)
      }
    }
    Await.result(consumerAfterWork, Duration.apply(timeoutMillis, TimeUnit.MILLISECONDS))
  }

  def toProducerProperties(config: KafkaConfig, clientId: String): Properties = {
    val props: Properties = new Properties
    props.put("key.serializer", classOf[ByteArraySerializer])
    props.put("value.serializer", classOf[ByteArraySerializer])
    setClientId(props, clientId)
    withPropertiesFromConfig(props, config)
  }

  private def withPropertiesFromConfig(defaults: Properties, kafkaConfig: KafkaConfig): Properties = {
    val props = new Properties()
    defaults.forEach((k, v) => props.put(k, v))
    kafkaConfig.kafkaAddress.foreach { kafkaAddress =>
      props.put("bootstrap.servers", kafkaAddress)
    }
    kafkaConfig.kafkaProperties.getOrElse(Map.empty).foreach { case (k, v) =>
      props.put(k, v)
    }
    props
  }

  def toConsumerProperties(config: KafkaConfig, groupId: Option[String]): Properties = {
    val props = new Properties()
    props.put("value.deserializer", classOf[ByteArrayDeserializer])
    props.put("key.deserializer", classOf[ByteArrayDeserializer])
    groupId.foreach(props.setProperty("group.id", _))
    withPropertiesFromConfig(props, config)
  }

  def readLastMessages(
      topic: TopicName.ForSource,
      size: Int,
      config: KafkaConfig
  ): List[ConsumerRecord[Array[Byte], Array[Byte]]] = {
    doWithTempKafkaConsumer(config, None) { consumer =>
      try {
        consumer
          .partitionsFor(topic.name)
          .asScala
          .map(no => new TopicPartition(topic.name, no.partition()))
          .view
          .flatMap { tp =>
            val partitions = Collections.singletonList(tp)
            consumer.assign(partitions)
            consumer.seekToEnd(partitions)
            val lastOffset     = consumer.position(tp)
            val offsetToSearch = Math.max(0, lastOffset - size)
            consumer.seek(tp, offsetToSearch)
            val result = new ArrayBuffer[ConsumerRecord[Array[Byte], Array[Byte]]](size)
            result.appendAll(consumer.poll(java.time.Duration.ofMillis(100)).records(tp).asScala)
            // result might be empty if we shift offset to far and there will be
            // no messages on the topic due to retention
            if (result.isEmpty) {
              consumer.seekToBeginning(partitions)
            }
            var currentOffset = consumer.position(tp)
            // Trying to poll records until desired size OR till the end of the topic.
            // So when trying to read 70 msgs from topic with only 50, we will return 50 immediately
            // instead of waiting for another 20 to be written to the topic.
            while (result.size < size && currentOffset < lastOffset) {
              result.appendAll(consumer.poll(java.time.Duration.ofMillis(100)).records(tp).asScala)
              currentOffset = consumer.position(tp)
            }
            consumer.unsubscribe()
            result.take(size)
          }
          .take(size)
          .toList
      } finally {
        consumer.unsubscribe()
      }
    }
  }

  private def doWithTempKafkaConsumer[T](config: KafkaConfig, groupId: Option[String])(
      fun: KafkaConsumer[Array[Byte], Array[Byte]] => T
  ) = {
    // there has to be Kafka's classloader
    // http://stackoverflow.com/questions/40037857/intermittent-exception-in-tests-using-the-java-kafka-client
    ThreadUtils.withThisAsContextClassLoader(classOf[KafkaClient].getClassLoader) {
      val properties = KafkaUtils.toConsumerProperties(config, groupId)
      // default is read uncommitted which is not a good default
      setIsolationLevelIfAbsent(properties, IsolationLevel.READ_COMMITTED)
      properties.setProperty("session.timeout.ms", readTimeoutForTempConsumer(config).toString)
      val consumer: KafkaConsumer[Array[Byte], Array[Byte]] = new KafkaConsumer(properties)
      Using.resource(consumer)(fun)
    }
  }

  def setIsolationLevelIfAbsent(consumerProperties: Properties, isolationLevel: IsolationLevel): Unit = {
    consumerProperties.putIfAbsent(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel.toString.toLowerCase)
  }

  private def readTimeoutForTempConsumer(config: KafkaConfig): Long =
    config.kafkaProperties.flatMap(_.get("session.timeout.ms").map(_.toLong)).getOrElse(defaultTimeoutMillis)

  private def setOffsetToLatest(topic: String, consumer: KafkaConsumer[_, _]): Unit = {
    val partitions = consumer.partitionsFor(topic).asScala.map { partition =>
      new TopicPartition(partition.topic(), partition.partition())
    }
    consumer.assign(partitions.asJava)
    consumer.seekToEnd(partitions.asJava)
    partitions.foreach(p => consumer.position(p)) // `seekToEnd` is lazy, we have to invoke `position` to change offset
    consumer.commitSync()
  }

  private def setOffsetToEarliest(topic: String, consumer: KafkaConsumer[_, _]): Unit = {
    val partitions = consumer.partitionsFor(topic).asScala.map { partition =>
      new TopicPartition(partition.topic(), partition.partition())
    }
    consumer.assign(partitions.asJava)
    consumer.seekToBeginning(partitions.asJava)
    partitions.foreach(p => consumer.position(p))
    consumer.commitSync()
  }

  def sendToKafkaWithTempProducer(topic: String, key: Array[Byte], value: Array[Byte])(
      kafkaProducerCreator: KafkaProducerCreator[Array[Byte], Array[Byte]]
  ): Future[RecordMetadata] = {
    sendToKafkaWithTempProducer(new ProducerRecord(topic, key, value))(kafkaProducerCreator)
  }

  def sendToKafkaWithTempProducer(
      record: ProducerRecord[Array[Byte], Array[Byte]]
  )(kafkaProducerCreator: KafkaProducerCreator[Array[Byte], Array[Byte]]): Future[RecordMetadata] = {
    // returned future is completed, as this method flushes producer cache
    Using.resource(kafkaProducerCreator.createProducer("temp-" + record.topic())) { producer =>
      sendToKafka(record)(producer)
    }
  }

  def sendToKafka[K, V](topic: String, key: K, value: V)(producer: Producer[K, V]): Future[RecordMetadata] = {
    sendToKafka(new ProducerRecord(topic, key, value))(producer)
  }

  def sendToKafka[K, V](record: ProducerRecord[K, V])(producer: Producer[K, V]): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    producer.send(record, KafkaUtils.producerCallback(promise))
    promise.future
  }

  def producerCallback(promise: Promise[RecordMetadata]): Callback = new Callback {

    override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
      val result = if (exception == null) Success(metadata) else Failure(exception)
      promise.complete(result)
    }

  }

  // It can't be in AzureUtils because it must be accessible from Lite Runtime
  val azureEventHubsUrl = ".servicebus.windows.net"

}

case class PreparedKafkaTopic[T <: TopicName](original: T, prepared: T)
