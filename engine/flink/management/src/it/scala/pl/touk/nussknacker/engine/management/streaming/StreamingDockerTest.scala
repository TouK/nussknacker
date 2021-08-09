package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.modelconfig.LoadedConfig
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import scala.concurrent.duration._

trait StreamingDockerTest extends DockerTest { self: Suite =>

  protected var kafkaClient: KafkaClient = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient = new KafkaClient(kafkaAddress, s"${ipOfContainer(zookeeperContainer)}:$ZookeeperDefaultPort", self.suiteName)
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

  override protected def additionalConfig: Config = ConfigFactory.empty()
    .withValue("modelConfig.kafka.kafkaAddress", fromAnyRef(kafkaAddress))

  private def kafkaAddress = s"${ipOfContainer(kafkaContainer)}:$KafkaPort"

  protected lazy val deploymentManager: DeploymentManager = FlinkStreamingDeploymentManagerProvider.defaultDeploymentManager(LoadedConfig(config, config))

}
