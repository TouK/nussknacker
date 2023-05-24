package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import db.util.DBIOActionInstances.DB
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Archive, Cancel, Deploy, Pause, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessActionType, ProcessState, StateStatus, StatusDetails}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.FlinkProcessStateDefinitionManager
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, NussknackerAssertions, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.{existingSinkFactory, existingSourceFactory, processorId}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnDeployActionSuccess
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, DeploymentComment}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DBIOActionValues
import slick.dbio.DBIOAction

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class DeploymentServiceSpec extends AnyFunSuite with Matchers with PatientScalaFutures with DBIOActionValues with NussknackerAssertions
  with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with WithHsqlDbTesting with EitherValuesDetailedMessage {

  import TestCategories._
  import TestFactory._
  import TestProcessingTypes._
  import VersionId._

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val user: LoggedUser = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher

  private val deploymentManager = new MockDeploymentManager
  override protected val dbioRunner: DBIOActionRunner = newDBIOActionRunner(db)
  private val fetchingProcessRepository = newFetchingProcessRepository(db)
  private val futureFetchingProcessRepository = newFutureFetchingProcessRepository(db)
  private val writeProcessRepository = newWriteProcessRepository(db)
  private val actionRepository = newActionProcessRepository(db)
  private val activityRepository = newProcessActivityRepository(db)
  private val dmDispatcher = new DeploymentManagerDispatcher(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> deploymentManager), futureFetchingProcessRepository)

  private val listener = new TestProcessChangeListener

  private val deploymentService = createDeploymentService(None)

  private def createDeploymentService(scenarioStateTimeout: Option[FiniteDuration]): DeploymentService = {
    new DeploymentServiceImpl(dmDispatcher, fetchingProcessRepository, actionRepository, dbioRunner, processValidation, TestFactory.scenarioResolver, listener, scenarioStateTimeout = scenarioStateTimeout)
  }

  test("should return state correctly when state is deployed") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId =  prepareProcess(processName).dbioActionValues

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
    val id: ProcessId = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withWaitForCancelFinish {
      deploymentService.cancelProcess(ProcessIdWithName(id, processName), None)
      eventually {
        deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringCancel
      }
    }
  }

  test("Should mark finished process as finished") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId = prepareDeployedProcess(processName).dbioActionValues

    checkIsFollowingDeploy(deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue, expected = true)
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value.lastStateAction should not be None

    deploymentManager.withProcessFinished(processName) {
      //we simulate what happens when retrieveStatus is called multiple times to check only one comment is added
      (1 to 5).foreach { _ =>
        checkIsFollowingDeploy(deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue, expected = false)
      }
      val finishedStatus = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      finishedStatus.status shouldBe SimpleStateStatus.Finished
      finishedStatus.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive)
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value
    processDetails.lastStateAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.lastDeployedAction should be(None)
    //one for deploy, one for cancel
    activityRepository.findActivity(ProcessIdWithName(id, processName)).futureValue.comments should have length 2
  }

  test("Should finish deployment only after DeploymentManager finishes") {
    val processName: ProcessName = generateProcessName
    val id: ProcessId = prepareProcess(processName).dbioActionValues
    val processIdName = ProcessIdWithName(id, processName)

    def checkStatusAction(expectedStatus: StateStatus, expectedAction: Option[ProcessActionType]) = {
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.flatMap(_.lastStateAction).map(_.action) shouldBe expectedAction
      deploymentService.getProcessState(processIdName).futureValue.status shouldBe expectedStatus
    }

    val statusFromDeploymentManager = SimpleStateStatus.NotDeployed
    deploymentManager.withEmptyProcessState(processName) {

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
    val id: ProcessId = prepareProcess(processName, Some(MockDeploymentManager.maxParallelism + 1)).dbioActionValues
    val processIdName = ProcessIdWithName(id, processName)

    deploymentManager.withEmptyProcessState(processName) {
      val result = deploymentService.deployProcessAsync(processIdName, None, None).failed.futureValue
      result.getMessage shouldBe "Parallelism too large"
      deploymentManager.deploys should not contain processName
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.flatMap(_.lastStateAction) shouldBe None
      listener.events shouldBe Symbol("empty")
      // during short period of time, status will be during deploy - because parallelism validation are done in the same critical section as deployment
      eventually {
        deploymentService.getProcessState(processIdName).futureValue.status shouldBe SimpleStateStatus.NotDeployed
      }
    }
  }

  test("Should return properly state when state is canceled and process is canceled") {
    val processName: ProcessName = generateProcessName
    val id =  prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return canceled status for canceled process with empty state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val id = prepareCanceledProcess(processName).dbioActionValues

    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value.lastStateAction should not be None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value
    processDetails.lastStateAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return canceled status for canceled process with not founded state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val id = prepareCanceledProcess(processName).dbioActionValues

    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value.lastStateAction should not be None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value
    processDetails.lastStateAction should not be None
    processDetails.isCanceled shouldBe true
    processDetails.history.head.actions.map(_.action) should be (List(ProcessActionType.Cancel, ProcessActionType.Deploy))
  }

  test("Should return state with warning when state is running and process is canceled") {
    val processName: ProcessName = generateProcessName
    val id =  prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return not deployed when engine returns any state and process hasn't action") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return DuringCancel state when is during canceled and process has CANCEL action") {
    val processName: ProcessName = generateProcessName
    val id = prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.DuringCancel) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.DuringCancel
    }
  }

  test("Should return state with error when state is finished and process hasn't action") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Finished) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.processWithoutAction
      state.status shouldBe expectedStatus
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return state with warning when state is restarting and process hasn't had action (couldn't be even deployed)") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).dbioActionValues

    val state = StatusDetails(SimpleStateStatus.Restarting, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))

    deploymentManager.withProcessState(processName, Some(state)) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.processWithoutAction
      state.status shouldBe expectedStatus
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return state with status Restarting when process has been deployed and is restarting") {
    val processName: ProcessName = generateProcessName
    val id = prepareDeployedProcess(processName).dbioActionValues

    val state = StatusDetails(SimpleStateStatus.Restarting, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))

    deploymentManager.withProcessState(processName, Some(state)) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe SimpleStateStatus.Restarting
      state.allowedActions shouldBe List(ProcessActionType.Cancel)
      state.description shouldBe "Scenario is restarting..."
    }
  }

  test("Should return state with error when state is not running and process is deployed") {
    val processName: ProcessName = generateProcessName
    val id = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return state with error when state is null and process is deployed") {
    val processName: ProcessName = generateProcessName
    val id = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return error state when state is running and process is deployed with mismatch versions") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).dbioActionValues
    val version = Some(ProcessVersion(versionId = VersionId(2), processId = ProcessId(1), processName = ProcessName(""), user = "other", modelVersion = None))

    deploymentManager.withProcessStateVersion(processName, SimpleStateStatus.Running, version) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.mismatchDeployedVersion(VersionId(2L), VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should always return process manager failure, even if some other verifications return invalid") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).dbioActionValues
    val version = Some(ProcessVersion(versionId = VersionId(2), processId = ProcessId(1), processName = ProcessName(""), user = "", modelVersion = None))

    // FIXME: doesnt check recover from failed verifications ???
    deploymentManager.withProcessStateVersion(processName, ProblemStateStatus.failed, version) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      state.status shouldBe ProblemStateStatus.failed
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
    }
  }

  test("Should return warning state when state is running with empty version and process is deployed") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withProcessStateVersion(processName, SimpleStateStatus.Running, Option.empty) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.missingDeployedVersion(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return error state when failed to get state") {
    val processName: ProcessName = generateProcessName
    val id =  prepareDeployedProcess(processName).dbioActionValues

    // FIXME: doesnt check recover from failed future of findJobStatus ???
    deploymentManager.withProcessStateVersion(processName, ProblemStateStatus.failedToGet, Option.empty) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue

      val expectedStatus = ProblemStateStatus.failedToGet
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return not deployed status for process with empty state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value.lastStateAction shouldBe None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return not deployed status for process with not found state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value.lastStateAction shouldBe None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return not deployed status for process without actions and with state (it should never happen) ") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value.lastStateAction shouldBe None

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails = fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.isNotDeployed shouldBe true
  }

  test("Should return not deployed state for archived never deployed process") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName, None).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
    state.status shouldBe SimpleStateStatus.NotDeployed
  }

  test("Should return not deployed state for archived never deployed process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName, None).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return canceled status for archived canceled process") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
    state.status shouldBe SimpleStateStatus.Canceled
  }

  test("Should return canceled status for archived canceled process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return not deployed state for unarchived never deployed process") {
    val processName: ProcessName = generateProcessName
    val id = preparedUnArchivedProcess(processName, None).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
    state.status shouldBe SimpleStateStatus.NotDeployed
  }

  test("Should return not deployed status for archived never deployed process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName, None).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return problem status for archived deployed process (last action deployed instead of cancel)") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName, Some(Deploy)).dbioActionValues

    val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
    state.status shouldBe ProblemStateStatus.archivedShouldBeCanceled
  }

  test("Should return canceled status for unarchived process") {
    val processName: ProcessName = generateProcessName
    val id = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return problem status for unarchived process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val id = preparedUnArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("should invalidate in progress processes") {
    val processName: ProcessName = generateProcessName
    val id = prepareProcess(processName).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val initialStatus = SimpleStateStatus.NotDeployed
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe initialStatus
      deploymentManager.withWaitForDeployFinish(processName) {
        deploymentService.deployProcessAsync(ProcessIdWithName(id, processName), None, None).futureValue
        deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe SimpleStateStatus.DuringDeploy

        deploymentService.invalidateInProgressActions()
        deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue.status shouldBe initialStatus
      }
    }
  }

  test("should return problem after occurring timeout during waiting on DM response") {
    val processName: ProcessName = generateProcessName
    val id = prepareDeployedProcess(processName).dbioActionValues
    val processIdName = ProcessIdWithName(id, processName)

    val timeout = 1.second
    val serviceWithTimeout = createDeploymentService(Some(timeout))

    val durationLongerThanTimeout = timeout.plus(patienceConfig.timeout)
    deploymentManager.withDelayBeforeStateReturn(durationLongerThanTimeout) {
      val status = serviceWithTimeout.getProcessState(processIdName).futureValueEnsuringInnerException(durationLongerThanTimeout).status
      status shouldBe ProblemStateStatus.failedToGet
    }
  }

  test("should fail when trying to get state for fragment") {
    val processName: ProcessName = generateProcessName
    val id = prepareFragment(processName).dbioActionValues

    assertThrowsWithParent[FragmentStateException] {
      deploymentService.getProcessState(ProcessIdWithName(id, processName)).futureValue
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

  private def prepareCanceledProcess(processName: ProcessName): DB[ProcessId] =
    for {
      id <- prepareDeployedProcess(processName)
      _ <- prepareAction(id, Cancel)
    } yield id

  private def prepareDeployedProcess(processName: ProcessName): DB[ProcessId] =
    prepareProcessWithAction(processName, Some(Deploy))

  private def preparedUnArchivedProcess(processName: ProcessName, actionType: Option[ProcessActionType]): DB[ProcessId] =
    for {
      id <- prepareArchivedProcess(processName, actionType)
      _ <- writeProcessRepository.archive(processId = id, isArchived = false)
      _ <- actionRepository.markProcessAsUnArchived(processId = id, initialVersionId)
    } yield id

  private def prepareArchivedProcess(processName: ProcessName, actionType: Option[ProcessActionType]): DB[ProcessId] =
    for {
      id <- prepareProcessWithAction(processName, actionType)
      _ <- writeProcessRepository.archive(processId = id, isArchived = true)
      _ <- actionRepository.markProcessAsArchived(processId = id, initialVersionId)
    } yield id

  private def prepareProcessWithAction(processName: ProcessName, actionType: Option[ProcessActionType]): DB[ProcessId] = {
    for {
      id <- prepareProcess(processName)
      _ <- DBIOAction.sequenceOption(actionType.map(prepareAction(id, _)))
    } yield id
  }

  private def prepareAction(processId: ProcessId, actionType: ProcessActionType) = {
    val comment = Some(DeploymentComment.unsafe(actionType.toString.capitalize).toComment(actionType))
    actionRepository.addInstantAction(processId, initialVersionId, actionType, comment, None)
  }

  private def prepareProcess(processName: ProcessName, parallelism: Option[Int] = None): DB[ProcessId] = {
    val baseBuilder = ScenarioBuilder
      .streaming(processName.value)
    val canonicalProcess = parallelism.map(baseBuilder.parallelism).getOrElse(baseBuilder)
      .source("source", existingSourceFactory)
      .emptySink("sink", existingSinkFactory)
    val action = CreateProcessAction(
      processName,
      TestCat,
      canonicalProcess,
      Streaming,
      isSubprocess = false,
      forwardedUserName = None)
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
      isSubprocess = true,
      forwardedUserName = None)

    writeProcessRepository.saveNewProcess(action).map(_.rightValue.value.processId)
  }

  private def generateProcessName = {
    ProcessName("proces_" + UUID.randomUUID())
  }
}
