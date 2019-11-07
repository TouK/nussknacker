package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkStreamingProcessManagerProvider}

import scala.concurrent.duration._

trait StreamingDockerTest extends DockerTest { self: Suite =>

  override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient = new KafkaClient(config.getString("processConfig.kafka.kafkaAddress"),
      config.getString("processConfig.kafka.zkAddress"))
  }

  override def afterAll(): Unit = {
    kafkaClient.shutdown()
    super.afterAll()
  }

  lazy val kafkaContainer: DockerContainer = DockerContainer("wurstmeister/kafka:1.0.1", name = Some("kafka"))
    .withEnv(s"KAFKA_ADVERTISED_PORT=$KafkaPort",
      s"KAFKA_ZOOKEEPER_CONNECT=zookeeper:$ZookeeperDefaultPort",
      "KAFKA_BROKER_ID=0",
      "HOSTNAME_COMMAND=grep $HOSTNAME /etc/hosts | awk '{print $1}'")
    .withLinks(ContainerLink(zookeeperContainer, "zookeeper"))
    .withReadyChecker(DockerReadyChecker.LogLineContains("started (kafka.server.KafkaServer)").looped(5, 1 second))

  lazy val taskManagerContainer: DockerContainer = buildTaskManagerContainer(additionalLinks = List(ContainerLink(kafkaContainer, "kafka")))

  abstract override def dockerContainers: List[DockerContainer] = {
    List(
      zookeeperContainer,
      kafkaContainer,
      jobManagerContainer,
      taskManagerContainer
    ) ++ super.dockerContainers
  }

  def config: Config = ConfigFactory.load()
    .withValue("processConfig.kafka.zkAddress", fromAnyRef(s"${ipOfContainer(zookeeperContainer)}:$ZookeeperDefaultPort"))
    .withValue("processConfig.kafka.kafkaAddress", fromAnyRef(s"${ipOfContainer(kafkaContainer)}:$KafkaPort"))
    .withValue("flinkConfig.restUrl", fromAnyRef(s"http://${ipOfContainer(jobManagerContainer)}:$FlinkJobManagerRestPort"))

  protected lazy val processManager: ProcessManager = FlinkStreamingProcessManagerProvider.defaultProcessManager(config)

}
