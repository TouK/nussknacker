package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.{Assertion, Matchers, Suite}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkStateStatus, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._

trait StreamingDockerTest extends DockerTest with Matchers { self: Suite =>

  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  implicit val deploymentService: ProcessingTypeDeploymentService = new ProcessingTypeDeploymentServiceStub(List.empty)

  protected var kafkaClient: KafkaClient = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient = new KafkaClient(hostKafkaAddress, self.suiteName)
  }

  override def afterAll(): Unit = {
    kafkaClient.shutdown()
    super.afterAll()
  }

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
    val graphProcess = ScenarioParser.toGraphProcess(process)
    assert(deploymentManager.deploy(processVersion, DeploymentData.empty, graphProcess, savepointPath).isReadyWithin(100 seconds))
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
