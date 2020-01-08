package pl.touk.nussknacker.engine.kafka

import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Duration
import java.util.Properties

import kafka.server.KafkaServer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringSerializer}
import org.apache.kafka.common.utils.Time
import org.apache.zookeeper.server.{NIOServerCnxnFactory, ZooKeeperServer}
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.{Millis, Seconds, Span}

object KafkaZookeeperServer {
  val localhost = "127.0.0.1"

  def run(zkPort: Int, kafkaPort: Int, kafkaBrokerConfig: Map[String, String]): KafkaZookeeperServer = {
    val zk = runZookeeper(zkPort)
    val kafka = runKafka(zkPort, kafkaPort, kafkaBrokerConfig)
    KafkaZookeeperServer(zk, kafka, s"$localhost:$zkPort", s"$localhost:$kafkaPort")
  }

  private def runZookeeper(zkPort: Int): NIOServerCnxnFactory = {
    val factory = new NIOServerCnxnFactory()
    factory.configure(new InetSocketAddress(localhost, zkPort), 1024)
    val zkServer = new ZooKeeperServer(tempDir(), tempDir(), 100)
    factory.startup(zkServer)
    factory
  }

  private def runKafka(zkPort: Int, kafkaPort: Int, kafkaBrokerConfig: Map[String, String]): KafkaServer = {
    val properties = new Properties()
    properties.setProperty("zookeeper.connect", s"$localhost:$zkPort")
    properties.setProperty("broker.id", "0")
    properties.setProperty("host.name", localhost)
    properties.setProperty("hostname", localhost)
    properties.setProperty("advertised.host.name", localhost)
    properties.setProperty("num.partitions", "1")
    properties.setProperty("offsets.topic.replication.factor", "1")
    properties.setProperty("log.cleaner.dedupe.buffer.size", (2 * 1024 * 1024L).toString) //2MB should be enough for tests

    properties.setProperty("port", s"$kafkaPort")
    properties.setProperty("log.dir", tempDir().getAbsolutePath)

    kafkaBrokerConfig.foreach { case (key, value) =>
      properties.setProperty(key, value)
    }

    val server = new KafkaServer(new kafka.server.KafkaConfig(properties), time = Time.SYSTEM)
    server.startup()

    server
  }


  private def tempDir(): File = {
    Files.createTempDirectory("zkKafka").toFile
  }
}

case class KafkaZookeeperServer(zooKeeperServer: NIOServerCnxnFactory, kafkaServer: KafkaServer, zkAddress: String, kafkaAddress: String) {
  def shutdown(): Unit = {
    kafkaServer.shutdown()
    zooKeeperServer.shutdown()
  }
}

object KafkaUtils {

  def createRawKafkaProducer(kafkaAddress: String, id: String): KafkaProducer[Array[Byte], Array[Byte]] = {
    val props: Properties = createCommonProducerProps(kafkaAddress, id)
    props.put("key.serializer", classOf[ByteArraySerializer].getName)
    props.put("value.serializer", classOf[ByteArraySerializer].getName)
    new KafkaProducer(props)
  }

  def createKafkaProducer(kafkaAddress: String, id: String): KafkaProducer[String, String] = {
    val props: Properties = createCommonProducerProps(kafkaAddress, id)
    props.put("key.serializer", classOf[StringSerializer].getName)
    props.put("value.serializer", classOf[StringSerializer].getName)
    new KafkaProducer(props)
  }

  private def createCommonProducerProps[K, T](kafkaAddress: String, id: String) = {
    val props = new Properties()
    props.put("bootstrap.servers", kafkaAddress)
    props.put("batch.size", "100000")
    KafkaEspUtils.setClientId(props, id)
    props
  }

  def createConsumerConnectorProperties(kafkaAddress: String, consumerTimeout: Long = 10000): Properties = {
    val props = new Properties()
    props.put("group.id", "testGroup")
    props.put("bootstrap.servers", kafkaAddress)
    props.put("auto.offset.reset", "earliest")
    props.put("consumer.timeout.ms", consumerTimeout.toString)
    props.put("key.deserializer", classOf[ByteArrayDeserializer])
    props.put("value.deserializer", classOf[ByteArrayDeserializer])
    props
  }

  case class KeyMessage[K, V](k: K, msg: V) {
    def message(): V = msg
    def key(): K = k
  }

  implicit class RichConsumerConnector(consumer: KafkaConsumer[Array[Byte], Array[Byte]]) {
    import scala.collection.JavaConverters._

    def consume(topic: String, secondsToWait: Int = 10): Stream[KeyMessage[Array[Byte], Array[Byte]]] = {
      implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(secondsToWait, Seconds), Span(100, Millis))
      val partitionsInfo = eventually {
        consumer.listTopics.asScala.getOrElse(topic, throw new IllegalStateException(s"Topic: $topic not exists"))
      }
      val partitions = partitionsInfo.asScala.map(no => new TopicPartition(topic, no.partition()))
      consumer.assign(partitions.asJava)

      Stream.continually(())
        .flatMap(_ => consumer.poll(Duration.ofSeconds(1)).asScala.toStream)
        .map(record => KeyMessage(record.key(), record.value()))
    }
  }

}