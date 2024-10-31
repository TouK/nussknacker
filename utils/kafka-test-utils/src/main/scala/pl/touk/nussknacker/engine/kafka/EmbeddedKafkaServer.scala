package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import kafka.server
import kafka.server.{KafkaRaftServer, KafkaServer, Server}
import kafka.tools.StorageTool
import org.apache.commons.io.FileUtils
import org.apache.commons.io.output.NullOutputStream
import org.apache.kafka.clients.consumer.{Consumer, ConsumerConfig}
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringSerializer}
import org.apache.kafka.common.utils.Time
import org.apache.kafka.common.{IsolationLevel, Uuid}
import org.apache.kafka.server.common.MetadataVersion
import org.apache.zookeeper.server.{NIOServerCnxnFactory, ZooKeeperServer}

import java.io.{File, PrintStream}
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.{Locale, Properties}
import scala.language.implicitConversions
import scala.util.control.NonFatal

object EmbeddedKafkaServer {

  // In Kafka 3.2.0 doesn't work create topic and describe topic instantly after it - it doesn't return newly created topic
  // Also there is erro "maybeBalancePartitionLeaders: unable to start processing because of TimeoutException" in log
  // We should consider switching to KafkaClusterTestKit (https://github.com/apache/kafka/blob/3.6/core/src/test/java/kafka/testkit/KafkaClusterTestKit.java),
  // it's used by spring-kafka (https://github.com/spring-projects/spring-kafka/blob/3.1.x/spring-kafka-test/src/main/java/org/springframework/kafka/test/EmbeddedKafkaKraftBroker.java).
  val kRaftEnabled: Boolean = true

  val localhost: String = "127.0.0.1"

  def run(brokerPort: Int, controllerPort: Int, kafkaBrokerConfig: Map[String, String]): EmbeddedKafkaServer = {
    val tempDir     = Files.createTempDirectory("embeddedKafka").toFile
    val clusterId   = Uuid.randomUuid()
    val kafkaConfig = prepareServerConfig(brokerPort, controllerPort, tempDir, kafkaBrokerConfig, clusterId)
    val server = if (kRaftEnabled) {
      prepareRaftStorage(tempDir, kafkaConfig, clusterId)
      new EmbeddedKafkaServer(
        None,
        () => new KafkaRaftServer(kafkaConfig, time = Time.SYSTEM),
        s"$localhost:$brokerPort",
        tempDir
      )
    } else {
      val zk = createZookeeperServer(controllerPort)
      new EmbeddedKafkaServer(
        Some(zk),
        () => new KafkaServer(kafkaConfig, time = Time.SYSTEM),
        s"$localhost:$brokerPort",
        tempDir
      )
    }
    server.startup()
    server
  }

  private def prepareServerConfig(
      brokerPort: Int,
      controllerPort: Int,
      logDir: File,
      kafkaBrokerConfig: Map[String, String],
      clusterId: Uuid
  ) = {
    val properties = new Properties()
    if (kRaftEnabled) {
      properties.setProperty("node.id", "0")
      properties.setProperty("process.roles", "broker,controller")
      properties.setProperty("listeners", s"PLAINTEXT://$localhost:$brokerPort,CONTROLLER://$localhost:$controllerPort")
      properties.setProperty("listener.security.protocol.map", s"PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
      properties.setProperty("controller.listener.names", s"CONTROLLER")
      properties.setProperty("inter.broker.listener.name", "PLAINTEXT")
      properties.setProperty("controller.quorum.voters", s"0@$localhost:$controllerPort")
      properties.setProperty("cluster.id", clusterId.toString)
    } else {
      properties.setProperty("broker.id", "0")
      properties.setProperty("zookeeper.connect", s"$localhost:$controllerPort")
      properties.setProperty("listeners", s"PLAINTEXT://$localhost:$brokerPort")
    }
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

  private def prepareRaftStorage(logDir: File, kafkaConfig: server.KafkaConfig, clusterId: Uuid) = {
    StorageTool.formatCommand(
      new PrintStream(NullOutputStream.INSTANCE),
      Seq(logDir.getAbsolutePath),
      StorageTool.buildMetadataProperties(clusterId.toString, kafkaConfig),
      MetadataVersion.LATEST_PRODUCTION,
      ignoreFormatted = false
    )
  }

  private def createZookeeperServer(zkPort: Int) = {
    val factory = new NIOServerCnxnFactory()
    factory.configure(new InetSocketAddress(localhost, zkPort), 1024)
    val tempDir  = Files.createTempDirectory("embeddedKafkaZk").toFile
    val zkServer = new ZooKeeperServer(tempDir, tempDir, ZooKeeperServer.DEFAULT_TICK_TIME)
    (factory, zkServer, tempDir)
  }

}

class EmbeddedKafkaServer(
    zooKeeperServerOpt: Option[(NIOServerCnxnFactory, ZooKeeperServer, File)],
    createKafkaServer: () => Server,
    val kafkaAddress: String,
    tempDir: File
) extends LazyLogging {

  var kafkaServer: Server = createKafkaServer()

  def recreateKafkaServer(): Unit = {
    kafkaServer = createKafkaServer()
  }

  def startup(): Unit = {
    zooKeeperServerOpt.foreach(t => t._1.startup(t._2))
    kafkaServer.startup()
  }

  def shutdown(): Unit = {
    kafkaServer.shutdown()
    kafkaServer.awaitShutdown()
    cleanDirectory(tempDir)

    zooKeeperServerOpt.foreach { case (cnxnFactory, zkServer, zkTempDir) =>
      cnxnFactory.shutdown()
      // factory shutdown doesn't pass 'fullyShutDown' flag to ZkServer.shutdown, we need to explicitly close database
      zkServer.getZKDatabase.close()
      cleanDirectory(zkTempDir)
    }
  }

  private def cleanDirectory(directory: File): Unit = {
    try {
      FileUtils.deleteDirectory(directory)
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Cannot remove $tempDir", e)
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
