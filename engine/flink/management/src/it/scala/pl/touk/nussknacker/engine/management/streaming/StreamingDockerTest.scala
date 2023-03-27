package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, Suite}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkStateStatus, FlinkStreamingDeploymentManagerProvider}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.SttpBackend

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

trait StreamingDockerTest extends DockerTest with Matchers { self: Suite =>

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  implicit val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
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

  protected lazy val deploymentManager: DeploymentManager =
    FlinkStreamingDeploymentManagerProvider.defaultDeploymentManager(ConfigWithUnresolvedVersion(config))

  protected def deployProcessAndWaitIfRunning(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None): Assertion = {
    deployProcess(process, processVersion, savepointPath)
    eventually {
      val jobStatus = deploymentManager.getProcessState(ProcessName(process.id)).futureValue.value
      logger.debug(s"Waiting for deploy: ${process.id}, $jobStatus")

      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
    }
  }

  protected def deployProcess(process: CanonicalProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None): Option[ExternalDeploymentId] = {
    deploymentManager.deploy(processVersion, DeploymentData.empty, process, savepointPath).futureValue
  }

  protected def cancelProcess(processId: String): Unit = {
    deploymentManager.cancel(ProcessName(processId), user = userToAct).futureValue
    eventually {
      val statusOpt = deploymentManager
        .getProcessState(ProcessName(processId))
        .futureValue.value
      val runningOrDurringCancelJobs = statusOpt
        .filter(state => state.status.isRunning || state.status == SimpleStateStatus.DuringCancel)

      logger.debug(s"waiting for jobs: $processId, $statusOpt")
      if (runningOrDurringCancelJobs.nonEmpty) {
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
