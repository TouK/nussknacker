package pl.touk.nussknacker.ui.process.deployment

import cats.implicits.toTraverseOps
import cats.instances.list._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.LoneElement._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig.ActiveScenariosLimit
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.{Cancel, Deploy}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentId
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, NuScalaTestAssertions, PatientScalaFutures}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.WithClock
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestProcessingType.{
  Streaming1,
  Streaming2
}
import pl.touk.nussknacker.test.mock.MockDeploymentManager
import pl.touk.nussknacker.test.mock.MockDeploymentManagerSyntaxSugar.Ops
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.limits.GlobalLimitsConfig
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError.ActiveScenariosLimitExceededError
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnActionExecutionFinished, OnActionSuccess}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.FragmentStateException
import pl.touk.nussknacker.ui.process.periodic.flink.FlinkClientStub
import pl.touk.nussknacker.ui.process.repository.{CommentValidationError, DBIOActionRunner}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.util.UUID
import scala.concurrent.duration._

class DeploymentServiceSpec
    extends AnyWordSpec
    with Matchers
    with PatientScalaFutures
    with DBIOActionValues
    with NuScalaTestAssertions
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithHsqlDbTesting
    with WithCategoryUsedMoreThanOnceDesignerConfig
    with WithClock
    with EitherValuesDetailedMessage {

  override protected val dbioRunner: DBIOActionRunner = newDBIOActionRunner(testDbRef)

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  private val writeProcessRepository = newWriteProcessRepository(
    testDbRef,
    clock,
    List(Streaming1.stringify, Streaming2.stringify)
  )

  private val deploymentServiceFactory = new TestDeploymentServiceFactory(testDbRef)

  import deploymentServiceFactory._

  import TestDeploymentServiceFactory._

  private val deploymentManager1: MockDeploymentManager = MockDeploymentManager.create(
    ConfigWithUnresolvedVersion(designerRawConfig.getConfig(s"scenarioTypes.${Streaming1.stringify}"))
  )

  private val deploymentManager2: MockDeploymentManager = MockDeploymentManager.create(
    ConfigWithUnresolvedVersion(designerRawConfig.getConfig(s"scenarioTypes.${Streaming2.stringify}"))
  )

  private val globalLimitsConfig =
    GlobalLimitsConfig.default.copy(activeScenariosLimit = Some(GlobalLimitsConfig.ActiveScenariosLimit(2)))
  private val processingTypeLimits = LimitsConfig.default.copy(activeScenariosLimit = Some(ActiveScenariosLimit(2)))

  val TestDeploymentServiceServices(scenarioStatusProvider, actionService, deploymentService, reconciler) =
    deploymentServiceFactory.create(
      Map(
        Streaming1.stringify -> deploymentManager1,
        Streaming2.stringify -> deploymentManager2
      ),
      globalLimitsConfig = globalLimitsConfig,
      processingTypeLimits = processingTypeLimits,
    )

  // TODO: temporary step - we would like to extract the validation and the comment validation tests to external validators
  private def createDeploymentServiceWithCommentSettings() = {
    val commentSettings = DeploymentCommentSettings.unsafe(".+", Option("sampleComment"))
    deploymentServiceFactory
      .create(
        Map(
          Streaming1.stringify -> deploymentManager1,
          Streaming2.stringify -> deploymentManager2
        ),
        globalLimitsConfig = globalLimitsConfig,
        processingTypeLimits = processingTypeLimits,
        deploymentCommentSettings = Some(commentSettings)
      )
      .deploymentService
  }

  "should return error when trying to deploy without comment when comment is required" in {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings()

    val processName: ProcessName = generateProcessName()
    val processIdWithName        = prepareProcess(processName)

    val result =
      deploymentServiceWithCommentSettings
        .processCommand(
          RunDeploymentCommand(
            CommonCommandData(processIdWithName, None, user),
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            NodesDeploymentData.empty
          )
        )
        .failed
        .futureValue

    result shouldBe a[CommentValidationError]
    result.getMessage.trim shouldBe "Comment is required."

    eventually {
      val inProgressActions =
        actionRepository.getInProgressActionNames(processIdWithName.id).dbioActionValues
      inProgressActions should have size 0
    }
  }

  "should not deploy without comment when comment is required" in {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings()

    val processName: ProcessName = generateProcessName()
    val processIdWithName        = prepareProcess(processName)

    deploymentServiceWithCommentSettings.processCommand(
      RunDeploymentCommand(
        CommonCommandData(processIdWithName, None, user),
        StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
        NodesDeploymentData.empty
      )
    )

    eventually {
      val status = scenarioStatusProvider
        .getScenarioStatus(processIdWithName)
        .futureValue

      status should not be SimpleStateStatus.Running

      status shouldBe SimpleStateStatus.NotDeployed
    }

    eventually {
      val inProgressActions = actionRepository.getInProgressActionNames(processIdWithName.id).dbioActionValues
      inProgressActions should have size 0
    }
  }

  "should pass when having an ok comment" in {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings()

    val processName: ProcessName = generateProcessName()
    val processIdWithName        = prepareProcess(processName)

    deploymentManager1
      .withWaitForDeployFinish(processName) {
        deploymentServiceWithCommentSettings
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(processIdWithName, Comment.from("samplePattern"), user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              NodesDeploymentData.empty
            )
          )
          .futureValue
      }
      .futureValue
  }

  "should not cancel a deployed process without cancel comment when comment is required" in {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings()

    val processName: ProcessName = generateProcessName()
    val (processIdWithName, _)   = prepareDeployedProcess(processName)
    deploymentManager1.withProcessRunning(processName) {
      val error = deploymentServiceWithCommentSettings
        .processCommand(CancelScenarioCommand(CommonCommandData(processIdWithName, None, user)))
        .failed
        .futureValue
      error.getMessage shouldBe "Comment is required."

      eventually {
        val status = scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue

        status should not be SimpleStateStatus.Canceled

        status shouldBe SimpleStateStatus.Running
      }

      eventually {
        val inProgressActions = actionRepository.getInProgressActionNames(processIdWithName.id).dbioActionValues
        inProgressActions should have size 0
      }
    }
  }

  "should return state correctly when state is deployed" in {
    val processName: ProcessName = generateProcessName()
    val processIdWithName        = prepareProcess(processName)

    deploymentManager1.withProcessRunning(processName) {
      deploymentManager1.withWaitForDeployFinish(processName) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(processIdWithName, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              NodesDeploymentData.empty
            )
          )
          .futureValue
        scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue shouldBe SimpleStateStatus.DuringDeploy
      }

      eventually {
        scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue shouldBe SimpleStateStatus.Running
      }
    }
  }

  "should return state correctly when state is cancelled" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)

    deploymentManager1.withWaitForCancelFinish {
      deploymentService.processCommand(CancelScenarioCommand(CommonCommandData(processId, None, user)))
      eventually {
        scenarioStatusProvider.getScenarioStatus(processId).futureValue shouldBe SimpleStateStatus.DuringCancel
      }
    }
  }

  "should mark Action ExecutionFinished and publish an event as finished" in {
    val processName: ProcessName = generateProcessName()
    val (processId, actionId)    = prepareDeployedProcess(processName)

    actionService.markActionExecutionFinished(Streaming1.stringify, actionId).futureValue
    eventually {
      val action =
        actionRepository.getFinishedProcessActions(processId.id, Some(Set(ScenarioActionName.Deploy))).dbioActionValues

      action.loneElement.state shouldBe ProcessActionState.ExecutionFinished
      listener.events.toArray.filter(_.isInstanceOf[OnActionExecutionFinished]) should have length 1
    }
  }

  "should mark finished process as finished" in {
    val processName: ProcessName    = generateProcessName()
    val (processId, deployActionId) = prepareDeployedProcess(processName)

    deploymentManager1.withProcessRunning(processName) {
      checkIsFollowingDeploy(
        scenarioStatusProvider.getScenarioStatus(processId).futureValue,
        expected = true
      )
      fetchingScenarioDBIORepository
        .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
        .dbioActionValues
        .value
        .lastStateAction should not be None
    }

    deploymentManager1.withProcessFinished(processName, DeploymentId.fromActionId(deployActionId)) {
      reconciler.synchronizeEngineFinishedDeploymentsLocalStatuses().futureValue
    }

    val processDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    val lastStateAction = processDetails.lastStateAction.value
    lastStateAction.actionName shouldBe ScenarioActionName.Deploy
    lastStateAction.state shouldBe ProcessActionState.ExecutionFinished
    // we want to hide finished deploys
    processDetails.lastDeployedAction shouldBe empty
    dbioRunner.run(activityRepository.findActivity(processId.id)).futureValue.comments should have length 1

    deploymentManager1.withEmptyProcessState(processName) {
      val stateAfterJobRetention =
        scenarioStatusProvider.getScenarioStatus(processId).futureValue
      stateAfterJobRetention shouldBe SimpleStateStatus.Finished
    }

    archiveProcess(processId)
    scenarioStatusProvider.getScenarioStatus(processId).futureValue shouldBe SimpleStateStatus.Finished
  }

  "should finish deployment only after DeploymentManager finishes" in {
    val processName: ProcessName = generateProcessName()
    val processIdWithName        = prepareProcess(processName)

    def checkStatusAction(expectedStatus: StateStatus, expectedAction: Option[ScenarioActionName]) = {
      fetchingScenarioDBIORepository
        .fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
        .dbioActionValues
        .flatMap(_.lastStateAction)
        .map(_.actionName) shouldBe expectedAction
      scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue shouldBe expectedStatus
    }

    deploymentManager1.withEmptyProcessState(processName) {
      checkStatusAction(SimpleStateStatus.NotDeployed, None)
      deploymentManager1.withWaitForDeployFinish(processName) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(processIdWithName, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              NodesDeploymentData.empty
            )
          )
          .futureValue
        checkStatusAction(SimpleStateStatus.DuringDeploy, None)
        listener.events shouldBe Symbol("empty")
      }
    }

    deploymentManager1.withProcessRunning(processName) {
      eventually {
        checkStatusAction(SimpleStateStatus.Running, Some(ScenarioActionName.Deploy))
        listener.events.toArray.filter(_.isInstanceOf[OnActionSuccess]) should have length 1
      }
    }

    val activities = dbioRunner.run(activityRepository.findActivities(processIdWithName.id)).futureValue

    activities.size shouldBe 2
    activities(0) match {
      case _: ScenarioActivity.ScenarioCreated => ()
      case _                                   => fail("First activity should be ScenarioCreated")
    }
    activities(1) match {
      case _: ScenarioActivity.ScenarioDeployed => ()
      case _                                    => fail("Second activity should be ScenarioDeployed")
    }
  }

  "should skip notifications and deployment on validation errors" in {
    val processName: ProcessName = generateProcessName()
    val requestedParallelism     = FlinkClientStub.maxParallelism + 1
    val processIdWithName =
      prepareProcess(processName, Some(requestedParallelism))

    deploymentManager1.withEmptyProcessState(processName) {
      val result =
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(processIdWithName, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              NodesDeploymentData.empty
            )
          )
          .failed
          .futureValue
      result.getMessage shouldBe s"Not enough free slots on Flink cluster. Available slots: ${FlinkClientStub.maxParallelism}, requested: $requestedParallelism. " +
        s"Decrease scenario's parallelism or extend Flink cluster resources"
      deploymentManager1.successfulDeploys should not contain processName
      fetchingScenarioDBIORepository
        .fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
        .dbioActionValues
        .flatMap(_.lastStateAction) shouldBe None
      listener.events shouldBe Symbol("empty")
      // during short period of time, status will be during deploy - because parallelism validation are done in the same critical section as deployment
      eventually {
        scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue shouldBe SimpleStateStatus.NotDeployed
      }
    }
  }

  "should return properly state when state is canceled and process is canceled" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareCanceledProcess(processName)

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      scenarioStatusProvider.getScenarioStatus(processId).futureValue shouldBe SimpleStateStatus.Canceled
    }
  }

  "should return canceled status for canceled process with empty state - cleaned state" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareCanceledProcess(processName)

    fetchingScenarioDBIORepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager1.withEmptyProcessState(processName) {
      scenarioStatusProvider.getScenarioStatus(processId).futureValue shouldBe SimpleStateStatus.Canceled
    }

    val processDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
  }

  "should return canceled status for canceled process with not founded state - cleaned state" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareCanceledProcess(processName)

    fetchingScenarioDBIORepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager1.withEmptyProcessState(processName) {
      scenarioStatusProvider.getScenarioStatus(processId).futureValue shouldBe SimpleStateStatus.Canceled
    }

    val processDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
  }

  "should return state with warning when state is running and process is canceled" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareCanceledProcess(processName)

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return not deployed when engine returns any state and process hasn't action" in {
    val processName: ProcessName = generateProcessName()
    val processId                = prepareProcess(processName)

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
      state shouldBe SimpleStateStatus.NotDeployed
    }
  }

  "should return DuringCancel state when is during canceled and process has CANCEL action" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareCanceledProcess(processName)

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.DuringCancel) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      state shouldBe SimpleStateStatus.DuringCancel
    }
  }

  "should return state with status Restarting when process has been deployed and is restarting" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)

    val state =
      DeploymentStatusDetails(
        status = SimpleStateStatus.Restarting,
        deploymentId = None,
        version = Some(VersionId.initialVersionId)
      )

    deploymentManager1.withProcessStates(processName, List(state)) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      state shouldBe SimpleStateStatus.Restarting
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Cancel)
    }
  }

  "should return state with error when state is not running and process is deployed" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return state with error when state is null and process is deployed" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)

    deploymentManager1.withEmptyProcessState(processName) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return error state when state is running and process is deployed with mismatch versions" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)
    val version                  = Some(VersionId(2))

    deploymentManager1.withProcessStateVersion(processName, SimpleStateStatus.Running, version) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      val expectedStatus = ProblemStateStatus.mismatchDeployedVersion(VersionId(2L), VersionId(1L), "admin")
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should always return process manager failure, even if some other verifications return invalid" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)
    val version                  = Some(VersionId(2))

    // FIXME: doesnt check recover from failed verifications ???
    deploymentManager1.withProcessStateVersion(processName, ProblemStateStatus.Failed, version) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      state shouldBe ProblemStateStatus.Failed
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return warning state when state is running with empty version and process is deployed" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)

    deploymentManager1.withProcessStateVersion(processName, SimpleStateStatus.Running, Option.empty) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      val expectedStatus = ProblemStateStatus.missingDeployedVersion(VersionId(1L), "admin")
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return error state when failed to get state" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)

    // FIXME: doesnt check recover from failed future of findJobStatus ???
    deploymentManager1.withProcessStateVersion(processName, ProblemStateStatus.FailedToGet, Option.empty) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue

      val expectedStatus = ProblemStateStatus.FailedToGet
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return not deployed status for process with empty state - not deployed state" in {
    val processName: ProcessName = generateProcessName()
    val processId                = prepareProcess(processName)
    fetchingScenarioDBIORepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager1.withEmptyProcessState(processName) {
      scenarioStatusProvider
        .getScenarioStatus(ProcessIdWithName(processId.id, processName))
        .futureValue shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  "should return not deployed status for process with not found state - not deployed state" in {
    val processName: ProcessName = generateProcessName()
    val processId                = prepareProcess(processName)
    fetchingScenarioDBIORepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager1.withEmptyProcessState(processName) {
      scenarioStatusProvider.getScenarioStatus(processId).futureValue shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  "should return not deployed status for process without actions and with state (it should never happen)" in {
    val processName: ProcessName = generateProcessName()
    val processId                = prepareProcess(processName)
    fetchingScenarioDBIORepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      scenarioStatusProvider
        .getScenarioStatus(ProcessIdWithName(processId.id, processName))
        .futureValue shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  "should return not deployed state for archived never deployed process" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareArchivedProcess(processName, None)

    val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
    state shouldBe SimpleStateStatus.NotDeployed
  }

  "should return not deployed state for archived never deployed process with running state (it should never happen)" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareArchivedProcess(processName, None)

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
      state shouldBe SimpleStateStatus.NotDeployed
    }
  }

  "should return canceled status for archived canceled process" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel))

    val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
    state shouldBe SimpleStateStatus.Canceled
  }

  "should return canceled status for archived canceled process with running state (it should never happen)" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel))

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
      state shouldBe SimpleStateStatus.Canceled
    }
  }

  "should return not deployed state for unarchived never deployed process" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = preparedUnArchivedProcess(processName, None)

    val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
    state shouldBe SimpleStateStatus.NotDeployed
  }

  "should return during deploy for process in deploy in progress" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = preparedUnArchivedProcess(processName, None)
    val _ = actionRepository
      .addInProgressAction(processId.id, ScenarioActionName.Deploy, Some(VersionId(1)))
      .dbioActionValues

    val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
    state shouldBe SimpleStateStatus.DuringDeploy
  }

  "should getScenariosStatuses bulk with the same result as for single scenario" in {
    val (_, _, runningScenarioId) = prepareScenariosInVariousStates()

    val processesDetails = fetchingScenarioDBIORepository
      .fetchLatestProcessesDetails[Unit](ScenarioQuery.empty)
      .dbioActionValues

    deploymentManager1.withProcessRunning(runningScenarioId.name) {
      val statesBasedOnCachedInProgressActionTypes = scenarioStatusProvider
        .getScenariosStatuses(processesDetails)
        .futureValue
        .map(_.map(_.name))

      statesBasedOnCachedInProgressActionTypes shouldBe List(
        Some("DURING_DEPLOY"),
        Some("DURING_CANCEL"),
        Some("RUNNING"),
        None
      )

      val statesBasedOnNotCachedInProgressActionTypes =
        processesDetails
          .map(pd =>
            Option(pd)
              .filterNot(_.isFragment)
              .map(scenarioStatusProvider.getAllowedActionsForScenarioStatus(_).map(_.scenarioStatus.name))
              .sequence
          )
          .sequence
          .futureValue

      statesBasedOnCachedInProgressActionTypes shouldEqual statesBasedOnNotCachedInProgressActionTypes
    }
  }

  "should return not deployed status for archived never deployed process with running state (it should never happen)" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareArchivedProcess(processName, None)

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
      state shouldBe SimpleStateStatus.NotDeployed
    }
  }

  "should return problem status for archived deployed process (last action deployed instead of cancel)" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareArchivedProcess(processName, Some(Deploy))

    val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
    state shouldBe ProblemStateStatus.ArchivedShouldBeCanceled
  }

  "should return canceled status for unarchived process" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel))

    deploymentManager1.withEmptyProcessState(processName) {
      val state = scenarioStatusProvider.getScenarioStatus(processId).futureValue
      state shouldBe SimpleStateStatus.Canceled
    }
  }

  "should return problem status for unarchived process with running state (it should never happen)" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = preparedUnArchivedProcess(processName, Some(Cancel))

    deploymentManager1.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state          = scenarioStatusProvider.getScenarioStatus(processId).futureValue
      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should invalidate in progress processes" in {
    val processName: ProcessName = generateProcessName()
    val processIdWithName        = prepareProcess(processName)

    deploymentManager1.withEmptyProcessState(processName) {
      val initialStatus = SimpleStateStatus.NotDeployed
      scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue shouldBe initialStatus
      deploymentManager1.withWaitForDeployFinish(processName) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(processIdWithName, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              NodesDeploymentData.empty
            )
          )
          .futureValue
        scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue shouldBe SimpleStateStatus.DuringDeploy

        actionService.invalidateInProgressActions()
        scenarioStatusProvider.getScenarioStatus(processIdWithName).futureValue shouldBe initialStatus
      }
    }
  }

  "should return problem after occurring timeout during waiting on DM response" in {
    val processName: ProcessName = generateProcessName()
    val (processId, _)           = prepareDeployedProcess(processName)

    val timeout = 1.second
    val serviceWithTimeout = deploymentServiceFactory
      .create(
        Map(
          Streaming1.stringify -> deploymentManager1,
          Streaming2.stringify -> deploymentManager2
        ),
        scenarioStateTimeout = Some(timeout)
      )
      .scenarioStatusProvider

    val durationLongerThanTimeout = timeout.plus(patienceConfig.timeout)
    deploymentManager1.withDelayBeforeStateReturn(durationLongerThanTimeout) {
      val status = serviceWithTimeout
        .getScenarioStatus(processId)
        .futureValueEnsuringInnerException(durationLongerThanTimeout)
      status shouldBe ProblemStateStatus.FailedToGet
    }
  }

  "should fail when trying to get state for fragment" in {
    val processName: ProcessName = generateProcessName()
    val id                       = prepareFragment(processName)

    assertThrowsWithParent[FragmentStateException.type] {
      scenarioStatusProvider.getScenarioStatus(id).futureValue
    }
  }

  // TODO: add tests for more advanced things such as changes in model api
  "should recover jobs" in {
    val processName = generateProcessName()
    prepareDeployedProcess(processName)

    deploymentManager1.withStubbedDeployResult(processName) {
      reconciler.recoverNotRunningDeploymentsThatShouldBeRunning(_ => true).futureValue
    }

    eventually {
      deploymentManager1.successfulDeploys should contain(processName)
    }
  }

  "should allow to deploy scenario when active scenarios count is less than the limit" when {
    "one processing type is considered" when {
      "1st scenario is running, and the 2nd scenario is not deployed" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.NotDeployed) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is cancelled" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Canceled) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is during cancel" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.DuringCancel) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is finished" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Finished) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is problem" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), StateStatus("PROBLEM")) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is being redeployed, when the 2nd scenario is running" in {
        val firstScenario = generateProcessName()
        deploymentManager1.withProcessStateStatus(firstScenario, SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
            deployExampleScenario(firstScenario)
          }
        }
      }
    }
    "two processing types are considered" when {
      "1st scenario is running (streaming1), and the 2nd scenario is not deployed (streaming2)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.NotDeployed) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is cancelled (streaming2)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Canceled) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is during cancel (streaming2)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.DuringCancel) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is finished (streaming2)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Finished) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is problem (streaming2)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), StateStatus("PROBLEM")) {
            deployExampleScenario()
          }
        }
      }
      "1st scenario is being redeployed (streaming1), when the 2nd scenario is running (streaming2)" in {
        val firstScenario = generateProcessName()
        deploymentManager1.withProcessStateStatus(firstScenario, SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
            deployExampleScenario(firstScenario)
          }
        }
      }
    }
  }

  "should not allow more scenarios than active scenario limits to be used" when {
    "one processing type is considered" when {
      "1st scenario is running, and the 2nd scenario is running, and the 3rd scenario is not deployed" in {
        val scenario1 = ProcessName("sc1")
        prepareDeployedProcess(scenario1)
        val scenario2 = ProcessName("sc2")
        prepareDeployedProcess(scenario2)
        val scenario3 = ProcessName("sc3")
        prepareNotDeployedProcess(scenario3)

        deploymentManager1.withProcessStateStatus(scenario1, SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(scenario2, SimpleStateStatus.Running) {
            deploymentManager1.withProcessStateStatus(scenario3, SimpleStateStatus.NotDeployed) {
              assertThrowsWithParent[ActiveScenariosLimitExceededError] {
                deployExampleScenario(ProcessName("sc4"))
              }
            }
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is during deploy, and the 3rd scenario is not deployed" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.DuringDeploy) {
            deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.NotDeployed) {
              assertThrowsWithParent[ActiveScenariosLimitExceededError] {
                deployExampleScenario()
              }
            }
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is restarting, and the 3rd scenario is not deployed" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Restarting) {
            deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.NotDeployed) {
              assertThrowsWithParent[ActiveScenariosLimitExceededError] {
                deployExampleScenario()
              }
            }
          }
        }
      }
    }
    "two processing types are considered" when {
      "1st scenario is running (streaming1), and the 2nd scenario is running (streaming2), and the 3rd scenario is not deployed (streaming1)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
            deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.NotDeployed) {
              assertThrowsWithParent[ActiveScenariosLimitExceededError] {
                deployExampleScenario()
              }
            }
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is during deploy (streaming2), and the 3rd scenario is not deployed (streaming1)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.DuringDeploy) {
            deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.NotDeployed) {
              assertThrowsWithParent[ActiveScenariosLimitExceededError] {
                deployExampleScenario()
              }
            }
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is restarting (streaming2), and the 3rd scenario is not deployed (streaming1)" in {
        deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Running) {
          deploymentManager2.withProcessStateStatus(generateProcessName(), SimpleStateStatus.Restarting) {
            deploymentManager1.withProcessStateStatus(generateProcessName(), SimpleStateStatus.NotDeployed) {
              assertThrowsWithParent[ActiveScenariosLimitExceededError] {
                deployExampleScenario()
              }
            }
          }
        }
      }
    }
  }

  private def deployExampleScenario(scenarioName: ProcessName = generateProcessName()) = {
    val processIdWithName = prepareProcess(scenarioName)
    deploymentManager1.withWaitForDeployFinish(scenarioName) {
      deploymentService
        .processCommand(
          RunDeploymentCommand(
            CommonCommandData(processIdWithName, None, user),
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            NodesDeploymentData.empty
          )
        )
        .futureValue
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    listener.clear()
    deploymentManager1.successfulDeploys.clear()
    deploymentManager2.successfulDeploys.clear()
  }

  private def checkIsFollowingDeploy(status: StateStatus, expected: Boolean) = {
    withClue(status) {
      SimpleStateStatus.DefaultFollowingDeployStatuses.contains(status) shouldBe expected
    }
  }

  private def prepareCanceledProcess(processName: ProcessName): (ProcessIdWithName, ProcessActionId) = {
    val (processId, _) = prepareDeployedProcess(processName)
    val cancelActionId = prepareAction(processId.id, Cancel)
    (processId, cancelActionId)
  }

  private def prepareDeployedProcess(processName: ProcessName): (ProcessIdWithName, ProcessActionId) =
    prepareProcessWithAction(processName, Some(Deploy)) match {
      case (processId, Some(actionId)) => (processId, actionId)
      case (_, None) => throw new IllegalStateException("Deploy actionId should be defined for deployed process")
    }

  private def prepareNotDeployedProcess(processName: ProcessName): ProcessIdWithName =
    prepareProcess(processName)

  private def preparedUnArchivedProcess(
      processName: ProcessName,
      actionNameOpt: Option[ScenarioActionName]
  ): (ProcessIdWithName, Option[ProcessActionId]) = {
    val (processId, actionIdOpt) = prepareArchivedProcess(processName, actionNameOpt)
    writeProcessRepository
      .archive(processId = processId, isArchived = false)
      .dbioActionValues
    actionRepository
      .addInstantAction(
        processId.id,
        VersionId.initialVersionId,
        ScenarioActionName.UnArchive,
        None
      )
      .dbioActionValues
    (processId, actionIdOpt)
  }

  private def prepareArchivedProcess(
      processName: ProcessName,
      actionNameOpt: Option[ScenarioActionName]
  ): (ProcessIdWithName, Option[ProcessActionId]) = {
    val (processId, actionIdOpt) = prepareProcessWithAction(processName, actionNameOpt)
    archiveProcess(processId)
    (processId, actionIdOpt)
  }

  private def archiveProcess(processId: ProcessIdWithName): Unit = {
    writeProcessRepository
      .archive(processId = processId, isArchived = true)
      .flatMap(_ =>
        actionRepository.addInstantAction(processId.id, VersionId.initialVersionId, ScenarioActionName.Archive, None)
      )
      .dbioActionValues
  }

  private def prepareScenariosInVariousStates(): (ProcessIdWithName, ProcessIdWithName, ProcessIdWithName) = {
    val duringDeployProcessName :: duringCancelProcessName :: otherProcess :: fragmentName :: Nil =
      (1 to 4).map(_ => generateProcessName()).toList

    val (duringDeployProcessId, _) = preparedUnArchivedProcess(duringDeployProcessName, None)
    val (duringCancelProcessId, _) = prepareDeployedProcess(duringCancelProcessName)
    actionRepository
      .addInProgressAction(duringDeployProcessId.id, ScenarioActionName.Deploy, Some(VersionId.initialVersionId))
      .dbioActionValues
    actionRepository
      .addInProgressAction(duringCancelProcessId.id, ScenarioActionName.Cancel, Some(VersionId.initialVersionId))
      .dbioActionValues
    val (deployedProcessId, _) = prepareDeployedProcess(otherProcess)
    prepareFragment(fragmentName)

    (duringDeployProcessId, duringCancelProcessId, deployedProcessId)
  }

  private def prepareProcessWithAction(
      processName: ProcessName,
      actionNameOpt: Option[ScenarioActionName]
  ): (ProcessIdWithName, Option[ProcessActionId]) = {
    val processId   = prepareProcess(processName)
    val actionIdOpt = actionNameOpt.map(prepareAction(processId.id, _))
    (processId, actionIdOpt)
  }

  private def prepareAction(processId: ProcessId, actionName: ScenarioActionName): ProcessActionId = {
    val comment = Comment.from(actionName.toString.capitalize)
    actionRepository
      .addInstantAction(processId, VersionId.initialVersionId, actionName, comment)
      .map(_.id)
      .dbioActionValues
  }

  private def prepareProcess(processName: ProcessName, parallelism: Option[Int] = None): ProcessIdWithName = {
    val baseBuilder = ScenarioBuilder
      .streaming(processName.value)
    val canonicalProcess = parallelism
      .map(baseBuilder.parallelism)
      .getOrElse(baseBuilder)
      .source("source", ProcessTestData.existingSourceFactory)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
    val action = CreateProcessAction(
      processName = processName,
      category = "Category1",
      canonicalProcess = canonicalProcess,
      processingType = Streaming1.stringify,
      isFragment = false,
    )
    writeProcessRepository
      .saveNewProcess(action)
      .map(_.value.processId)
      .map(ProcessIdWithName(_, processName))
      .dbioActionValues
  }

  private def prepareFragment(processName: ProcessName): ProcessIdWithName = {
    val canonicalProcess = ScenarioBuilder
      .fragment(processName.value)
      .emptySink("end", "end")

    val action = CreateProcessAction(
      processName = processName,
      category = "Category1",
      canonicalProcess = canonicalProcess,
      processingType = Streaming1.stringify,
      isFragment = true,
    )

    writeProcessRepository
      .saveNewProcess(action)
      .map(_.value.processId)
      .map(ProcessIdWithName(_, processName))
      .dbioActionValues
  }

  private def generateProcessName(): ProcessName = {
    ProcessName("proces_" + UUID.randomUUID())
  }

  private def getAllowedActions(status: StateStatus) = {
    deploymentManager1.processStateDefinitionManager.statusActions(
      ScenarioStatusWithScenarioContext(
        scenarioStatus = status,
        deployedVersionId = None,
        currentlyPresentedVersionId = None
      )
    )
  }

}
