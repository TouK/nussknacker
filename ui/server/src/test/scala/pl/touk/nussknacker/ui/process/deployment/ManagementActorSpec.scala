package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import org.scalatest._
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, ProcessActionType, ProcessState}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.management.{FlinkProcessStateDefinitionManager, FlinkStateStatus}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{MockDeploymentManager, mapProcessingTypeDataProvider, newActionProcessRepository, newDBRepositoryManager, newFetchingProcessRepository, newProcessActivityRepository, newWriteProcessRepository, processResolving, testCategoryName}
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.{DBProcessService, NewProcessPreparer, ConfigProcessCategoryService}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import java.time
import scala.concurrent.{ExecutionContextExecutor, Future}

class ManagementActorSpec extends FunSuite with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with WithHsqlDbTesting {

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val user: LoggedUser = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher
  val processName: ProcessName = ProcessName("proces1")

  private val deploymentManager = new MockDeploymentManager
  private val repositoryManager = newDBRepositoryManager(db)
  private val fetchingProcessRepository = newFetchingProcessRepository(db)
  private val writeProcessRepository = newWriteProcessRepository(db)
  private val actionRepository = newActionProcessRepository(db)
  private val activityRepository = newProcessActivityRepository(db)
  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.config)

  val newProcessPreparer = new NewProcessPreparer(
    mapProcessingTypeDataProvider("streaming" ->  ProcessTestData.processDefinition),
    mapProcessingTypeDataProvider("streaming" -> (_ => StreamMetaData(None))),
    mapProcessingTypeDataProvider("streaming" -> Map.empty)
  )

  private val managementActor = system.actorOf(
      ManagementActor.props(
        mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> deploymentManager),
        fetchingProcessRepository,
        actionRepository,
        TestFactory.sampleResolver,
        ProcessChangeListener.noop
      ),
    "management"
  )

  private val processService = new DBProcessService(
    managementActor, time.Duration.ofMinutes(1), newProcessPreparer, processCategoryService, processResolving,
    repositoryManager, fetchingProcessRepository, actionRepository, writeProcessRepository
  )

  test("should return state correctly when state is deployed") {
    val id: ProcessId =  prepareProcess(processName).futureValue

    deploymentManager.withWaitForDeployFinish {
      managementActor ! Deploy(ProcessIdWithName(id, processName), user, None, None)
      processService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringDeploy
    }
    eventually {
      processService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Running
    }
  }

  test("Should mark finished process as finished") {
    val id: ProcessId = prepareDeployedProcess(processName).futureValue

    isFollowingDeploy(processService.getProcessState(ProcessIdWithName(id, processName)).futureValue) shouldBe true
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    deploymentManager.withProcessFinished {
      //we simulate what happens when retrieveStatus is called mulitple times to check only one comment is added
      (1 to 5).foreach { _ =>
        isFollowingDeploy(processService.getProcessState(ProcessIdWithName(id, processName)).futureValue) shouldBe false
      }
      val finishedStatus = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      finishedStatus.status shouldBe SimpleStateStatus.Finished
      finishedStatus.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive)

    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.lastDeployedAction should be (None)
    //one for deploy, one for cancel
    activityRepository.findActivity(ProcessIdWithName(id, processName)).futureValue.comments should have length 2
  }

  test("Should return properly state when state is canceled and process is canceled") {
    val id =  prepareCanceledProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      processService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return canceled status for canceled process with empty state - cleaned state") {
    val id = prepareCanceledProcess(processName).futureValue

    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    deploymentManager.withEmptyProcessState {
      processService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return canceled status for canceled process with not founded state - cleaned state") {
    val id = prepareCanceledProcess(processName).futureValue

    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    deploymentManager.withEmptyProcessState {
      processService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return state with warning when state is running and process is canceled") {
    val id =  prepareCanceledProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.stoppingWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(true))
    }
  }

  test("Should return state with warning when state is running and process is not deployed") {
    val id = prepareProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(false))
    }
  }

  test("Should return state with warning when state is during canceled and process hasn't action") {
    val id = prepareProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.DuringCancel) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage)
    }
  }

  test("Should return state with error when state is finished and process hasn't action") {
    val id = prepareProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Finished) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage)
    }
  }

  test("Should return state with error when state is restarting and process hasn't action") {
    val id = prepareProcess(processName).futureValue

    val state = ProcessState("12", FlinkStateStatus.Restarting, Some(ProcessVersion.empty), FlinkProcessStateDefinitionManager)

    deploymentManager.withProcessState(Some(state)) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      //See comment in ManagementActor.handleState...
      state.status shouldBe FlinkStateStatus.Restarting
      state.allowedActions shouldBe List(ProcessActionType.Cancel)
      state.description shouldBe Some("Scenario is restarting...")

    }
  }

  test("Should return state with error when state is not running and process is deployed") {
    val id = prepareDeployedProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription)
    }
  }

  test("Should return state with error when state is null and process is deployed") {
    val id = prepareDeployedProcess(processName).futureValue

    deploymentManager.withEmptyProcessState {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription)
    }
  }

  test("Should return error state when state is running and process is deployed with mismatch versions") {
    val id =  prepareDeployedProcess(processName).futureValue
    val version = Some(ProcessVersion(versionId = 2, processId = ProcessId(1), processName = ProcessName(""), user = "", modelVersion = None))

    deploymentManager.withProcessStateVersion(SimpleStateStatus.Running, version) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.mismatchDeployedVersionDescription)
    }
  }

  test("Should always return process manager failure, even if some other verifications return invalid") {
    val id =  prepareDeployedProcess(processName).futureValue
    val version = Some(ProcessVersion(versionId = 2, processId = ProcessId(1), processName = ProcessName(""), user = "", modelVersion = None))

    deploymentManager.withProcessStateVersion(SimpleStateStatus.Failed, version) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Failed
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  test("Should return warning state when state is running with empty version and process is deployed") {
    val id =  prepareDeployedProcess(processName).futureValue

    deploymentManager.withProcessStateVersion(SimpleStateStatus.Running, Option.empty) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.missingDeployedVersionDescription)
    }
  }

  test("Should return not deployed status for process with empty state - not deployed state") {
    val id = prepareProcess(processName).futureValue
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction shouldBe None

    deploymentManager.withEmptyProcessState {
      processService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return not deployed status for process with not found state - not deployed state") {
    val id = prepareProcess(processName).futureValue
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction shouldBe None

    deploymentManager.withEmptyProcessState {
      processService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return NotDeployed state for archived process with missing state") {
    val id = prepareArchivedProcess(processName).futureValue
    deploymentManager.withEmptyProcessState {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return NotDeployed state for unarchived process with missing state") {
    val id = prepareUnArchivedProcess(processName).futureValue
    deploymentManager.withEmptyProcessState {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return any status for archived process with any available state") {
    val id = prepareArchivedProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return warning status for archived process with running state") {
    val id = prepareArchivedProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = processService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.stoppingWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  private def isFollowingDeploy(state: ProcessState): Boolean = state.isDeployed

  private def prepareDeployedProcess(processName: ProcessName): Future[ProcessId] =
    for {
      id <- prepareProcess(processName)
      _ <- actionRepository.markProcessAsDeployed(id, 1, "stream", Some("Deployed"))
    }  yield id

  private def prepareCanceledProcess(processName: ProcessName): Future[ProcessId] =
    for {
      id <- prepareDeployedProcess(processName)
      _ <- actionRepository.markProcessAsCancelled(id, 1, Some("Canceled"))
    } yield id

  private def prepareProcess(processName: ProcessName): Future[ProcessId] = {
    val action = CreateProcessAction(processName, testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)
    for {
      _ <- repositoryManager.runInTransaction(writeProcessRepository.saveNewProcess(action))
      id <- fetchingProcessRepository.fetchProcessId(processName).map(_.get)
    } yield id
  }


  private def prepareArchivedProcess(processName: ProcessName): Future[ProcessId] = {
      for {
        id <- prepareProcess(processName)
        _ <- repositoryManager.runInTransaction(
          writeProcessRepository.archive(processId = id, isArchived = true),
          actionRepository.markProcessAsArchived(processId = id, 1)
        )
      } yield id
  }

  private def prepareUnArchivedProcess(processName: ProcessName): Future[ProcessId] = {
    for {
      id <- prepareProcess(processName)
      _ <- repositoryManager.runInTransaction(
        actionRepository.markProcessAsArchived(processId = id, 1),
        actionRepository.markProcessAsUnArchived(processId = id, 1)
      )
    } yield id
  }
}
