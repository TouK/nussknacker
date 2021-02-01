package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import org.scalatest._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, ProcessActionType, ProcessState}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.{FlinkProcessStateDefinitionManager, FlinkStateStatus}
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{MockProcessManager, mapProcessingTypeDataProvider, newActionProcessRepository, newProcessActivityRepository, newProcessRepository, newWriteProcessRepository, testCategoryName}
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time
import scala.concurrent.{ExecutionContextExecutor, Future}

class ManagementActorSpec extends FunSuite  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with WithHsqlDbTesting {

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val user: LoggedUser = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher
  val processName: ProcessName = ProcessName("proces1")

  private val processManager = new MockProcessManager
  private val processRepository = newProcessRepository(db)
  private val writeProcessRepository = newWriteProcessRepository(db)
  private val actionRepository = newActionProcessRepository(db)
  private val activityRepository = newProcessActivityRepository(db)

  private val managementActor = system.actorOf(
      ManagementActor.props(
        mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> processManager),
        processRepository,
        actionRepository,
        TestFactory.sampleResolver,
        ProcessChangeListener.noop
      ),
    "management"
  )

  private val managementService = new ManagementService(managementActor, time.Duration.ofMinutes(1))

  test("should return state correctly when state is deployed") {
    val id: process.ProcessId =  prepareProcess(processName).futureValue

    processManager.withWaitForDeployFinish {
      managementActor ! Deploy(ProcessIdWithName(id, processName), user, None, None)
      managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringDeploy
    }
    eventually {
      managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Running
    }
  }

  test("Should mark finished process as finished") {
    val id: process.ProcessId = prepareDeployedProcess(processName).futureValue

    isFollowingDeploy(managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue) shouldBe true
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    processManager.withProcessFinished {
      //we simulate what happens when retrieveStatus is called mulitple times to check only one comment is added
      (1 to 5).foreach { _ =>
        isFollowingDeploy(managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue) shouldBe false
      }
      val finishedStatus = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      finishedStatus.status shouldBe SimpleStateStatus.Finished
      finishedStatus.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive)

    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.lastDeployedAction should be (None)
    //one for deploy, one for cancel
    activityRepository.findActivity(ProcessIdWithName(id, processName)).futureValue.comments should have length 2
  }

  test("Should return properly state when state is canceled and process is canceled") {
    val id =  prepareCanceledProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return canceled status for canceled process with empty state - cleaned state") {
    val id = prepareCanceledProcess(processName).futureValue

    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    processManager.withEmptyProcessState {
      managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return canceled status for canceled process with not founded state - cleaned state") {
    val id = prepareCanceledProcess(processName).futureValue

    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    processManager.withProcessStateStatus(SimpleStateStatus.NotFound) {
      managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return state with warning when state is running and process is canceled") {
    val id =  prepareCanceledProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.stoppingWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(true))
    }
  }

  test("Should return state with warning when state is running and process is not deployed") {
    val id = prepareProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(false))
    }
  }

  test("Should return state with warning when state is during canceled and process hasn't action") {
    val id = prepareProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.DuringCancel) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage)
    }
  }

  test("Should return state with error when state is finished and process hasn't action") {
    val id = prepareProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Finished) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage)
    }
  }

  test("Should return state with error when state is restarting and process hasn't action") {
    val id = prepareProcess(processName).futureValue

    val state = ProcessState("12", FlinkStateStatus.Restarting, Some(ProcessVersion.empty), FlinkProcessStateDefinitionManager)

    processManager.withProcessState(Some(state)) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      //See comment in ManagementActor.handleState...
      state.status shouldBe FlinkStateStatus.Restarting
      state.allowedActions shouldBe List(ProcessActionType.Cancel)
      state.description shouldBe Some("Process is restarting...")

    }
  }

  test("Should return state with error when state is not running and process is deployed") {
    val id = prepareDeployedProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription)
    }
  }

  test("Should return state with error when state is null and process is deployed") {
    val id = prepareDeployedProcess(processName).futureValue

    processManager.withEmptyProcessState {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription)
    }
  }

  test("Should return error state when state is running and process is deployed with mismatch versions") {
    val id =  prepareDeployedProcess(processName).futureValue
    val version = Some(ProcessVersion(versionId = 2, processName = ProcessName(""), user = "", modelVersion = None))

    processManager.withProcessStateVersion(SimpleStateStatus.Running, version) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.mismatchDeployedVersionDescription)
    }
  }

  test("Should always return process manager failure, even if some other verifications return invalid") {
    val id =  prepareDeployedProcess(processName).futureValue
    val version = Some(ProcessVersion(versionId = 2, processName = ProcessName(""), user = "", modelVersion = None))

    processManager.withProcessStateVersion(SimpleStateStatus.Failed, version) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Failed
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  test("Should return warning state when state is running with empty version and process is deployed") {
    val id =  prepareDeployedProcess(processName).futureValue

    processManager.withProcessStateVersion(SimpleStateStatus.Running, Option.empty) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.missingDeployedVersionDescription)
    }
  }

  test("Should return not deployed status for process with empty state - not deployed state") {
    val id = prepareProcess(processName).futureValue
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction shouldBe None

    processManager.withEmptyProcessState {
      managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return not deployed status for process with not found state - not deployed state") {
    val id = prepareProcess(processName).futureValue
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction shouldBe None

    processManager.withProcessStateStatus(SimpleStateStatus.NotFound) {
      managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = processRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return NotFound state for archived process with missing state") {
    val id = prepareArchivedProcess(processName).futureValue

    processManager.withEmptyProcessState {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.NotFound
    }
  }

  test("Should return any status for archived process with any available state") {
    val id = prepareArchivedProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return warning status for archived process with running state") {
    val id = prepareArchivedProcess(processName).futureValue

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = managementService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.stoppingWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  private def isFollowingDeploy(state: ProcessState): Boolean = state.isDeployed

  private def prepareDeployedProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      id <- prepareProcess(processName)
      _ <- actionRepository.markProcessAsDeployed(id, 1, "stream", Some("Deployed"))
    }  yield id

  private def prepareCanceledProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      id <- prepareDeployedProcess(processName)
      _ <- actionRepository.markProcessAsCancelled(id, 1, Some("Canceled"))
    } yield id

  private def prepareProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      _ <- writeProcessRepository.saveNewProcess(processName, testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)
      id <- processRepository.fetchProcessId(processName).map(_.get)
    } yield id


  private def prepareArchivedProcess(processName: ProcessName): Future[process.ProcessId] =
    for {
      id <- prepareProcess(processName)
      _ <- actionRepository.markProcessAsArchived(id, 1, Some("Archived"))
    }  yield id
}
