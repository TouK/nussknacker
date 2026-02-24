package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import kafka.server
import kafka.server.{KafkaRaftServer, Server}
import org.apache.commons.io.FileUtils
import org.apache.kafka.clients.consumer.{Consumer, ConsumerConfig}
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.{IsolationLevel, Uuid}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringSerializer}
import org.apache.kafka.common.utils.Time
import org.apache.kafka.metadata.storage.Formatter

import java.io.{ByteArrayOutputStream, File, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.{Locale, Properties}
import scala.language.implicitConversions
import scala.util.control.NonFatal

// We should consider switching to KafkaClusterTestKit (https://github.com/apache/kafka/blob/3.6/core/src/test/java/kafka/testkit/KafkaClusterTestKit.java),
// it's used by spring-kafka (https://github.com/spring-projects/spring-kafka/blob/3.1.x/spring-kafka-test/src/main/java/org/springframework/kafka/test/EmbeddedKafkaKraftBroker.java).
object EmbeddedKafkaKraftServer extends LazyLogging {

  private val localhost: String = "127.0.0.1"

  def run(
      brokerPort: Int,
      controllerPort: Int,
      kafkaBrokerConfig: Map[String, String]
  ): EmbeddedKafkaKraftServer = {
    val kafkaServerLogDir = Files.createTempDirectory("embeddedKafka").toFile
    val clusterId         = Uuid.randomUuid()
    val nodeId            = 1
    val kafkaConfig =
      prepareKafkaServerConfig(brokerPort, controllerPort, kafkaServerLogDir, kafkaBrokerConfig, clusterId, nodeId)
    val kafkaServer = new EmbeddedKafkaKraftServer(
      () => {
        prepareRaftStorage(kafkaServerLogDir, clusterId, nodeId)
        new KafkaRaftServer(kafkaConfig, time = Time.SYSTEM)
      },
      s"$localhost:$brokerPort",
      kafkaServerLogDir
    )
    kafkaServer.startupKafkaServer()
    kafkaServer
  }

  private def prepareKafkaServerConfig(
      brokerPort: Int,
      controllerPort: Int,
      logDir: File,
      kafkaBrokerConfig: Map[String, String],
      clusterId: Uuid,
      nodeId: Int
  ) = {
    val properties = new Properties()
    properties.setProperty("node.id", nodeId.toString)
    properties.setProperty("process.roles", "broker,controller")
    properties.setProperty("listeners", s"PLAINTEXT://$localhost:$brokerPort,CONTROLLER://$localhost:$controllerPort")
    properties.setProperty("listener.security.protocol.map", s"PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
    properties.setProperty("controller.listener.names", s"CONTROLLER")
    properties.setProperty("inter.broker.listener.name", "PLAINTEXT")
    properties.setProperty("controller.quorum.voters", s"$nodeId@$localhost:$controllerPort")
    properties.setProperty("cluster.id", clusterId.toString)
    properties.setProperty("num.partitions", "1")
    properties.setProperty("group.initial.rebalance.delay.ms", "0")
    properties.setProperty("offsets.topic.num.partitions", "1")
    properties.setProperty("offsets.topic.replication.factor", "1")
    properties.setProperty(
      "log.cleaner.dedupe.buffer.size",
      (2 * 1024 * 1024L).toString
    ) // 2MB should be enough for tests
    properties.setProperty("transaction.state.log.num.partitions", "1")
    properties.setProperty("transaction.state.log.replication.factor", "1")
    properties.setProperty("transaction.state.log.min.isr", "1")
    properties.setProperty("log.dir", logDir.getAbsolutePath)
    kafkaBrokerConfig.foreach { case (key, value) =>
      properties.setProperty(key, value)
    }
    new server.KafkaConfig(properties)
  }

  private def prepareRaftStorage(logDir: File, clusterId: Uuid, nodeId: Int): Unit = {
    val logStream = new ByteArrayOutputStream()
    try {
      val logDirAsString = logDir.getAbsolutePath
      val formatter = new Formatter()
        .setPrintStream(new PrintStream(logStream))
        .setClusterId(clusterId.toString)
        .setNodeId(nodeId)
        .setControllerListenerName("CONTROLLER")
        .setMetadataLogDirectory(logDirAsString)
        .addDirectory(logDirAsString)
      formatter.run()
    } finally {
      logStream.toString(StandardCharsets.UTF_8).trim.linesIterator.foreach(logger.info("[init] {}", _))
    }
  }

}

class EmbeddedKafkaKraftServer(
    createKafkaServer: () => Server,
    val bootstrapServers: String,
    kafkaServerLogDir: File
) extends LazyLogging {

  private var kafkaServer: Server = createKafkaServer()

  def recreateKafkaServer(): Unit = {
    shutdownKafkaServer()
    kafkaServer = createKafkaServer()
  }

  def startupKafkaServer(): Unit = {
    kafkaServer.startup()
  }

  def shutdownKafkaServer(): Unit = {
    kafkaServer.shutdown()
    kafkaServer.awaitShutdown()
    cleanDirectorySilently(kafkaServerLogDir)
  }

  private def cleanDirectorySilently(directory: File): Unit = {
    try {
      FileUtils.deleteDirectory(directory)
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Cannot remove $kafkaServerLogDir", e)
    }
  }

}

object KafkaTestUtils {

  def createRawKafkaProducer(kafkaAddress: String, id: String): KafkaProducer[Array[Byte], Array[Byte]] = {
    val props: Properties = createCommonProducerProps(kafkaAddress, id)
    props.put("key.serializer", classOf[ByteArraySerializer].getName)
    props.put("value.serializer", classOf[ByteArraySerializer].getName)
    props.put("retries", 3.toString)
    props.put("acks", "all")
    new KafkaProducer(props)
  }

  def createKafkaProducer(kafkaAddress: String, id: String): KafkaProducer[String, String] = {
    val props: Properties = createCommonProducerProps(kafkaAddress, id)
    props.put("key.serializer", classOf[StringSerializer].getName)
    props.put("value.serializer", classOf[StringSerializer].getName)
    props.put("retries", 3.toString)
    props.put("acks", "all")
    new KafkaProducer(props)
  }

  private def createCommonProducerProps[K, T](kafkaAddress: String, id: String): Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", kafkaAddress)
    props.put("batch.size", "100000")
    KafkaUtils.setClientId(props, id)
    props
  }

  def createConsumerConnectorProperties(kafkaAddress: String, groupId: String = "testGroup"): Properties = {
    val props = new Properties()
    props.put("group.id", groupId)
    props.put("bootstrap.servers", kafkaAddress)
    props.put("auto.offset.reset", "earliest")
    props.put("request.timeout.ms", 2000)
    props.put("default.api.timeout.ms", 2000)
    props.put("key.deserializer", classOf[ByteArrayDeserializer])
    props.put("value.deserializer", classOf[ByteArrayDeserializer])
    // default is read uncommitted which is pretty weird and harmful
    props.setProperty(
      ConsumerConfig.ISOLATION_LEVEL_CONFIG,
      IsolationLevel.READ_COMMITTED.toString.toLowerCase(Locale.ROOT)
    )
    props
  }

  implicit def richConsumer[K, M](consumer: Consumer[K, M]): RichKafkaConsumer[K, M] = new RichKafkaConsumer(consumer)

}
