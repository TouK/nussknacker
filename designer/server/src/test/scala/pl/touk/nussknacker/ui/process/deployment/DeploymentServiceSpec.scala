package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import db.util.DBIOActionInstances.DB
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.LoneElement._
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Cancel, Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{
  DataFreshnessPolicy,
  DeploymentManager,
  ProcessActionId,
  ProcessActionState,
  ProcessActionType,
  ProcessState,
  StateStatus,
  StatusDetails
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, NuScalaTestAssertions, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessesQuery
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.{existingSinkFactory, existingSourceFactory, processorId}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnActionExecutionFinished, OnDeployActionSuccess}
import pl.touk.nussknacker.ui.process.processingtypedata.{
  DefaultProcessingTypeDeploymentService,
  ProcessingTypeDataProvider
}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, DeploymentComment}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DBIOActionValues
import slick.dbio.DBIOAction

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

class DeploymentServiceSpec
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with DBIOActionValues
    with NuScalaTestAssertions
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithHsqlDbTesting
    with EitherValuesDetailedMessage {

  import TestCategories._
  import TestFactory._
  import TestProcessingTypes._
  import VersionId._

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private implicit val system: ActorSystem          = ActorSystem()
  private implicit val user: LoggedUser             = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher

  private var deploymentManager: MockDeploymentManager = _
  override protected val dbioRunner: DBIOActionRunner  = newDBIOActionRunner(testDbRef)
  private val fetchingProcessRepository                = newFetchingProcessRepository(testDbRef)
  private val futureFetchingProcessRepository          = newFutureFetchingProcessRepository(testDbRef)
  private val writeProcessRepository                   = newWriteProcessRepository(testDbRef)
  private val actionRepository                         = newActionProcessRepository(testDbRef)
  private val activityRepository                       = newProcessActivityRepository(testDbRef)

  private val processingTypeDataProvider: ProcessingTypeDataProvider[DeploymentManager, Nothing] =
    new ProcessingTypeDataProvider[DeploymentManager, Nothing] {
      override def forType(typ: ProcessingType): Option[DeploymentManager] = all.get(typ)

      override def all: Map[ProcessingType, DeploymentManager] = Map(TestProcessingTypes.Streaming -> deploymentManager)

      override def combined: Nothing = ???
    }

  private val dmDispatcher =
    new DeploymentManagerDispatcher(processingTypeDataProvider, futureFetchingProcessRepository)

  private val listener = new TestProcessChangeListener

  private val deploymentService = createDeploymentService(None)

  deploymentManager = new MockDeploymentManager(SimpleStateStatus.Running)(
    new DefaultProcessingTypeDeploymentService(TestProcessingTypes.Streaming, deploymentService)
  )

  private def createDeploymentService(scenarioStateTimeout: Option[FiniteDuration]): DeploymentService = {
    new DeploymentServiceImpl(
      dmDispatcher,
      fetchingProcessRepository,
      actionRepository,
      dbioRunner,
      processValidation,
      TestFactory.scenarioResolver,
      listener,
      scenarioStateTimeout = scenarioStateTimeout
    )
  }

  test("should return state correctly when state is deployed") {
    val processName: ProcessName = generateProcessName
    val processId: ProcessId     = prepareProcess(processName).dbioActionValues

    deploymentManager.withWaitForDeployFinish(processName) {
      deploymentService.deployProcessAsync(ProcessIdWithName(processId, processName), None, None).futureValue
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.DuringDeploy
    }

    eventually {
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.Running
    }
  }

  test("should return state correctly when state is cancelled") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withWaitForCancelFinish {
      deploymentService.cancelProcess(ProcessIdWithName(processId, processName), None)
      eventually {
        deploymentService
          .getProcessState(ProcessIdWithName(processId, processName))
          .futureValue
          .status shouldBe SimpleStateStatus.DuringCancel
      }
    }
  }

  test("should mark Action ExecutionFinished and publish an event as finished") {

    val processName: ProcessName = generateProcessName
    val (processId, actionId)    = prepareDeployedProcess(processName).dbioActionValues

    deploymentService.markActionExecutionFinished(Streaming, actionId).futureValue
    eventually {
      val action =
        actionRepository.getFinishedProcessActions(processId, Some(Set(ProcessActionType.Deploy))).dbioActionValues

      action.loneElement.state shouldBe ProcessActionState.ExecutionFinished
      listener.events.toArray.filter(_.isInstanceOf[OnActionExecutionFinished]) should have length 1
    }

  }

  test("Should mark finished process as finished") {
    val processName: ProcessName    = generateProcessName
    val (processId, deployActionId) = prepareDeployedProcess(processName).dbioActionValues

    checkIsFollowingDeploy(
      deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue,
      expected = true
    )
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager.withProcessFinished(processName, DeploymentId.fromActionId(deployActionId)) {
      // we simulate what happens when retrieveStatus is called multiple times to check only one comment is added
      (1 to 5).foreach { _ =>
        checkIsFollowingDeploy(
          deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue,
          expected = false
        )
      }
      val finishedStatus = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      finishedStatus.status shouldBe SimpleStateStatus.Finished
      finishedStatus.allowedActions shouldBe List(
        ProcessActionType.Deploy,
        ProcessActionType.Archive,
        ProcessActionType.Rename
      )
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).dbioActionValues.value
    val lastStateAction = processDetails.lastStateAction.value
    lastStateAction.actionType shouldBe ProcessActionType.Deploy
    lastStateAction.state shouldBe ProcessActionState.ExecutionFinished
    // we want to hide finished deploys
    processDetails.lastDeployedAction shouldBe empty
    activityRepository.findActivity(ProcessIdWithName(processId, processName)).futureValue.comments should have length 1

    deploymentManager.withEmptyProcessState(processName) {
      val stateAfterJobRetention =
        deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      stateAfterJobRetention.status shouldBe SimpleStateStatus.Finished
    }

    archiveProcess(processId).dbioActionValues
    deploymentService
      .getProcessState(ProcessIdWithName(processId, processName))
      .futureValue
      .status shouldBe SimpleStateStatus.Finished
  }

  test("Should finish deployment only after DeploymentManager finishes") {
    val processName: ProcessName = generateProcessName
    val processId: ProcessId     = prepareProcess(processName).dbioActionValues
    val processIdName            = ProcessIdWithName(processId, processName)

    def checkStatusAction(expectedStatus: StateStatus, expectedAction: Option[ProcessActionType]) = {
      fetchingProcessRepository
        .fetchLatestProcessDetailsForProcessId[Unit](processId)
        .dbioActionValues
        .flatMap(_.lastStateAction)
        .map(_.actionType) shouldBe expectedAction
      deploymentService.getProcessState(processIdName).futureValue.status shouldBe expectedStatus
    }

    deploymentManager.withEmptyProcessState(processName) {

      checkStatusAction(SimpleStateStatus.NotDeployed, None)
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
    val processId: ProcessId =
      prepareProcess(processName, Some(MockDeploymentManager.maxParallelism + 1)).dbioActionValues
    val processIdName = ProcessIdWithName(processId, processName)

    deploymentManager.withEmptyProcessState(processName) {
      val result = deploymentService.deployProcessAsync(processIdName, None, None).failed.futureValue
      result.getMessage shouldBe "Parallelism too large"
      deploymentManager.deploys should not contain processName
      fetchingProcessRepository
        .fetchLatestProcessDetailsForProcessId[Unit](processId)
        .dbioActionValues
        .flatMap(_.lastStateAction) shouldBe None
      listener.events shouldBe Symbol("empty")
      // during short period of time, status will be during deploy - because parallelism validation are done in the same critical section as deployment
      eventually {
        deploymentService.getProcessState(processIdName).futureValue.status shouldBe SimpleStateStatus.NotDeployed
      }
    }
  }

  test("Should return properly state when state is canceled and process is canceled") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return canceled status for canceled process with empty state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).dbioActionValues.value
    processDetails.lastStateAction.exists(_.actionType.equals(ProcessActionType.Cancel)) shouldBe true
    processDetails.history.get.head.actions.map(_.actionType) should be(
      List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    )
  }

  test("Should return canceled status for canceled process with not founded state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).dbioActionValues.value
    processDetails.lastStateAction.exists(_.actionType.equals(ProcessActionType.Cancel)) shouldBe true
    processDetails.history.get.head.actions.map(_.actionType) should be(
      List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    )
  }

  test("Should return state with warning when state is running and process is canceled") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return not deployed when engine returns any state and process hasn't action") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return DuringCancel state when is during canceled and process has CANCEL action") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.DuringCancel) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      state.status shouldBe SimpleStateStatus.DuringCancel
    }
  }

  test("Should return state with status Restarting when process has been deployed and is restarting") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    val state =
      StatusDetails(SimpleStateStatus.Restarting, None, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))

    deploymentManager.withProcessStates(processName, List(state)) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Restarting
      state.allowedActions shouldBe List(ProcessActionType.Cancel)
      state.description shouldBe "Scenario is restarting..."
    }
  }

  test("Should return state with error when state is not running and process is deployed") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return state with error when state is null and process is deployed") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return error state when state is running and process is deployed with mismatch versions") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues
    val version = Some(
      ProcessVersion(
        versionId = VersionId(2),
        processId = ProcessId(1),
        processName = ProcessName(""),
        user = "other",
        modelVersion = None
      )
    )

    deploymentManager.withProcessStateVersion(processName, SimpleStateStatus.Running, version) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      val expectedStatus = ProblemStateStatus.mismatchDeployedVersion(VersionId(2L), VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should always return process manager failure, even if some other verifications return invalid") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues
    val version = Some(
      ProcessVersion(
        versionId = VersionId(2),
        processId = ProcessId(1),
        processName = ProcessName(""),
        user = "",
        modelVersion = None
      )
    )

    // FIXME: doesnt check recover from failed verifications ???
    deploymentManager.withProcessStateVersion(processName, ProblemStateStatus.Failed, version) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      state.status shouldBe ProblemStateStatus.Failed
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  test("Should return warning state when state is running with empty version and process is deployed") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withProcessStateVersion(processName, SimpleStateStatus.Running, Option.empty) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      val expectedStatus = ProblemStateStatus.missingDeployedVersion(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return error state when failed to get state") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    // FIXME: doesnt check recover from failed future of findJobStatus ???
    deploymentManager.withProcessStateVersion(processName, ProblemStateStatus.FailedToGet, Option.empty) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue

      val expectedStatus = ProblemStateStatus.FailedToGet
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return not deployed status for process with empty state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  test("Should return not deployed status for process with not found state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  test("Should return not deployed status for process without actions and with state (it should never happen) ") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  test("Should return not deployed state for archived never deployed process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, None).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
    state.status shouldBe SimpleStateStatus.NotDeployed
  }

  test(
    "Should return not deployed state for archived never deployed process with running state (it should never happen)"
  ) {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, None).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return canceled status for archived canceled process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
    state.status shouldBe SimpleStateStatus.Canceled
  }

  test("Should return canceled status for archived canceled process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return not deployed state for unarchived never deployed process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = preparedUnArchivedProcess(processName, None).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
    state.status shouldBe SimpleStateStatus.NotDeployed
  }

  test("Should return during deploy for process in deploy in progress") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = preparedUnArchivedProcess(processName, None).dbioActionValues
    val _ = actionRepository
      .addInProgressAction(processId, ProcessActionType.Deploy, Some(VersionId(1)), None)
      .dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
    state.status shouldBe SimpleStateStatus.DuringDeploy
  }

  test("Should return valid process list") {
    prepareProcessesInProgress

    val processes = fetchingProcessRepository
      .fetchProcessesDetails[Unit](FetchProcessesDetailsQuery(isFragment = Some(false)))
      .dbioActionValues

    val resultWithCachedInProgress = deploymentService.fetchProcessStatesForProcesses(processes).futureValue

    val resultWithoutCachedInProgress = processes
      .map(baseProcess => Future.successful(baseProcess.name) zip deploymentService.getProcessState(baseProcess))
      .sequence
      .futureValue
      .toMap

    resultWithCachedInProgress shouldBe resultWithoutCachedInProgress
  }

  test("Should enrich BaseProcessDetails") {
    prepareProcessesInProgress

    val processesDetails = fetchingProcessRepository
      .fetchProcessesDetails[Unit](ProcessesQuery.empty.toRepositoryQuery)
      .dbioActionValues

    val processesDetailsWithState = deploymentService.enrichDetailsWithProcessState(processesDetails).futureValue

    processesDetailsWithState.map(_.state.map(_.status.name)) shouldBe List(
      Some("DURING_DEPLOY"),
      Some("DURING_CANCEL"),
      Some("RUNNING"),
      None
    )
  }

  test(
    "Should return not deployed status for archived never deployed process with running state (it should never happen)"
  ) {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, None).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return problem status for archived deployed process (last action deployed instead of cancel)") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Deploy)).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
    state.status shouldBe ProblemStateStatus.ArchivedShouldBeCanceled
  }

  test("Should return canceled status for unarchived process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val state = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return problem status for unarchived process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = preparedUnArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state          = deploymentService.getProcessState(ProcessIdWithName(processId, processName)).futureValue
      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("should invalidate in progress processes") {
    val processName: ProcessName = generateProcessName
    val id                       = prepareProcess(processName).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val initialStatus = SimpleStateStatus.NotDeployed
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe initialStatus
      deploymentManager.withWaitForDeployFinish(processName) {
        deploymentService.deployProcessAsync(ProcessIdWithName(id, processName), None, None).futureValue
        deploymentService
          .getProcessState(ProcessIdWithName(id, processName))
          .futureValue
          .status shouldBe SimpleStateStatus.DuringDeploy

        deploymentService.invalidateInProgressActions()
        deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe initialStatus
      }
    }
  }

  test("should return problem after occurring timeout during waiting on DM response") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues
    val processIdName            = ProcessIdWithName(processId, processName)

    val timeout            = 1.second
    val serviceWithTimeout = createDeploymentService(Some(timeout))

    val durationLongerThanTimeout = timeout.plus(patienceConfig.timeout)
    deploymentManager.withDelayBeforeStateReturn(durationLongerThanTimeout) {
      val status = serviceWithTimeout
        .getProcessState(processIdName)
        .futureValueEnsuringInnerException(durationLongerThanTimeout)
        .status
      status shouldBe ProblemStateStatus.FailedToGet
    }
  }

  test("should fail when trying to get state for fragment") {
    val processName: ProcessName = generateProcessName
    val id                       = prepareFragment(processName).dbioActionValues

    assertThrowsWithParent[FragmentStateException] {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    listener.clear()
    deploymentManager.deploys.clear()
  }

  private def checkIsFollowingDeploy(state: ProcessState, expected: Boolean) = {
    withClue(state) {
      SimpleStateStatus.DefaultFollowingDeployStatuses.contains(state.status) shouldBe expected
    }
  }

  private def prepareCanceledProcess(processName: ProcessName): DB[(ProcessId, ProcessActionId)] =
    for {
      (processId, _) <- prepareDeployedProcess(processName)
      cancelActionId <- prepareAction(processId, Cancel)
    } yield (processId, cancelActionId)

  private def prepareDeployedProcess(processName: ProcessName): DB[(ProcessId, ProcessActionId)] =
    prepareProcessWithAction(processName, Some(Deploy)).map {
      case (processId, Some(actionId)) => (processId, actionId)
      case (_, None) => throw new IllegalStateException("Deploy actionId should be defined for deployed process")
    }

  private def preparedUnArchivedProcess(
      processName: ProcessName,
      actionType: Option[ProcessActionType]
  ): DB[(ProcessId, Option[ProcessActionId])] =
    for {
      (processId, actionIdOpt) <- prepareArchivedProcess(processName, actionType)
      _                        <- writeProcessRepository.archive(processId = processId, isArchived = false)
      _                        <- actionRepository.markProcessAsUnArchived(processId = processId, initialVersionId)
    } yield (processId, actionIdOpt)

  private def prepareArchivedProcess(
      processName: ProcessName,
      actionType: Option[ProcessActionType]
  ): DB[(ProcessId, Option[ProcessActionId])] =
    for {
      (processId, actionIdOpt) <- prepareProcessWithAction(processName, actionType)
      _                        <- archiveProcess(processId)
    } yield (processId, actionIdOpt)

  private def archiveProcess(processId: ProcessId): DB[_] = {
    writeProcessRepository
      .archive(processId = processId, isArchived = true)
      .flatMap(_ => actionRepository.markProcessAsArchived(processId = processId, initialVersionId))
  }

  private def prepareProcessesInProgress = {
    val duringDeployProcessName :: duringCancelProcessName :: otherProcess :: fragmentName :: Nil =
      (1 to 4).map(_ => generateProcessName).toList

    val processIdsInProgress = for {
      (duringDeployProcessId, _) <- preparedUnArchivedProcess(duringDeployProcessName, None)
      (duringCancelProcessId, _) <- prepareDeployedProcess(duringCancelProcessName)
      _ <- actionRepository
        .addInProgressAction(duringDeployProcessId, ProcessActionType.Deploy, Some(VersionId.initialVersionId), None)
      _ <- actionRepository
        .addInProgressAction(duringCancelProcessId, ProcessActionType.Cancel, Some(VersionId.initialVersionId), None)
      _ <- prepareDeployedProcess(otherProcess)
      _ <- prepareFragment(fragmentName)
    } yield (duringDeployProcessId, duringCancelProcessId)

    val (duringDeployProcessId, duringCancelProcessId) = processIdsInProgress.dbioActionValues
    (duringDeployProcessId, duringCancelProcessId)
  }

  private def prepareProcessWithAction(
      processName: ProcessName,
      actionType: Option[ProcessActionType]
  ): DB[(ProcessId, Option[ProcessActionId])] = {
    for {
      processId   <- prepareProcess(processName)
      actionIdOpt <- DBIOAction.sequenceOption(actionType.map(prepareAction(processId, _)))
    } yield (processId, actionIdOpt)
  }

  private def prepareAction(processId: ProcessId, actionType: ProcessActionType) = {
    val comment = Some(DeploymentComment.unsafe(actionType.toString.capitalize).toComment(actionType))
    actionRepository.addInstantAction(processId, initialVersionId, actionType, comment, None).map(_.id)
  }

  private def prepareProcess(processName: ProcessName, parallelism: Option[Int] = None): DB[ProcessId] = {
    val baseBuilder = ScenarioBuilder
      .streaming(processName.value)
    val canonicalProcess = parallelism
      .map(baseBuilder.parallelism)
      .getOrElse(baseBuilder)
      .source("source", existingSourceFactory)
      .emptySink("sink", existingSinkFactory)
    val action = CreateProcessAction(
      processName,
      TestCat,
      canonicalProcess,
      Streaming,
      isFragment = false,
      forwardedUserName = None
    )
    writeProcessRepository.saveNewProcess(action).map(_.rightValue.value.processId)
  }

  private def prepareFragment(processName: ProcessName): DB[ProcessId] = {
    val canonicalProcess = ScenarioBuilder
      .fragment(processName.value)
      .emptySink("end", "end")

    val action = CreateProcessAction(
      processName,
      TestCat,
      canonicalProcess,
      Streaming,
      isFragment = true,
      forwardedUserName = None
    )

    writeProcessRepository.saveNewProcess(action).map(_.rightValue.value.processId)
  }

  private def generateProcessName = {
    ProcessName("proces_" + UUID.randomUUID())
  }

}
