package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.deployment.CustomProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{MockProcessManager, newDeploymentProcessRepository, newProcessRepository, newWriteProcessRepository, testCategoryName}
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.process.JobStatusService
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.ExecutionContextExecutor

class ManagementActorSpec extends FunSuite  with Matchers with ScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with WithHsqlDbTesting {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))
  private implicit val system: ActorSystem = ActorSystem()
  private implicit val user: LoggedUser = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher

  private val env = "test1"
  val processName = ProcessName("proces1")

  private val processManager = new MockProcessManager
  private val processRepository = newProcessRepository(db)
  private val writeProcessRepository = newWriteProcessRepository(db)
  private val deploymentProcessRepository = newDeploymentProcessRepository(db)
  private val managementActor = ManagementActor(env,
    Map(TestProcessingTypes.Streaming -> processManager), processRepository, deploymentProcessRepository, TestFactory.sampleResolver)

  private val jobStatusService = new JobStatusService(managementActor)

  test("Should mark finished process as finished") {

    val id: process.ProcessId = prepareDeployedProcess(processName)

    jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.isOkForDeployed) shouldBe Some(true)
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.currentlyDeployedAt should have length 1

    processManager.withProcessFinished {
      jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.isOkForDeployed) shouldBe Some(false)
    }

    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.currentlyDeployedAt shouldBe List()

  }

  private def prepareDeployedProcess(processName: ProcessName) = {
    (for {
      _ <- writeProcessRepository.saveNewProcess(processName, testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)
      id <- processRepository.fetchProcessId(processName).map(_.get)
      _ <- deploymentProcessRepository.markProcessAsDeployed(id, 1, "stream", env, Some("one"))
    } yield id).futureValue
  }
}
