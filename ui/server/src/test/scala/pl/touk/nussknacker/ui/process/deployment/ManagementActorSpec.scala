package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import org.scalatest._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.CustomProcess
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessStatus
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{MockProcessManager, newDeploymentProcessRepository, newProcessActivityRepository, newProcessRepository, newWriteProcessRepository, testCategoryName}
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.process.JobStatusService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContextExecutor, Future}

class ManagementActorSpec extends FunSuite  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with WithHsqlDbTesting {

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val user: LoggedUser = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher
  val processName: ProcessName = ProcessName("proces1")

  private val processManager = new MockProcessManager
  private val processRepository = newProcessRepository(db)
  private val writeProcessRepository = newWriteProcessRepository(db)
  private val deploymentProcessRepository = newDeploymentProcessRepository(db)
  private val activityRepository = newProcessActivityRepository(db)

  private val managementActor = system.actorOf(
      ManagementActor.props(
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
    val id = prepareCanceledProcess(processName).futureValue

    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    processManager.withNotFoundProcessState {
      jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Canceled)
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
  }

  test("Should return state with error when state is running and process is canceled") {
    val id =  prepareCanceledProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue

      state.map(_.status) shouldBe Some(SimpleStateStatus.Error)
      state.flatMap(_.description) shouldBe Some(SimpleProcessStateDefinitionManager.errorShouldNotBeRunningDescription)
    }
  }

  test("Should return state with error when state is running and process is not deployed") {
    val id = prepareProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue

      state.map(_.status) shouldBe Some(SimpleStateStatus.Error)
      state.flatMap(_.description) shouldBe Some(SimpleProcessStateDefinitionManager.errorShouldNotBeRunningDescription)
    }
  }

  test("Should return state with error when state is not running and process is deployed") {
    val id = prepareDeployedProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      val state = jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue

      state.map(_.status) shouldBe Some(SimpleStateStatus.Error)
      state.flatMap(_.description) shouldBe Some(SimpleProcessStateDefinitionManager.errorShouldRunningDescription)
    }
  }

  test("Should return state with error when state is null and process is deployed") {
    val id = prepareDeployedProcess(processName).futureValue

    processManager.withNotFoundProcessState {
      val state = jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue

      state.map(_.status) shouldBe Some(SimpleStateStatus.Error)
      state.flatMap(_.description) shouldBe Some(SimpleProcessStateDefinitionManager.errorShouldRunningDescription)
    }
  }

  test("Should return properly state when state is canceled and process is canceled") {
    val id =  prepareCanceledProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Canceled)
    }
  }

  test("Should return error state when state is running and process is deployed with mismatch versions") {
    val id =  prepareDeployedProcess(processName).futureValue
    val version = Some(ProcessVersion(versionId = 2, processName = ProcessName(""), user = "", modelVersion = None))

    processManager.withProcessStateVersion(SimpleStateStatus.Running, version) {
      val state = jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue

      state.map(_.status) shouldBe Some(SimpleStateStatus.Error)
      state.flatMap(_.description) shouldBe Some(SimpleProcessStateDefinitionManager.errorMismatchDeployedVersionDescription)
    }
  }

  test("Should return error state when state is running with empty version and process is deployed") {
    val id =  prepareDeployedProcess(processName).futureValue

    processManager.withProcessStateVersion(SimpleStateStatus.Running, Option.empty) {
      val state = jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue

      state.map(_.status) shouldBe Some(SimpleStateStatus.Error)
      state.flatMap(_.description) shouldBe Some(SimpleProcessStateDefinitionManager.errorMissingDeployedVersionDescription)
    }
  }

  test("Should return not deployed status for process with not founded state - not deployed state") {
    val id = prepareProcess(processName).futureValue
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction shouldBe None

    processManager.withNotDeployedProcessState {
      jobStatusService.retrieveJobStatus(ProcessIdWithName(id, processName)).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.NotDeployed)
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  private def isOkForDeployed(state: ProcessStatus): Boolean =
    state.status.isDuringDeploy || state.status.isRunning

  private def prepareDeployedProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      id <- prepareProcess(processName)
      _ <- deploymentProcessRepository.markProcessAsDeployed(id, 1, "stream", Some("Deployed"))
    }  yield id

  private def prepareCanceledProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      id <- prepareDeployedProcess(processName)
      _ <- deploymentProcessRepository.markProcessAsCancelled(id, 1, Some("Canceled"))
    } yield id

  private def prepareProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      _ <- writeProcessRepository.saveNewProcess(processName, testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)
      id <- processRepository.fetchProcessId(processName).map(_.get)
    } yield id
}
