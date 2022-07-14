package pl.touk.nussknacker.engine.kafka

import kafka.server
import kafka.server.{KafkaRaftServer, Server}
import kafka.tools.StorageTool
import org.apache.commons.io.output.NullOutputStream
import org.apache.kafka.clients.consumer.{Consumer, ConsumerConfig}
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringSerializer}
import org.apache.kafka.common.utils.Time
import org.apache.kafka.common.{IsolationLevel, Uuid}

import java.io.{File, PrintStream}
import java.nio.file.Files
import java.util.{Locale, Properties}
import scala.language.implicitConversions

object EmbeddedKafkaServer {
  val localhost = "127.0.0.1"

  def run(brokerPort: Int, controllerPort: Int, kafkaBrokerConfig: Map[String, String]): EmbeddedKafkaServer = {
    val kafka = runKafka(brokerPort, controllerPort, kafkaBrokerConfig)
    EmbeddedKafkaServer(kafka, s"$localhost:$brokerPort")
  }

  private def runKafka(brokerPort: Int, controllerPort: Int, kafkaBrokerConfig: Map[String, String]): Server = {
    val logDir = tempDir()
    val kafkaConfig = prepareServerConfig(brokerPort, controllerPort, logDir, kafkaBrokerConfig)
    prepareRaftStorage(logDir, kafkaConfig)
    val server = new KafkaRaftServer(kafkaConfig, time = Time.SYSTEM, None)
    server.startup()
    server
  }

  private def prepareServerConfig(brokerPort: Int, controllerPort: Int, logDir: File, kafkaBrokerConfig: Map[String, String]) = {
    val properties = new Properties()
    properties.setProperty("broker.id", "0")
    properties.setProperty("process.roles", "broker,controller")
    properties.setProperty("listeners", s"PLAINTEXT://$localhost:$brokerPort,CONTROLLER://$localhost:$controllerPort")
    properties.setProperty("listener.security.protocol.map", s"PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
    properties.setProperty("controller.listener.names", s"CONTROLLER")
    properties.setProperty("controller.quorum.voters", s"0@$localhost:$controllerPort")
    properties.setProperty("num.partitions", "1")
    properties.setProperty("offsets.topic.replication.factor", "1")
    properties.setProperty("log.cleaner.dedupe.buffer.size", (2 * 1024 * 1024L).toString) //2MB should be enough for tests
    properties.setProperty("transaction.state.log.replication.factor", "1")
    properties.setProperty("transaction.state.log.min.isr", "1")
    properties.setProperty("log.dir", logDir.getAbsolutePath)
    kafkaBrokerConfig.foreach { case (key, value) =>
      properties.setProperty(key, value)
    }
    new server.KafkaConfig(properties)
  }

  private def prepareRaftStorage(logDir: File, kafkaConfig: server.KafkaConfig) = {
    val uuid = Uuid.randomUuid()
    StorageTool.formatCommand(new PrintStream(new NullOutputStream), Seq(logDir.getAbsolutePath),
      StorageTool.buildMetadataProperties(uuid.toString, kafkaConfig), ignoreFormatted = false)
  }

  private def tempDir(): File = {
    Files.createTempDirectory("embeddedKafka").toFile
  }
}

case class EmbeddedKafkaServer(kafkaServer: Server, kafkaAddress: String) {
  def shutdown(): Unit = {
    kafkaServer.shutdown()
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

  private def createCommonProducerProps[K, T](kafkaAddress: String, id: String) = {
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
    props.setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString.toLowerCase(Locale.ROOT))
    props
  }

  implicit def richConsumer[K, M](consumer: Consumer[K, M]): RichKafkaConsumer[K, M] = new RichKafkaConsumer(consumer)

}
