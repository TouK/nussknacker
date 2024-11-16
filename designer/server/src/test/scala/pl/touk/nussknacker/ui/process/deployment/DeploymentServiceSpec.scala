package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import cats.instances.list._
import db.util.DBIOActionInstances.DB
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.{Cancel, Deploy}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{Comment, ProcessVersion}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.{CustomActionResult, DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.WithClock
import pl.touk.nussknacker.test.mock.{MockDeploymentManager, TestProcessChangeListener}
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, NuScalaTestAssertions, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnActionExecutionFinished, OnActionSuccess}
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider.noCombinedDataFun
import pl.touk.nussknacker.ui.process.processingtype.provider.{ProcessingTypeDataProvider, ProcessingTypeDataState}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository.{CommentValidationError, DBIOActionRunner}
import pl.touk.nussknacker.ui.process.{ScenarioQuery, ScenarioWithDetailsConversions}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
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
    with WithClock
    with EitherValuesDetailedMessage {

  import VersionId._

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private implicit val system: ActorSystem          = ActorSystem()
  private implicit val user: LoggedUser             = TestFactory.adminUser("user")
  private implicit val ds: ExecutionContextExecutor = system.dispatcher

  private var deploymentManager: MockDeploymentManager = _
  override protected val dbioRunner: DBIOActionRunner  = newDBIOActionRunner(testDbRef)
  private val fetchingProcessRepository                = newFetchingProcessRepository(testDbRef)
  private val futureFetchingProcessRepository          = newFutureFetchingScenarioRepository(testDbRef)
  private val writeProcessRepository                   = newWriteProcessRepository(testDbRef, clock)
  private val actionRepository                         = newActionProcessRepository(testDbRef)
  private val activityRepository                       = newScenarioActivityRepository(testDbRef, clock)

  private val processingTypeDataProvider: ProcessingTypeDataProvider[DeploymentManager, Nothing] =
    new ProcessingTypeDataProvider[DeploymentManager, Nothing] {

      override val state: ProcessingTypeDataState[DeploymentManager, Nothing] =
        new ProcessingTypeDataState[DeploymentManager, Nothing] {

          override def all: Map[ProcessingType, ValueWithRestriction[DeploymentManager]] = Map(
            "streaming" -> ValueWithRestriction.anyUser(deploymentManager)
          )

          override def getCombined: () => Nothing = noCombinedDataFun
          override def stateIdentity: Any         = deploymentManager
        }

    }

  private val dmDispatcher =
    new DeploymentManagerDispatcher(processingTypeDataProvider, futureFetchingProcessRepository)

  private val listener = new TestProcessChangeListener

  private val deploymentCommentSettings = None

  private val deploymentService = createDeploymentService(None)

  deploymentManager = new MockDeploymentManager(
    SimpleStateStatus.Running,
    DefaultProcessingTypeDeployedScenariosProvider(testDbRef, "streaming"),
    new DefaultProcessingTypeActionService("streaming", deploymentService),
    new RepositoryBasedScenarioActivityManager(activityRepository, dbioRunner),
  )

  private def createDeploymentService(
      scenarioStateTimeout: Option[FiniteDuration] = None,
      deploymentCommentSettings: Option[DeploymentCommentSettings] = deploymentCommentSettings,
  ) = {
    new DeploymentService(
      dmDispatcher,
      fetchingProcessRepository,
      actionRepository,
      dbioRunner,
      processValidatorByProcessingType,
      TestFactory.scenarioResolverByProcessingType,
      listener,
      scenarioStateTimeout,
      deploymentCommentSettings
    )
  }

  // TODO: temporary step - we would like to extract the validation and the comment validation tests to external validators
  private def createDeploymentServiceWithCommentSettings = {
    val commentSettings = DeploymentCommentSettings.unsafe(".+", Option("sampleComment"))
    val deploymentServiceWithCommentSettings =
      createDeploymentService(deploymentCommentSettings = Some(commentSettings))
    deploymentServiceWithCommentSettings
  }

  test("should return error when trying to deploy without comment when comment is required") {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings

    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues

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
      val inProgressActions = actionRepository.getInProgressActionNames(processIdWithName.id).dbioActionValues
      inProgressActions should have size 0
    }
  }

  test("should not deploy without comment when comment is required") {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings

    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues

    deploymentServiceWithCommentSettings.processCommand(
      RunDeploymentCommand(
        CommonCommandData(processIdWithName, None, user),
        StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
        NodesDeploymentData.empty
      )
    )

    eventually {
      val status = deploymentServiceWithCommentSettings
        .getProcessState(processIdWithName)
        .futureValue
        .status

      status should not be SimpleStateStatus.Running

      status shouldBe SimpleStateStatus.NotDeployed
    }

    eventually {
      val inProgressActions = actionRepository.getInProgressActionNames(processIdWithName.id).dbioActionValues
      inProgressActions should have size 0
    }
  }

  test("should pass when having an ok comment") {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings

    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues

    deploymentServiceWithCommentSettings.processCommand(
      RunDeploymentCommand(
        CommonCommandData(processIdWithName, Comment.from("samplePattern"), user),
        StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
        NodesDeploymentData.empty
      )
    )

    eventually {
      deploymentServiceWithCommentSettings
        .getProcessState(processIdWithName)
        .futureValue
        .status shouldBe SimpleStateStatus.Running
    }
  }

  test("should not cancel a deployed process without cancel comment when comment is required") {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings

    val processName: ProcessName = generateProcessName
    val (processIdWithName, _)   = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withWaitForCancelFinish {
      deploymentServiceWithCommentSettings
        .processCommand(CancelScenarioCommand(CommonCommandData(processIdWithName, None, user)))
        .failed
        .futureValue

      eventually {
        val status = deploymentServiceWithCommentSettings
          .getProcessState(processIdWithName)
          .futureValue
          .status

        status should not be SimpleStateStatus.Canceled

        status shouldBe SimpleStateStatus.Running
      }

      eventually {
        val inProgressActions = actionRepository.getInProgressActionNames(processIdWithName.id).dbioActionValues
        inProgressActions should have size 0
      }
    }
  }

  test("should return state correctly when state is deployed") {
    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues

    deploymentManager.withWaitForDeployFinish(processName) {
      deploymentService
        .processCommand(
          RunDeploymentCommand(
            CommonCommandData(processIdWithName, None, user),
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            NodesDeploymentData.empty
          )
        )
        .futureValue
      deploymentService
        .getProcessState(processIdWithName)
        .futureValue
        .status shouldBe SimpleStateStatus.DuringDeploy
    }

    eventually {
      deploymentService
        .getProcessState(processIdWithName)
        .futureValue
        .status shouldBe SimpleStateStatus.Running
    }
  }

  test("should return state correctly when state is cancelled") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withWaitForCancelFinish {
      deploymentService.processCommand(CancelScenarioCommand(CommonCommandData(processId, None, user)))
      eventually {
        deploymentService
          .getProcessState(processId)
          .futureValue
          .status shouldBe SimpleStateStatus.DuringCancel
      }
    }
  }

  test("should mark Action ExecutionFinished and publish an event as finished") {

    val processName: ProcessName = generateProcessName
    val (processId, actionId)    = prepareDeployedProcess(processName).dbioActionValues

    deploymentService.markActionExecutionFinished("streaming", actionId).futureValue
    eventually {
      val action =
        actionRepository.getFinishedProcessActions(processId.id, Some(Set(ScenarioActionName.Deploy))).dbioActionValues

      action.loneElement.state shouldBe ProcessActionState.ExecutionFinished
      listener.events.toArray.filter(_.isInstanceOf[OnActionExecutionFinished]) should have length 1
    }

  }

  test("Should mark finished process as finished") {
    val processName: ProcessName    = generateProcessName
    val (processId, deployActionId) = prepareDeployedProcess(processName).dbioActionValues

    checkIsFollowingDeploy(
      deploymentService.getProcessState(processId).futureValue,
      expected = true
    )
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager.withProcessFinished(processName, DeploymentId.fromActionId(deployActionId)) {
      // we simulate what happens when retrieveStatus is called multiple times to check only one comment is added
      (1 to 5).foreach { _ =>
        checkIsFollowingDeploy(
          deploymentService.getProcessState(processId).futureValue,
          expected = false
        )
      }
      val finishedStatus = deploymentService.getProcessState(processId).futureValue
      finishedStatus.status shouldBe SimpleStateStatus.Finished
      finishedStatus.allowedActions shouldBe List(
        ScenarioActionName.Deploy,
        ScenarioActionName.Archive,
        ScenarioActionName.Rename
      )
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    val lastStateAction = processDetails.lastStateAction.value
    lastStateAction.actionName shouldBe ScenarioActionName.Deploy
    lastStateAction.state shouldBe ProcessActionState.ExecutionFinished
    // we want to hide finished deploys
    processDetails.lastDeployedAction shouldBe empty
    dbioRunner.run(activityRepository.findActivity(processId.id)).futureValue.comments should have length 1

    deploymentManager.withEmptyProcessState(processName) {
      val stateAfterJobRetention =
        deploymentService.getProcessState(processId).futureValue
      stateAfterJobRetention.status shouldBe SimpleStateStatus.Finished
    }

    archiveProcess(processId).dbioActionValues
    deploymentService
      .getProcessState(processId)
      .futureValue
      .status shouldBe SimpleStateStatus.Finished
  }

  test("Should finish deployment only after DeploymentManager finishes") {
    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues

    def checkStatusAction(expectedStatus: StateStatus, expectedAction: Option[ScenarioActionName]) = {
      fetchingProcessRepository
        .fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
        .dbioActionValues
        .flatMap(_.lastStateAction)
        .map(_.actionName) shouldBe expectedAction
      deploymentService.getProcessState(processIdWithName).futureValue.status shouldBe expectedStatus
    }

    deploymentManager.withEmptyProcessState(processName) {

      checkStatusAction(SimpleStateStatus.NotDeployed, None)
      deploymentManager.withWaitForDeployFinish(processName) {
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
    eventually {
      checkStatusAction(SimpleStateStatus.Running, Some(ScenarioActionName.Deploy))
      listener.events.toArray.filter(_.isInstanceOf[OnActionSuccess]) should have length 1
    }

    val activities = dbioRunner.run(activityRepository.findActivities(processIdWithName.id)).futureValue

    activities.size shouldBe 3
    activities(0) match {
      case _: ScenarioActivity.ScenarioCreated => ()
      case _                                   => fail("First activity should be ScenarioCreated")
    }
    activities(1) match {
      case _: ScenarioActivity.ScenarioDeployed => ()
      case _                                    => fail("Second activity should be ScenarioDeployed")
    }
    activities(2) match {
      case ScenarioActivity.CustomAction(
            _,
            _,
            _,
            _,
            _,
            actionName,
            ScenarioComment.WithContent(comment, _, _),
            result,
          ) =>
        actionName shouldBe "Custom action of MockDeploymentManager just before deployment"
        comment.content shouldBe "With comment from DeploymentManager"
        result shouldBe DeploymentResult.Success(result.dateFinished)
      case _ => fail("Third activity should be CustomAction with comment")
    }
  }

  test("Should skip notifications and deployment on validation errors") {
    val processName: ProcessName = generateProcessName
    val processIdWithName =
      prepareProcess(processName, Some(MockDeploymentManager.maxParallelism + 1)).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
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
      result.getMessage shouldBe "Parallelism too large"
      deploymentManager.deploys should not contain processName
      fetchingProcessRepository
        .fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
        .dbioActionValues
        .flatMap(_.lastStateAction) shouldBe None
      listener.events shouldBe Symbol("empty")
      // during short period of time, status will be during deploy - because parallelism validation are done in the same critical section as deployment
      eventually {
        deploymentService.getProcessState(processIdWithName).futureValue.status shouldBe SimpleStateStatus.NotDeployed
      }
    }
  }

  test("Should return properly state when state is canceled and process is canceled") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      deploymentService
        .getProcessState(processId)
        .futureValue
        .status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return canceled status for canceled process with empty state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(processId)
        .futureValue
        .status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
    processDetails.history.value.head.actions.map(_.actionName) should be(
      List(ScenarioActionName.Cancel, ScenarioActionName.Deploy)
    )
  }

  test("Should return canceled status for canceled process with not founded state - cleaned state") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(processId)
        .futureValue
        .status shouldBe SimpleStateStatus.Canceled
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
    processDetails.history.value.head.actions.map(_.actionName) should be(
      List(ScenarioActionName.Cancel, ScenarioActionName.Deploy)
    )
  }

  test("Should return state with warning when state is running and process is canceled") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(processId).futureValue

      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return not deployed when engine returns any state and process hasn't action") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(processId).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return DuringCancel state when is during canceled and process has CANCEL action") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareCanceledProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.DuringCancel) {
      val state = deploymentService.getProcessState(processId).futureValue

      state.status shouldBe SimpleStateStatus.DuringCancel
    }
  }

  test("Should return state with status Restarting when process has been deployed and is restarting") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    val state =
      StatusDetails(SimpleStateStatus.Restarting, None, Some(ExternalDeploymentId("12")), Some(ProcessVersion.empty))

    deploymentManager.withProcessStates(processName, List(state)) {
      val state = deploymentService.getProcessState(processId).futureValue

      state.status shouldBe SimpleStateStatus.Restarting
      state.allowedActions shouldBe List(ScenarioActionName.Cancel)
      state.description shouldBe "Scenario is restarting..."
    }
  }

  test("Should return state with error when state is not running and process is deployed") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      val state = deploymentService.getProcessState(processId).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return state with error when state is null and process is deployed") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val state = deploymentService.getProcessState(processId).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
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
        labels = List.empty,
        user = "other",
        modelVersion = None
      )
    )

    deploymentManager.withProcessStateVersion(processName, SimpleStateStatus.Running, version) {
      val state = deploymentService.getProcessState(processId).futureValue

      val expectedStatus = ProblemStateStatus.mismatchDeployedVersion(VersionId(2L), VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
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
        labels = List.empty,
        user = "",
        modelVersion = None
      )
    )

    // FIXME: doesnt check recover from failed verifications ???
    deploymentManager.withProcessStateVersion(processName, ProblemStateStatus.Failed, version) {
      val state = deploymentService.getProcessState(processId).futureValue

      state.status shouldBe ProblemStateStatus.Failed
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  test("Should return warning state when state is running with empty version and process is deployed") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    deploymentManager.withProcessStateVersion(processName, SimpleStateStatus.Running, Option.empty) {
      val state = deploymentService.getProcessState(processId).futureValue

      val expectedStatus = ProblemStateStatus.missingDeployedVersion(VersionId(1L), "admin")
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return error state when failed to get state") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    // FIXME: doesnt check recover from failed future of findJobStatus ???
    deploymentManager.withProcessStateVersion(processName, ProblemStateStatus.FailedToGet, Option.empty) {
      val state = deploymentService.getProcessState(processId).futureValue

      val expectedStatus = ProblemStateStatus.FailedToGet
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("Should return not deployed status for process with empty state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId.id, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  test("Should return not deployed status for process with not found state - not deployed state") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager.withEmptyProcessState(processName) {
      deploymentService
        .getProcessState(processId)
        .futureValue
        .status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  test("Should return not deployed status for process without actions and with state (it should never happen) ") {
    val processName: ProcessName = generateProcessName
    val processId                = prepareProcess(processName).dbioActionValues
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processId.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      deploymentService
        .getProcessState(ProcessIdWithName(processId.id, processName))
        .futureValue
        .status shouldBe SimpleStateStatus.NotDeployed
    }

    val processDetails =
      fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).dbioActionValues.value
    processDetails.lastStateAction shouldBe None
    processDetails.lastAction shouldBe None
  }

  test("Should return not deployed state for archived never deployed process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, None).dbioActionValues

    val state = deploymentService.getProcessState(processId).futureValue
    state.status shouldBe SimpleStateStatus.NotDeployed
  }

  test(
    "Should return not deployed state for archived never deployed process with running state (it should never happen)"
  ) {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, None).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(processId).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return canceled status for archived canceled process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    val state = deploymentService.getProcessState(processId).futureValue
    state.status shouldBe SimpleStateStatus.Canceled
  }

  test("Should return canceled status for archived canceled process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(processId).futureValue
      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return not deployed state for unarchived never deployed process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = preparedUnArchivedProcess(processName, None).dbioActionValues

    val state = deploymentService.getProcessState(processId).futureValue
    state.status shouldBe SimpleStateStatus.NotDeployed
  }

  test("Should return during deploy for process in deploy in progress") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = preparedUnArchivedProcess(processName, None).dbioActionValues
    val _ = actionRepository
      .addInProgressAction(processId.id, ScenarioActionName.Deploy, Some(VersionId(1)), None)
      .dbioActionValues

    val state = deploymentService.getProcessState(processId).futureValue
    state.status shouldBe SimpleStateStatus.DuringDeploy
  }

  test("Should enrich BaseProcessDetails") {
    prepareProcessesInProgress

    val processesDetails = fetchingProcessRepository
      .fetchLatestProcessesDetails[Unit](ScenarioQuery.empty)
      .dbioActionValues

    val processesDetailsWithState = deploymentService
      .enrichDetailsWithProcessState(
        processesDetails
          .map(
            ScenarioWithDetailsConversions
              .fromEntityIgnoringGraphAndValidationResult(_, ProcessTestData.sampleScenarioParameters)
          )
      )
      .futureValue

    val statesBasedOnCachedInProgressActionTypes = processesDetailsWithState.map(_.state)

    statesBasedOnCachedInProgressActionTypes.map(_.map(_.status.name)) shouldBe List(
      Some("DURING_DEPLOY"),
      Some("DURING_CANCEL"),
      Some("RUNNING"),
      None
    )

    val statesBasedOnNotCachedInProgressActionTypes =
      processesDetails
        .map(pd => Option(pd).filterNot(_.isFragment).map(deploymentService.getProcessState).sequence)
        .sequence
        .futureValue

    statesBasedOnCachedInProgressActionTypes shouldEqual statesBasedOnNotCachedInProgressActionTypes
  }

  test(
    "Should return not deployed status for archived never deployed process with running state (it should never happen)"
  ) {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, None).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state = deploymentService.getProcessState(processId).futureValue
      state.status shouldBe SimpleStateStatus.NotDeployed
    }
  }

  test("Should return problem status for archived deployed process (last action deployed instead of cancel)") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Deploy)).dbioActionValues

    val state = deploymentService.getProcessState(processId).futureValue
    state.status shouldBe ProblemStateStatus.ArchivedShouldBeCanceled
  }

  test("Should return canceled status for unarchived process") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val state = deploymentService.getProcessState(processId).futureValue
      state.status shouldBe SimpleStateStatus.Canceled
    }
  }

  test("Should return problem status for unarchived process with running state (it should never happen)") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = preparedUnArchivedProcess(processName, Some(Cancel)).dbioActionValues

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Running) {
      val state          = deploymentService.getProcessState(processId).futureValue
      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state.status shouldBe expectedStatus
      state.icon shouldBe ProblemStateStatus.icon
      state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
      state.description shouldBe expectedStatus.description
    }
  }

  test("should invalidate in progress processes") {
    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues

    deploymentManager.withEmptyProcessState(processName) {
      val initialStatus = SimpleStateStatus.NotDeployed
      deploymentService.getProcessState(processIdWithName).futureValue.status shouldBe initialStatus
      deploymentManager.withWaitForDeployFinish(processName) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(processIdWithName, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              NodesDeploymentData.empty
            )
          )
          .futureValue
        deploymentService
          .getProcessState(processIdWithName)
          .futureValue
          .status shouldBe SimpleStateStatus.DuringDeploy

        deploymentService.invalidateInProgressActions()
        deploymentService.getProcessState(processIdWithName).futureValue.status shouldBe initialStatus
      }
    }
  }

  test("should return problem after occurring timeout during waiting on DM response") {
    val processName: ProcessName = generateProcessName
    val (processId, _)           = prepareDeployedProcess(processName).dbioActionValues

    val timeout            = 1.second
    val serviceWithTimeout = createDeploymentService(Some(timeout))

    val durationLongerThanTimeout = timeout.plus(patienceConfig.timeout)
    deploymentManager.withDelayBeforeStateReturn(durationLongerThanTimeout) {
      val status = serviceWithTimeout
        .getProcessState(processId)
        .futureValueEnsuringInnerException(durationLongerThanTimeout)
        .status
      status shouldBe ProblemStateStatus.FailedToGet
    }
  }

  test("should fail when trying to get state for fragment") {
    val processName: ProcessName = generateProcessName
    val id                       = prepareFragment(processName).dbioActionValues

    assertThrowsWithParent[FragmentStateException] {
      deploymentService.getProcessState(id).futureValue
    }
  }

  test("should fail invoking custom action when action validation fails") {

    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues
    val actionName               = ScenarioActionName("has-params")
    val wrongParams              = Map("testParam" -> "")

    val result =
      deploymentService
        .processCommand(CustomActionCommand(CommonCommandData(processIdWithName, None, user), actionName, wrongParams))
        .failed
        .futureValue

    result shouldBe a[CustomActionValidationError]
    result.getMessage.trim shouldBe s"Validation failed for: ${actionName}"
  }

  test("should register custom action and comment in repository") {
    val processName: ProcessName = generateProcessName
    val processIdWithName        = prepareProcess(processName).dbioActionValues
    val actionName               = ScenarioActionName("hello")
    val nonEmptyCommentContent   = "not empty comment"
    val comments = List(
      Comment.from(nonEmptyCommentContent),
      Comment.from(""),
      Comment.from(" "),
    )

    val results = comments.map { comment =>
      deploymentService
        .processCommand(
          CustomActionCommand(CommonCommandData(processIdWithName, comment, user), actionName, Map.empty)
        )
        .futureValue
    }

    eventually {
      all(results) shouldBe CustomActionResult("Hi")
      val actions =
        actionRepository
          .getFinishedProcessActions(processIdWithName.id, Some(Set(actionName)))
          .dbioActionValues
          .sortBy(_.createdAt)

      actions.size shouldBe 3
      all(actions.map(_.state)) shouldBe ProcessActionState.Finished
      actions.map(_.comment) shouldBe List(
        Some(nonEmptyCommentContent),
        None,
        None,
      )
      listener.events.toArray.filter(_.isInstanceOf[OnActionSuccess]) should have length 3
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

  private def prepareCanceledProcess(processName: ProcessName): DB[(ProcessIdWithName, ProcessActionId)] =
    for {
      (processId, _) <- prepareDeployedProcess(processName)
      cancelActionId <- prepareAction(processId.id, Cancel)
    } yield (processId, cancelActionId)

  private def prepareDeployedProcess(processName: ProcessName): DB[(ProcessIdWithName, ProcessActionId)] =
    prepareProcessWithAction(processName, Some(Deploy)).map {
      case (processId, Some(actionId)) => (processId, actionId)
      case (_, None) => throw new IllegalStateException("Deploy actionId should be defined for deployed process")
    }

  private def preparedUnArchivedProcess(
      processName: ProcessName,
      actionNameOpt: Option[ScenarioActionName]
  ): DB[(ProcessIdWithName, Option[ProcessActionId])] =
    for {
      (processId, actionIdOpt) <- prepareArchivedProcess(processName, actionNameOpt)
      _                        <- writeProcessRepository.archive(processId = processId, isArchived = false)
      _ <- actionRepository.addInstantAction(processId.id, initialVersionId, ScenarioActionName.UnArchive, None, None)
    } yield (processId, actionIdOpt)

  private def prepareArchivedProcess(
      processName: ProcessName,
      actionNameOpt: Option[ScenarioActionName]
  ): DB[(ProcessIdWithName, Option[ProcessActionId])] = {
    for {
      (processId, actionIdOpt) <- prepareProcessWithAction(processName, actionNameOpt)
      _                        <- archiveProcess(processId)
    } yield (processId, actionIdOpt)
  }

  private def archiveProcess(processId: ProcessIdWithName): DB[_] = {
    writeProcessRepository
      .archive(processId = processId, isArchived = true)
      .flatMap(_ =>
        actionRepository.addInstantAction(processId.id, initialVersionId, ScenarioActionName.Archive, None, None)
      )
  }

  private def prepareProcessesInProgress = {
    val duringDeployProcessName :: duringCancelProcessName :: otherProcess :: fragmentName :: Nil =
      (1 to 4).map(_ => generateProcessName).toList

    val processIdsInProgress = for {
      (duringDeployProcessId, _) <- preparedUnArchivedProcess(duringDeployProcessName, None)
      (duringCancelProcessId, _) <- prepareDeployedProcess(duringCancelProcessName)
      _ <- actionRepository
        .addInProgressAction(
          duringDeployProcessId.id,
          ScenarioActionName.Deploy,
          Some(VersionId.initialVersionId),
          None
        )
      _ <- actionRepository
        .addInProgressAction(
          duringCancelProcessId.id,
          ScenarioActionName.Cancel,
          Some(VersionId.initialVersionId),
          None
        )
      _ <- prepareDeployedProcess(otherProcess)
      _ <- prepareFragment(fragmentName)
    } yield (duringDeployProcessId, duringCancelProcessId)

    val (duringDeployProcessId, duringCancelProcessId) = processIdsInProgress.dbioActionValues
    (duringDeployProcessId, duringCancelProcessId)
  }

  private def prepareProcessWithAction(
      processName: ProcessName,
      actionNameOpt: Option[ScenarioActionName]
  ): DB[(ProcessIdWithName, Option[ProcessActionId])] = {
    for {
      processId   <- prepareProcess(processName)
      actionIdOpt <- DBIOAction.sequenceOption(actionNameOpt.map(prepareAction(processId.id, _)))
    } yield (processId, actionIdOpt)
  }

  private def prepareAction(processId: ProcessId, actionName: ScenarioActionName) = {
    val comment = Comment.from(actionName.toString.capitalize)
    actionRepository.addInstantAction(processId, initialVersionId, actionName, comment, None).map(_.id)
  }

  private def prepareProcess(processName: ProcessName, parallelism: Option[Int] = None): DB[ProcessIdWithName] = {
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
      processingType = "streaming",
      isFragment = false,
      forwardedUserName = None
    )
    writeProcessRepository.saveNewProcess(action).map(_.value.processId).map(ProcessIdWithName(_, processName))
  }

  private def prepareFragment(processName: ProcessName): DB[ProcessIdWithName] = {
    val canonicalProcess = ScenarioBuilder
      .fragment(processName.value)
      .emptySink("end", "end")

    val action = CreateProcessAction(
      processName = processName,
      category = "Category1",
      canonicalProcess = canonicalProcess,
      processingType = "streaming",
      isFragment = true,
      forwardedUserName = None
    )

    writeProcessRepository.saveNewProcess(action).map(_.value.processId).map(ProcessIdWithName(_, processName))
  }

  private def generateProcessName = {
    ProcessName("proces_" + UUID.randomUUID())
  }

}
