package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import org.scalatest._
import pl.touk.nussknacker.engine.api.deployment.CustomProcess
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessStatus
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{MockProcessManager, newDeploymentProcessRepository, newProcessRepository, newWriteProcessRepository, newProcessActivityRepository, testCategoryName}
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.process.JobStatusService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContextExecutor, Future}

class ManagementActorSpec extends FunSuite  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with WithHsqlDbTesting {

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val user: LoggedUser = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher

  private val env = "test1"
  val processName = ProcessName("proces1")

  private val processManager = new MockProcessManager
  private val processRepository = newProcessRepository(db)
  private val writeProcessRepository = newWriteProcessRepository(db)
  private val deploymentProcessRepository = newDeploymentProcessRepository(db)
  private val activityRepository = newProcessActivityRepository(db)

  private val managementActor = system.actorOf(
      ManagementActor.props(
        env,
        Map(TestProcessingTypes.Streaming -> processManager),
        processRepository,
        deploymentProcessRepository,
        TestFactory.sampleResolver,
        ProcessChangeListener.noop
      ),
    "management"
  )

  private val jobStatusService = new JobStatusService(managementActor)

  test("should return state correctly when state is deployed") {
    val id: process.ProcessId =  prepareProcess(processName).futureValue

    processManager.withWaitForDeployFinish {
      managementActor ! Deploy(ProcessIdWithName(id, processName), user, None, None)
      jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.DuringDeploy)
    }
    eventually {
      jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }
  }

  test("Should mark finished process as finished") {
    val id: process.ProcessId = prepareDeployedProcess(processName).futureValue

    jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(isOkForDeployed) shouldBe Some(true)
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    processManager.withProcessFinished {
      //we simulate what happens when retrieveStatus is called mulitple times to check only one comment is added
      (1 to 5).foreach { _ =>
        jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(isOkForDeployed) shouldBe Some(false)
      }
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.lastDeployedAction should be (None)
    //one for deploy, one for cancel
    activityRepository.findActivity(ProcessIdWithName(id, processName)).futureValue.comments should have length 2
  }


  test("Should return canceled status for canceled process with not founded state - cleaned state") {
    val id: process.ProcessId = prepareCanceledProcess(processName).futureValue

    jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(isOkForDeployed) shouldBe Some(true)
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    processManager.withCleanedProcessState {
      jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Canceled)
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
  }

  private def isOkForDeployed(state: ProcessStatus): Boolean =
    state.status.isDuringDeploy || state.status.isRunning

  private def prepareDeployedProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      id <- prepareProcess(processName)
      _ <- deploymentProcessRepository.markProcessAsDeployed(id, 1, "stream", env, Some("Deployed"))
    }  yield id

  private def prepareCanceledProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      id <- prepareDeployedProcess(processName)
      _ <- deploymentProcessRepository.markProcessAsCancelled(id, 1, "stream", Some("Canceled"))
    } yield id

  private def prepareProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      _ <- writeProcessRepository.saveNewProcess(processName, testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)
      id <- processRepository.fetchProcessId(processName).map(_.get)
    } yield id
}
