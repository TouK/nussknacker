package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, Suite}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkDeploymentManager, FlinkStateStatus, FlinkStreamingDeploymentManagerProvider}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

trait StreamingDockerTest extends DockerTest with Matchers { self: Suite =>

  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  implicit val deploymentService: ProcessingTypeDeploymentService = new ProcessingTypeDeploymentServiceStub(List.empty)

  protected var kafkaClient: KafkaClient = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient = new KafkaClient(hostKafkaAddress, self.suiteName)
    logger.info("Kafka client created")
  }

  override def afterAll(): Unit = {
    kafkaClient.shutdown()
    logger.info("Kafka client closed")
    super.afterAll()
  }

  protected lazy val deploymentManager: FlinkDeploymentManager = FlinkStreamingDeploymentManagerProvider.defaultDeploymentManager(config).asInstanceOf[FlinkDeploymentManager]

  protected def deployProcessAndWaitIfRunning(process: EspProcess, processVersion: ProcessVersion, oldJobStatus: Option[ProcessState], savepointPath : Option[String] = None): Option[ProcessState] = {
    deployProcess(process, processVersion, oldJobStatus, savepointPath)
    eventually {
      val jobStatus = deploymentManager.findJobStatus(ProcessName(process.id)).futureValue
      logger.debug(s"Waiting for deploy: ${process.id}, $jobStatus")

      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
      jobStatus
    }
  }

  protected def deployProcess(process: EspProcess, processVersion: ProcessVersion, oldJobStatus: Option[ProcessState], savepointPath : Option[String] = None): Option[ExternalDeploymentId] = {
    deploymentManager.deploy(processVersion, DeploymentData.empty, process.toCanonicalProcess, savepointPath, oldJobStatus).futureValue
  }

  protected def cancelProcess(processId: String): Unit = {
    deploymentManager.cancel(ProcessName(processId), user = userToAct).futureValue
    eventually {
      val statusOpt = deploymentManager
        .findJobStatus(ProcessName(processId))
        .futureValue
      val runningOrDurringCancelJobs = statusOpt
        .filter(state => state.status.isRunning || state.status == SimpleStateStatus.DuringCancel)

      logger.debug(s"waiting for jobs: $processId, $statusOpt")
      if (runningOrDurringCancelJobs.nonEmpty) {
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
