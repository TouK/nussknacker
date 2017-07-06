package pl.touk.esp.engine.kafka

import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Properties

import kafka.api.OffsetRequest
import kafka.consumer.{Consumer, ConsumerConfig, ConsumerConnector, KafkaStream}
import kafka.message.MessageAndMetadata
import kafka.server.{KafkaConfig, KafkaServer}
import kafka.utils.SystemTime
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.zookeeper.server.{NIOServerCnxnFactory, ZooKeeperServer}

import scala.concurrent.{ExecutionContext, Future}

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
    properties.setProperty("log.cleaner.dedupe.buffer.size", (2 * 1024 * 1024L).toString) //2MB should be enough for tests

    properties.setProperty("port", s"$kafkaPort")
    properties.setProperty("log.dir", tempDir().getAbsolutePath)

    kafkaBrokerConfig.foreach { case (key, value) =>
      properties.setProperty(key, value)
    }

    val server = new KafkaServer(new KafkaConfig(properties), SystemTime)
    server.startup()

    server
  }


  private def tempDir(): File = {
    Files.createTempDirectory("zkKafka").toFile
  }
}

case class KafkaZookeeperServer(zooKeeperServer: NIOServerCnxnFactory, kafkaServer: KafkaServer, zkAddress: String, kafkaAddress: String) {
  def shutdown() = {
    kafkaServer.shutdown()
    zooKeeperServer.shutdown()
  }
}

object KafkaUtils {
  def createKafkaProducer[T,K](kafkaAddress: String): KafkaProducer[T, K] = {
    val props = new Properties()
    props.put("bootstrap.servers", kafkaAddress)
    props.put("key.serializer", classOf[StringSerializer].getName)
    props.put("value.serializer", classOf[StringSerializer].getName)
    props.put("batch.size", "100000")
    props.put("request.required.acks", "1")
    new KafkaProducer[T, K](props)
  }

  def createConsumer(zookeeperAddress: String): ConsumerConnector = {
    createConsumerConnector(zookeeperAddress)
  }

  def createConsumerConnector(zookeeperAddress: String, consumerTimeout: Long = 10000): ConsumerConnector = {
    val props = new Properties()
    props.put("group.id", "testGroup")
    props.put("zookeeper.connect", zookeeperAddress)
    props.put("auto.offset.reset", OffsetRequest.SmallestTimeString)
    props.put("consumer.timeout.ms", consumerTimeout.toString)
    Consumer.create(new ConsumerConfig(props))
  }

  implicit class RichConsumerConnector(c: ConsumerConnector) {
    def consume(topic: String): KafkaStream[Array[Byte], Array[Byte]] = {
      c.createMessageStreams(Map(topic -> 1))(topic).head
    }
  }

  implicit class RichKafkaStream(stream: KafkaStream[Array[Byte], Array[Byte]]) {
    def takeNthNonBlocking(n: Int)
                          (implicit ec: ExecutionContext) =
      Future {
        stream.slice(n-1, n).head
      }
  }

}