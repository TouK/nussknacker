package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState, StateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.{FlinkProcessStateDefinitionManager, FlinkStateStatus}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnDeployActionSuccess
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.deployment.DeploymentActionsInProgressActor.ActorBasedDeploymentActionsInProgressRepository
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

class DeploymentServiceSpec extends AnyFunSuite with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with WithHsqlDbTesting {

  import TestCategories._
  import TestFactory._
  import TestProcessingTypes._
  import VersionId._

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val user: LoggedUser = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher

  private val deploymentManager = new MockDeploymentManager
  private val repositoryManager = newDBRepositoryManager(db)
  private val fetchingProcessRepository = newFetchingProcessRepository(db)
  private val writeProcessRepository = newWriteProcessRepository(db)
  private val actionRepository = newActionProcessRepository(db)
  private val activityRepository = newProcessActivityRepository(db)
  private val dmDispatcher = new DeploymentManagerDispatcher(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> deploymentManager), fetchingProcessRepository)

  private val listener = new TestProcessChangeListener

  private val deploymentActionsInProgressActor = system.actorOf(DeploymentActionsInProgressActor.props, "deploymentActionsInProgress")

  private val deploymentActionsInProgressRepository = new ActorBasedDeploymentActionsInProgressRepository(deploymentActionsInProgressActor, 1 minute)

  private val deploymentService = new DeploymentServiceImpl(dmDispatcher, fetchingProcessRepository, actionRepository,
    TestFactory.scenarioResolver, deploymentActionsInProgressRepository, listener)

  val newProcessPreparer = new NewProcessPreparer(
    mapProcessingTypeDataProvider("streaming" -> ProcessTestData.streamingTypeSpecificInitialData),
    mapProcessingTypeDataProvider("streaming" -> Map.empty)
  )

  test("should return state correctly when state is deployed") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId =  prepareProcess(processName).futureValue

    deploymentManager.withWaitForDeployFinish(processName) {
      deploymentService.deployProcessAsync(ProcessIdWithName(id, processName), None, None).futureValue
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringDeploy
    }

    eventually {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Running
    }
  }

  test("should return state correctly when state is cancelled") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId = prepareDeployedProcess(processName).futureValue

    deploymentManager.withWaitForCancelFinish {
      deploymentService.cancelProcess(ProcessIdWithName(id, processName), None)
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringCancel
    }
  }

  test("Should mark finished process as finished") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId = prepareDeployedProcess(processName).futureValue

    checkIsFollowingDeploy(deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue, expected = true)
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    deploymentManager.withProcessFinished {
      //we simulate what happens when retrieveStatus is called mulitple times to check only one comment is added
      (1 to 5).foreach { _ =>
        checkIsFollowingDeploy(deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue, expected = false)
      }
      val finishedStatus = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
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

  test("Should finish deployment only after DeploymentManager finishes") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId = prepareProcess(processName).futureValue
    val processIdName = ProcessIdWithName(id, processName)

    def checkStatusAction(expectedStatus: StateStatus, expectedAction: Option[ProcessActionType]) = {
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.flatMap(_.lastAction).map(_.action) shouldBe expectedAction
      deploymentService.getProcessState(processIdName).futureValue.status shouldBe expectedStatus
    }

    val statusFromDeploymentManager = SimpleStateStatus.NotDeployed
    deploymentManager.withProcessState(None) {

      checkStatusAction(statusFromDeploymentManager, None)
      deploymentManager.withWaitForDeployFinish(processName) {
        deploymentService.deployProcessAsync(processIdName, None, None).futureValue
        checkStatusAction(SimpleStateStatus.DuringDeploy, None)
        listener.events shouldBe Symbol("empty")
      }
    }
    eventually {
      checkStatusAction(SimpleStateStatus.Running, Some(ProcessActionType.Deploy))
      listener.events.toArray.filter(_.isInstanceOf[OnDeployActionSuccess]) should have length 1
    }
  }

  test("Should skip notifications and deployment on validation errors") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId = prepareProcess(processName, Some(MockDeploymentManager.maxParallelism + 1)).futureValue
    val processIdName = ProcessIdWithName(id, processName)

    deploymentManager.withProcessState(None) {
      val result = deploymentService.deployProcessAsync(processIdName, None, None).failed.futureValue
      result.getMessage shouldBe "Parallelism too large"
      deploymentManager.deploys shouldBe Symbol("empty")
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.flatMap(_.lastAction) shouldBe None
      listener.events shouldBe Symbol("empty")
      // during short period of time, status will be during deploy - because parallelism validation are done in the same critical section as deployment
      eventually {
        deploymentService.getProcessState(processIdName).futureValue.status shouldBe SimpleStateStatus.NotDeployed
      }
    }
  }

  test("Should return properly state when state is canceled and process is canceled") {
    val processName: ProcessName = generateProcessName
    val id =  prepareCanceledProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return canceled status for canceled process with empty state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val id = prepareCanceledProcess(processName).futureValue

    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    deploymentManager.withEmptyProcessState {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return canceled status for canceled process with not founded state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val id = prepareCanceledProcess(processName).futureValue

    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction should not be None

    deploymentManager.withEmptyProcessState {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return state with warning when state is running and process is canceled") {
    val processName: ProcessName = generateProcessName
    val id =  prepareCanceledProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.stoppingWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(true))
    }
  }

  test("Should return state with warning when state is running and process is not deployed") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(false))
    }
  }

  test("Should return state with warning when state is during canceled and process hasn't action") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.DuringCancel) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage)
    }
  }

  test("Should return DuringCancel state when is during canceled and process has CANCEL action") {
    val processName: ProcessName = generateProcessName
    val id = prepareCanceledProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.DuringCancel) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.DuringCancel
    }
  }

  test("Should return state with error when state is finished and process hasn't action") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Finished) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage)
    }
  }

  test("Should return state with warning when state is restarting and process hasn't had action (couldn't be even deployed)") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).futureValue

    val state = FlinkProcessStateDefinitionManager.processState(FlinkStateStatus.Restarting, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))

    deploymentManager.withProcessState(Some(state)) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some("Scenario state error - no actions found!")
    }
  }

  test("Should return state with status Restarting when process has been deployed and is restarting") {
    val processName: ProcessName = generateProcessName
    val id = prepareDeployedProcess(processName).futureValue

    val state = FlinkProcessStateDefinitionManager.processState(FlinkStateStatus.Restarting, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))

    deploymentManager.withProcessState(Some(state)) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe FlinkStateStatus.Restarting
      state.allowedActions shouldBe List(ProcessActionType.Cancel)
      state.description shouldBe Some("Scenario is restarting...")
    }
  }

  test("Should return state with error when state is not running and process is deployed") {
    val processName: ProcessName = generateProcessName
    val id = prepareDeployedProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription)
    }
  }

  test("Should return state with error when state is null and process is deployed") {
    val processName: ProcessName = generateProcessName
    val id = prepareDeployedProcess(processName).futureValue

    deploymentManager.withEmptyProcessState {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription)
    }
  }

  test("Should return error state when state is running and process is deployed with mismatch versions") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).futureValue
    val version = Some(ProcessVersion(versionId = VersionId(2), processId = ProcessId(1), processName = ProcessName(""), user = "", modelVersion = None))

    deploymentManager.withProcessStateVersion(SimpleStateStatus.Running, version) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.mismatchDeployedVersionDescription)
    }
  }

  test("Should always return process manager failure, even if some other verifications return invalid") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).futureValue
    val version = Some(ProcessVersion(versionId = VersionId(2), processId = ProcessId(1), processName = ProcessName(""), user = "", modelVersion = None))

    deploymentManager.withProcessStateVersion(SimpleStateStatus.Failed, version) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Failed
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  test("Should return warning state when state is running with empty version and process is deployed") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).futureValue

    deploymentManager.withProcessStateVersion(SimpleStateStatus.Running, Option.empty) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.missingDeployedVersionDescription)
    }
  }

  test("Should return error state when failed to get state") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).futureValue

    deploymentManager.withProcessStateVersion(SimpleStateStatus.FailedToGet, Option.empty) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Error
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.deployFailedIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription)
    }
  }

  test("Should return not deployed status for process with empty state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).futureValue
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction shouldBe None

    deploymentManager.withEmptyProcessState {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return not deployed status for process with not found state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).futureValue
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get.lastAction shouldBe None

    deploymentManager.withEmptyProcessState {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).futureValue.get
    processDetails.lastAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return NotDeployed state for archived process with missing state") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName).futureValue
    deploymentManager.withEmptyProcessState {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return NotDeployed state for unarchived process with missing state") {
    val processName: ProcessName = generateProcessName
    val id = prepareUnArchivedProcess(processName).futureValue
    deploymentManager.withEmptyProcessState {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return any status for archived process with any available state") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return warning status for archived process with running state") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName).futureValue

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Warning
      state.icon shouldBe Some(SimpleProcessStateDefinitionManager.stoppingWarningIcon)
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  test("should handle correctly concurrent >1 deploy") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId = prepareProcess(processName).futureValue

    deploymentManager.withWaitForDeployFinish(processName) {
      val firstDeployResult = deploymentService.deployProcessAsync(ProcessIdWithName(id, processName), None, None).futureValue
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringDeploy

      // in the normal situation, scenario probably will have some properties changed for the next deployment
      deploymentManager.withWaitForDeployFinish(processName) {
        deploymentService.deployProcessAsync(ProcessIdWithName(id, processName), None, None).futureValue
      }.futureValue

      eventually {
        listener.events should have size 1
      }
      all(listener.events.toArray.toList) should matchPattern {
        case _: OnDeployActionSuccess =>
      }

      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringDeploy
      firstDeployResult
    }.futureValue

    eventually {
      listener.events should have size 2
    }
    all(listener.events.toArray.toList) should matchPattern {
      case _: OnDeployActionSuccess =>
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    listener.clear()
    deploymentManager.deploys.clear()
  }

  private def checkIsFollowingDeploy(state: ProcessState, expected: Boolean) = {
    withClue(state) {
      state.isDeployed shouldBe expected
    }
  }

  private def prepareDeployedProcess(processName: ProcessName): Future[ProcessId] =
    for {
      id <- prepareProcess(processName)
      _ <- actionRepository.markProcessAsDeployed(id, initialVersionId, "stream", Some(DeploymentComment.unsafe("Deployed")))
    }  yield id

  private def prepareCanceledProcess(processName: ProcessName): Future[ProcessId] =
    for {
      id <- prepareDeployedProcess(processName)
      _ <- actionRepository.markProcessAsCancelled(id, initialVersionId, Some(DeploymentComment.unsafe("Canceled")))
    } yield id

  private def prepareProcess(processName: ProcessName, parallelism: Option[Int] = None): Future[ProcessId] = {
    val canonicalProcess = CanonicalProcess(MetaData(processName.value, StreamMetaData(parallelism)), Nil)
    val action = CreateProcessAction(processName, TestCat, canonicalProcess, Streaming, isSubprocess = false)

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
          actionRepository.markProcessAsArchived(processId = id, initialVersionId)
        )
      } yield id
  }

  private def prepareUnArchivedProcess(processName: ProcessName): Future[ProcessId] = {
    for {
      id <- prepareProcess(processName)
      _ <- repositoryManager.runInTransaction(
        actionRepository.markProcessAsArchived(processId = id, initialVersionId),
        actionRepository.markProcessAsUnArchived(processId = id, initialVersionId)
      )
    } yield id
  }

  private def generateProcessName = {
    ProcessName("proces_" + UUID.randomUUID())
  }
}
