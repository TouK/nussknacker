package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.{Assertion, Matchers, Suite}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, DeploymentManager, GraphProcess}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkStateStatus, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future
import scala.concurrent.duration._

trait StreamingDockerTest extends DockerTest with Matchers { self: Suite =>

  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

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

  protected lazy val deploymentManager: DeploymentManager = FlinkStreamingDeploymentManagerProvider.defaultDeploymentManager(config)

  protected def deployProcessAndWaitIfRunning(process: EspProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None): Assertion = {
    deployProcess(process, processVersion, savepointPath)
    eventually {
      val jobStatus = deploymentManager.findJobStatus(ProcessName(process.id)).futureValue
      logger.debug(s"Waiting for deploy: ${process.id}, $jobStatus")

      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
    }
  }

  protected def deployProcess(process: EspProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None): Assertion = {
    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    assert(deploymentManager.deploy(processVersion, DeploymentData.empty, GraphProcess(marshaled), savepointPath).isReadyWithin(100 seconds))
  }

  protected def cancelProcess(processId: String): Unit = {
    assert(deploymentManager.cancel(ProcessName(processId), user = userToAct).isReadyWithin(10 seconds))
    eventually {
      val runningJobs = deploymentManager
        .findJobStatus(ProcessName(processId))
        .futureValue
        .filter(_.status.isRunning)

      logger.debug(s"waiting for jobs: $processId, $runningJobs")
      if (runningJobs.nonEmpty) {
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
