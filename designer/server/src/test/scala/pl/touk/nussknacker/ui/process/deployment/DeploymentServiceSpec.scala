package pl.touk.nussknacker.ui.process.deployment

import cats.implicits.toTraverseOps
import cats.instances.list._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.LoneElement._
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig.MaxActiveScenariosCount
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.{Cancel, Deploy}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{
  DeploymentId,
  ExternalDeploymentId,
  FromGraph,
  LatestVersion,
  ScenarioGraphSource
}
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.{
  EitherValuesDetailedMessage,
  ExtremelyPatientScalaFutures,
  NuScalaTestAssertions,
  PatientScalaFutures
}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.WithClock
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestProcessingType
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestProcessingType.{
  Streaming1,
  Streaming2
}
import pl.touk.nussknacker.test.mock.MockDeploymentManager
import pl.touk.nussknacker.test.mock.MockDeploymentManagerSyntaxSugar.Ops
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.limits.GlobalLimitsConfig
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError.MaxActiveScenariosCountExceededError
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnActionExecutionFinished, OnActionSuccess}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.FragmentStateException
import pl.touk.nussknacker.ui.process.periodic.flink.FlinkClientStub
import pl.touk.nussknacker.ui.process.repository.{CommentValidationError, DBIOActionRunner}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, UpdateProcessAction}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
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
    GlobalLimitsConfig.default.copy(maxActiveScenariosCount = Some(GlobalLimitsConfig.MaxActiveScenariosCount(2)))
  private val processingTypeLimits =
    LimitsConfig.default.copy(maxActiveScenariosCount = Some(MaxActiveScenariosCount(2)))

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
    val scenario                             = prepareScenario(generateScenarioName())

    val result =
      deploymentServiceWithCommentSettings
        .processCommand(
          RunDeploymentCommand(
            CommonCommandData(scenario, None, user),
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            Some(NodesDeploymentData.empty),
            scenarioSource = LatestVersion,
          )
        )
        .failed
        .futureValue

    result shouldBe a[CommentValidationError]
    result.getMessage.trim shouldBe "Comment is required."

    eventually {
      val inProgressActions =
        actionRepository.getInProgressActionNames(scenario.id).dbioActionValues
      inProgressActions should have size 0
    }
  }

  "should not deploy without comment when comment is required" in {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings()
    val scenario                             = prepareScenario(generateScenarioName())

    deploymentServiceWithCommentSettings.processCommand(
      RunDeploymentCommand(
        CommonCommandData(scenario, None, user),
        StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
        Some(NodesDeploymentData.empty),
        scenarioSource = LatestVersion,
      )
    )

    eventually {
      val status = scenarioStatusProvider
        .getScenarioStatus(scenario)
        .futureValue

      status shouldBe SimpleStateStatus.NotDeployed
    }

    eventually {
      val inProgressActions = actionRepository.getInProgressActionNames(scenario.id).dbioActionValues
      inProgressActions should have size 0
    }
  }

  "should pass when having an ok comment" in {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings()

    val scenario = prepareScenario(generateScenarioName())

    deploymentManager1
      .withWaitForDeployFinish(scenario.name) {
        deploymentServiceWithCommentSettings
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(scenario, Comment.from("samplePattern"), user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              Some(NodesDeploymentData.empty),
              scenarioSource = LatestVersion,
            )
          )
      }
      .futureValue
  }

  "should not cancel a deployed scenario without cancel comment when comment is required" in {
    val deploymentServiceWithCommentSettings = createDeploymentServiceWithCommentSettings()

    val (scenario, _) = prepareDeployedScenario(generateScenarioName())
    val versionId     = VersionId(1)
    val startedAt     = Instant.now()
    deploymentManager1.withScenarioRunning(scenario.name, versionId, startedAt = startedAt) {
      val error = deploymentServiceWithCommentSettings
        .processCommand(CancelScenarioCommand(CommonCommandData(scenario, None, user)))
        .failed
        .futureValue
      error.getMessage shouldBe "Comment is required."

      eventually {
        val status = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

        status should not be SimpleStateStatus.Canceled

        status shouldBe SimpleStateStatus.Running(versionId, startedAt)
      }

      eventually {
        val inProgressActions = actionRepository.getInProgressActionNames(scenario.id).dbioActionValues
        inProgressActions should have size 0
      }
    }
  }

  "should return state correctly when state is deployed" in {
    val scenario  = prepareScenario(generateScenarioName())
    val versionId = VersionId(1)
    val startedAt = Instant.now()

    deploymentManager1.withScenarioRunning(scenario.name, versionId, startedAt) {
      deploymentManager1.withWaitForDeployFinish(scenario.name) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(scenario, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              Some(NodesDeploymentData.empty),
              scenarioSource = LatestVersion,
            )
          )
          .futureValue
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.DuringDeploy(
          versionId
        )
      }

      eventually {
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.Running(
          versionId,
          startedAt
        )
      }
    }
  }

  "should return state correctly when state is cancelled" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

    deploymentManager1.withWaitForCancelFinish {
      deploymentService.processCommand(CancelScenarioCommand(CommonCommandData(scenario, None, user)))
      eventually {
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.DuringCancel
      }
    }
  }

  "should mark Action ExecutionFinished and publish an event as finished" in {
    val (scenario, actionId) = prepareDeployedScenario(generateScenarioName())

    actionService.markActionExecutionFinished(Streaming1.stringify, actionId).futureValue
    eventually {
      val action =
        actionRepository.getFinishedProcessActions(scenario.id, Some(Set(ScenarioActionName.Deploy))).dbioActionValues

      action.loneElement.state shouldBe ProcessActionState.ExecutionFinished
      listener.events.toArray.filter(_.isInstanceOf[OnActionExecutionFinished]) should have length 1
    }
  }

  "should mark finished scenario as finished" in {
    val (scenario, deployActionId) = prepareDeployedScenario(generateScenarioName())
    val versionId                  = VersionId(1)
    val startedAt                  = Instant.now()

    deploymentManager1.withScenarioRunning(scenario.name, versionId, startedAt) {
      checkIsFollowingDeploy(
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue,
        expected = true
      )
      fetchingScenarioDBIORepository
        .fetchLatestProcessDetails[Unit](scenario.id)
        .dbioActionValues
        .value
        .lastStateAction should not be None
    }

    deploymentManager1.withScenarioFinished(scenario.name, VersionId(1), DeploymentId.fromActionId(deployActionId)) {
      reconciler.synchronizeEngineFinishedDeploymentsLocalStatuses().futureValue
    }

    val scenarioDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetails[Unit](scenario.id).dbioActionValues.value
    val lastStateAction = scenarioDetails.lastStateAction.value
    lastStateAction.actionName shouldBe ScenarioActionName.Deploy
    lastStateAction.state shouldBe ProcessActionState.ExecutionFinished
    // we want to hide finished deploys
    scenarioDetails.lastDeployedAction shouldBe empty
    dbioRunner.run(activityRepository.findActivity(scenario.id)).futureValue.comments should have length 1

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      val stateAfterJobRetention =
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue
      stateAfterJobRetention shouldBe SimpleStateStatus.Finished(versionId)
    }

    archiveScenario(scenario)
    scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.Finished(versionId)
  }

  "should finish deployment only after DeploymentManager finishes" in {
    val scenario = prepareScenario(generateScenarioName())

    def checkStatusAction(expectedStatus: StateStatus, expectedAction: Option[ScenarioActionName]) = {
      fetchingScenarioDBIORepository
        .fetchLatestProcessDetails[Unit](scenario.id)
        .dbioActionValues
        .flatMap(_.lastStateAction)
        .map(_.actionName) shouldBe expectedAction
      scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe expectedStatus
    }

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      checkStatusAction(SimpleStateStatus.NotDeployed, None)
      deploymentManager1.withWaitForDeployFinish(scenario.name) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(scenario, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              Some(NodesDeploymentData.empty),
              scenarioSource = LatestVersion,
            )
          )
          .futureValue
        checkStatusAction(SimpleStateStatus.DuringDeploy(VersionId(1)), None)
        listener.events shouldBe Symbol("empty")
      }
    }
    val versionId = VersionId(1)
    val startedAt = Instant.now()

    deploymentManager1.withScenarioRunning(scenario.name, versionId, startedAt) {
      eventually {
        checkStatusAction(SimpleStateStatus.Running(versionId, startedAt), Some(ScenarioActionName.Deploy))
        listener.events.toArray.filter(_.isInstanceOf[OnActionSuccess]) should have length 1
      }
    }

    val activities = dbioRunner.run(activityRepository.findActivities(scenario.id)).futureValue

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
    val requestedParallelism = FlinkClientStub.maxParallelism + 1
    val scenario             = prepareScenario(generateScenarioName(), parallelism = Some(requestedParallelism))

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      val result =
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(scenario, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              Some(NodesDeploymentData.empty),
              scenarioSource = LatestVersion,
            )
          )
          .failed
          .futureValue
      result.getMessage shouldBe s"Not enough free slots on Flink cluster. Available slots: ${FlinkClientStub.maxParallelism}, requested: $requestedParallelism. " +
        s"Decrease scenario's parallelism or extend Flink cluster resources"
      deploymentManager1.successfulDeploys should not contain scenario.name
      fetchingScenarioDBIORepository
        .fetchLatestProcessDetails[Unit](scenario.id)
        .dbioActionValues
        .flatMap(_.lastStateAction) shouldBe None
      listener.events shouldBe Symbol("empty")
      // during short period of time, status will be during deploy - because parallelism validation are done in the same critical section as deployment
      eventually {
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.NotDeployed
      }
    }
  }

  "should return properly state when state is canceled and process is canceled" in {
    val (scenario, _) = prepareCanceledScenario(generateScenarioName())

    deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.Canceled) {
      scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.Canceled
    }
  }

  "should return canceled status for canceled scenario with empty state - cleaned state" in {
    val (scenario, _) = prepareCanceledScenario(generateScenarioName())

    fetchingScenarioDBIORepository
      .fetchLatestProcessDetails[Unit](scenario.id)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.Canceled
    }

    val scenarioDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetails[Unit](scenario.id).dbioActionValues.value
    scenarioDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
  }

  "should return canceled status for canceled process with not founded state - cleaned state" in {
    val (scenario, _) = prepareCanceledScenario(generateScenarioName())

    fetchingScenarioDBIORepository
      .fetchLatestProcessDetails[Unit](scenario.id)
      .dbioActionValues
      .value
      .lastStateAction should not be None

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.Canceled
    }

    val scenarioDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetails[Unit](scenario.id).dbioActionValues.value
    scenarioDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
  }

  "should return state with warning when state is running and scenario is canceled" in {
    val (scenario, _) = prepareCanceledScenario(generateScenarioName())

    deploymentManager1.withScenarioStateStatus(
      scenario.name,
      SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
    ) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return not deployed when engine returns any state and scenario hasn't action" in {
    val scenario = prepareScenario(generateScenarioName())

    deploymentManager1.withScenarioStateStatus(
      scenario.name,
      SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now)
    ) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
      state shouldBe SimpleStateStatus.NotDeployed
    }
  }

  "should return DuringCancel state when is during canceled and scenario has CANCEL action" in {
    val (scenario, _) = prepareCanceledScenario(generateScenarioName())

    deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.DuringCancel) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      state shouldBe SimpleStateStatus.DuringCancel
    }
  }

  "should return state with status Restarting when scenario has been deployed and is restarting" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

    val state =
      DeploymentStatusDetails(
        status = SimpleStateStatus.Restarting,
        deploymentId = None,
      )

    deploymentManager1.withScenarioStates(scenario.name, List(state)) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      state shouldBe SimpleStateStatus.Restarting
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Cancel)
    }
  }

  "should return state with error when state is not running and scenario is deployed" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

    deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.Canceled) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return state with error when state is null and scenario is deployed" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      val expectedStatus = ProblemStateStatus.shouldBeRunning(VersionId(1L), "admin")
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return error state when state is running and scenario is deployed with mismatch versions" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())
    val version       = VersionId(2)

    deploymentManager1.withScenarioStateVersion(
      scenario.name,
      SimpleStateStatus.Running(version, startedAt = Instant.now()),
    ) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      val expectedStatus = ProblemStateStatus.mismatchDeployedVersion(VersionId(2L), VersionId(1L), "admin")
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should always return scenario manager failure, even if some other verifications return invalid" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

    // FIXME: doesnt check recover from failed verifications ???
    deploymentManager1.withScenarioStateVersion(scenario.name, ProblemStateStatus.Failed) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      state shouldBe ProblemStateStatus.Failed
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return error state when failed to get state" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

    // FIXME: doesnt check recover from failed future of findJobStatus ???
    deploymentManager1.withScenarioStateVersion(scenario.name, ProblemStateStatus.FailedToGet) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue

      val expectedStatus = ProblemStateStatus.FailedToGet
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should return not deployed status for scenario with empty state - not deployed state" in {
    val scenario = prepareScenario(generateScenarioName())
    fetchingScenarioDBIORepository
      .fetchLatestProcessDetails[Unit](scenario.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      scenarioStatusProvider
        .getScenarioStatus(scenario)
        .futureValue shouldBe SimpleStateStatus.NotDeployed
    }

    val scenarioDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetails[Unit](scenario.id).dbioActionValues.value
    scenarioDetails.lastStateAction shouldBe None
    scenarioDetails.lastAction shouldBe None
  }

  "should return not deployed status for scenario with not found state - not deployed state" in {
    val scenario = prepareScenario(generateScenarioName())
    fetchingScenarioDBIORepository
      .fetchLatestProcessDetails[Unit](scenario.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.NotDeployed
    }

    val scenarioDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetails[Unit](scenario.id).dbioActionValues.value
    scenarioDetails.lastStateAction shouldBe None
    scenarioDetails.lastAction shouldBe None
  }

  "should return not deployed status for scenario without actions and with state (it should never happen)" in {
    val scenario = prepareScenario(generateScenarioName())
    fetchingScenarioDBIORepository
      .fetchLatestProcessDetails[Unit](scenario.id)
      .dbioActionValues
      .value
      .lastStateAction shouldBe None

    deploymentManager1.withScenarioStateStatus(
      scenario.name,
      SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
    ) {
      scenarioStatusProvider
        .getScenarioStatus(scenario)
        .futureValue shouldBe SimpleStateStatus.NotDeployed
    }

    val scenarioDetails =
      fetchingScenarioDBIORepository.fetchLatestProcessDetails[Unit](scenario.id).dbioActionValues.value
    scenarioDetails.lastStateAction shouldBe None
    scenarioDetails.lastAction shouldBe None
  }

  "should return not deployed state for archived never deployed scenario" in {
    val (scenario, _) = prepareArchivedScenario(generateScenarioName(), None)

    val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
    state shouldBe SimpleStateStatus.NotDeployed
  }

  "should return not deployed state for archived never deployed scenario with running state (it should never happen)" in {
    val (scenario, _) = prepareArchivedScenario(generateScenarioName(), None)

    deploymentManager1.withScenarioStateStatus(
      scenario.name,
      SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
    ) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
      state shouldBe SimpleStateStatus.NotDeployed
    }
  }

  "should return canceled status for archived canceled scenario" in {
    val (scenario, _) = prepareArchivedScenario(generateScenarioName(), Some(Cancel))

    val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
    state shouldBe SimpleStateStatus.Canceled
  }

  "should return canceled status for archived canceled scenario with running state (it should never happen)" in {
    val (scenario, _) = prepareArchivedScenario(generateScenarioName(), Some(Cancel))

    deploymentManager1.withScenarioStateStatus(
      scenario.name,
      SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
    ) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
      state shouldBe SimpleStateStatus.Canceled
    }
  }

  "should return not deployed state for unarchived never deployed scenario" in {
    val (scenario, _) = preparedUnarchivedScenario(generateScenarioName(), None)

    val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
    state shouldBe SimpleStateStatus.NotDeployed
  }

  "should return during deploy for scenario in deploy in progress" in {
    val (scenario, _) = preparedUnarchivedScenario(generateScenarioName(), None)
    val versionId     = VersionId(1)
    val _ = actionRepository
      .addInProgressAction(scenario.id, ScenarioActionName.Deploy, Some(versionId))
      .dbioActionValues

    val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
    state shouldBe SimpleStateStatus.DuringDeploy(versionId)
  }

  "should getScenariosStatuses bulk with the same result as for single scenario" in {
    val (_, _, runningScenarioId) = prepareScenariosInVariousStates()

    val scenarioDetails = fetchingScenarioDBIORepository
      .fetchLatestProcessesDetails[Unit](ScenarioQuery.empty)
      .dbioActionValues

    val versionId = VersionId(1)
    val startedAt = Instant.now()

    deploymentManager1.withScenarioRunning(runningScenarioId.name, versionId, startedAt) {
      val statesBasedOnCachedInProgressActionTypes = scenarioStatusProvider
        .getScenariosStatuses(scenarioDetails)
        .futureValue
        .map(_.map(_.name))

      statesBasedOnCachedInProgressActionTypes shouldBe List(
        Some("DURING_DEPLOY"),
        Some("DURING_CANCEL"),
        Some("RUNNING"),
        None
      )

      val statesBasedOnNotCachedInProgressActionTypes =
        scenarioDetails
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

  "should return not deployed status for archived never deployed scenario with running state (it should never happen)" in {
    val (scenario, _) = prepareArchivedScenario(generateScenarioName(), None)

    deploymentManager1.withScenarioStateStatus(
      scenario.name,
      SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
    ) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
      state shouldBe SimpleStateStatus.NotDeployed
    }
  }

  "should return problem status for archived deployed scenario (last action deployed instead of cancel)" in {
    val (scenario, _) = prepareArchivedScenario(generateScenarioName(), Some(Deploy))

    val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
    state shouldBe ProblemStateStatus.ArchivedShouldBeCanceled
  }

  "should return canceled status for unarchived scenario" in {
    val (scenario, _) = prepareArchivedScenario(generateScenarioName(), Some(Cancel))

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      val state = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
      state shouldBe SimpleStateStatus.Canceled
    }
  }

  "should return problem status for unarchived scenario with running state (it should never happen)" in {
    val (scenario, _) = preparedUnarchivedScenario(generateScenarioName(), Some(Cancel))

    deploymentManager1.withScenarioStateStatus(
      scenario.name,
      SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
    ) {
      val state          = scenarioStatusProvider.getScenarioStatus(scenario).futureValue
      val expectedStatus = ProblemStateStatus.shouldNotBeRunning(true)
      state shouldBe expectedStatus
      getAllowedActions(state) shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel)
    }
  }

  "should invalidate in progress scenarios" in {
    val scenario  = prepareScenario(generateScenarioName())
    val versionId = VersionId(1)

    deploymentManager1.withEmptyScenarioState(scenario.name) {
      val initialStatus = SimpleStateStatus.NotDeployed
      scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe initialStatus
      deploymentManager1.withWaitForDeployFinish(scenario.name) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(scenario, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              Some(NodesDeploymentData.empty),
              scenarioSource = LatestVersion,
            )
          )
          .futureValue
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe SimpleStateStatus.DuringDeploy(
          versionId
        )

        actionService.invalidateInProgressActions()
        scenarioStatusProvider.getScenarioStatus(scenario).futureValue shouldBe initialStatus
      }
    }
  }

  "should return problem after occurring timeout during waiting on DM response" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

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
        .getScenarioStatus(scenario)
        .futureValueEnsuringInnerException(durationLongerThanTimeout)
      status shouldBe ProblemStateStatus.FailedToGet
    }
  }

  "should fail when trying to get state for fragment" in {
    val fragment = prepareFragment(generateScenarioName())

    assertThrowsWithParent[FragmentStateException.type] {
      scenarioStatusProvider.getScenarioStatus(fragment).futureValue
    }
  }

  // TODO: add tests for more advanced things such as changes in model api
  "should recover jobs" in {
    val (scenario, _) = prepareDeployedScenario(generateScenarioName())

    deploymentManager1.withStubbedDeployResult(scenario.name) {
      reconciler.recoverNotRunningDeploymentsThatShouldBeRunning(_ => true).futureValue
    }

    eventually {
      deploymentManager1.successfulDeploys should contain(scenario.name)
    }
  }

  "should allow to deploy scenario using fragment with unspecified nodesDeploymentData" in {
    val fragment = ScenarioBuilder
      .fragment(generateScenarioName())
      .emptySink("sink", ProcessTestData.existingSinkFactory)

    val scenarioUsingFragment = ScenarioBuilder
      .streaming(generateScenarioName())
      .source("source", ProcessTestData.existingSourceFactory)
      .fragmentEnd("fragment", fragment.name.value)

    saveScenario(fragment, isFragment = true)
    val idWithName = saveScenario(scenarioUsingFragment, isFragment = false)

    deploymentManager1.withStubbedDeployResult(scenarioUsingFragment.name) {
      deploymentService
        .processCommand(
          RunDeploymentCommand(
            commonData = CommonCommandData(processIdWithName = idWithName, comment = None, user = user),
            stateRestoringStrategy = RestoreStateFromReplacedJobSavepoint,
            nodesDeploymentData = None,
            scenarioSource = LatestVersion
          )
        )
        .futureValue
    }
  }

  "should allow to deploy scenario when active scenarios count is less than the limit" when {
    "one processing type is considered" when {
      "1st scenario is running, and the 2nd scenario is not deployed" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val scenario2      = prepareNotDeployedScenario(generateScenarioName("scenario2"))

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager1.withScenarioStateStatus(scenario2.name, SimpleStateStatus.NotDeployed) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is running, and the 2nd scenario is cancelled" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareCanceledScenario(generateScenarioName("scenario2"))

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager1.withScenarioStateStatus(scenario2.name, SimpleStateStatus.Canceled) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is running, and the 2nd scenario is during cancel" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareCanceledScenario(generateScenarioName("scenario2"))

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager1.withScenarioStateStatus(scenario2.name, SimpleStateStatus.DuringCancel) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is running, and the 2nd scenario is finished" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))

        deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager1.withScenarioStateStatus(scenario2.name, SimpleStateStatus.Finished(VersionId(1))) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is general problem" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager1.withScenarioStateStatus(scenario2.name, StateStatus("PROBLEM")) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is being redeployed, when the 2nd scenario is running" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager1.withScenarioStateStatus(
            scenario2.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            redeployExampleScenario(scenario1)
          }
        }

        result.externalDeploymentId should not be None
      }
    }
    "two processing types are considered" when {
      "1st scenario is running (streaming1), and the 2nd scenario is not deployed (streaming2)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val scenario2      = prepareNotDeployedScenario(generateScenarioName("scenario2"), Streaming2)

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager2.withScenarioStateStatus(scenario2.name, SimpleStateStatus.NotDeployed) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is running (streaming1), and the 2nd scenario is cancelled (streaming2)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareCanceledScenario(generateScenarioName("scenario2"), Streaming2)

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager2.withScenarioStateStatus(scenario2.name, SimpleStateStatus.Canceled) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is running (streaming1), and the 2nd scenario is during cancel (streaming2)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareCanceledScenario(generateScenarioName("scenario2"), Streaming2)

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager2.withScenarioStateStatus(scenario2.name, SimpleStateStatus.DuringCancel) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is running (streaming1), and the 2nd scenario is finished (streaming2)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager2.withScenarioStateStatus(scenario2.name, SimpleStateStatus.Finished(VersionId(1))) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is running (streaming1), and the 2nd scenario is general problem (streaming2)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager2.withScenarioStateStatus(scenario2.name, StateStatus("PROBLEM")) {
            deployExampleScenario(generateScenarioName("scenario3"))
          }
        }

        result should not be None
      }
      "1st scenario is being redeployed (streaming1), when the 2nd scenario is running (streaming2)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)

        val result = deploymentManager1.withScenarioStateStatus(
          scenario1.name,
          SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
        ) {
          deploymentManager2.withScenarioStateStatus(
            scenario2.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            redeployExampleScenario(scenario1)
          }
        }

        result.externalDeploymentId should not be None
      }
    }
  }

  "should not allow more scenarios than active scenario limits to be used" when {
    "one processing type is considered" when {
      "1st scenario is running, and the 2nd scenario is running, and the 3rd scenario is not deployed" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"))

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager1.withScenarioStateStatus(
              scenario2.name,
              SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
            ) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is during deploy, and the 3rd scenario is not deployed" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"))

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager1.withScenarioStateStatus(scenario2.name, SimpleStateStatus.DuringDeploy(VersionId(1))) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running, and the 2nd scenario is restarting, and the 3rd scenario is not deployed" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"))

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager1.withScenarioStateStatus(scenario2.name, SimpleStateStatus.Restarting) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running, and the 2nd scenario has 'should not be running' problem, and the 3rd scenario is not deployed" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"))

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager1.withScenarioStateStatus(scenario2.name, ProblemStateStatus.shouldNotBeRunning(true)) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running, and the 2nd scenario has 'multiple jobs are running' problem, and the 3rd scenario is not deployed" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"))
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"))
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"))

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager1.withScenarioStateStatus(scenario2.name, exampleMultipleJobsRunningStatus) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
    }
    "two processing types are considered" when {
      "1st scenario is running (streaming1), and the 2nd scenario is running (streaming2), and the 3rd scenario is not deployed (streaming1)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"), Streaming1)

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager2.withScenarioStateStatus(
              scenario2.name,
              SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
            ) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is during deploy (streaming2), and the 3rd scenario is not deployed (streaming1)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"), Streaming1)

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager2.withScenarioStateStatus(scenario2.name, SimpleStateStatus.DuringDeploy(VersionId(1))) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario is restarting (streaming2), and the 3rd scenario is not deployed (streaming1)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"), Streaming1)

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager2.withScenarioStateStatus(scenario2.name, SimpleStateStatus.Restarting) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario has 'should not be running' problem, and the 3rd scenario is not deployed (streaming1)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"), Streaming1)

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager2.withScenarioStateStatus(scenario2.name, ProblemStateStatus.shouldNotBeRunning(true)) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
      "1st scenario is running (streaming1), and the 2nd scenario has 'multiple jobs are running' problem, and the 3rd scenario is not deployed (streaming1)" in {
        val (scenario1, _) = prepareDeployedScenario(generateScenarioName("scenario1"), Streaming1)
        val (scenario2, _) = prepareDeployedScenario(generateScenarioName("scenario2"), Streaming2)
        val scenario3      = prepareNotDeployedScenario(generateScenarioName("scenario3"), Streaming1)

        assertThrowsWithParent[MaxActiveScenariosCountExceededError] {
          deploymentManager1.withScenarioStateStatus(
            scenario1.name,
            SimpleStateStatus.Running(VersionId(1), startedAt = Instant.now())
          ) {
            deploymentManager2.withScenarioStateStatus(scenario2.name, exampleMultipleJobsRunningStatus) {
              deploymentManager1.withScenarioStateStatus(scenario3.name, SimpleStateStatus.NotDeployed) {
                deployExampleScenario(generateScenarioName("scenario4"))
              }
            }
          }
        }
      }
    }
  }

  "should deploy scenario taking scenario from different sources" when {
    "should deploy scenario latest version" in {
      val scenarioName = generateScenarioName()
      val result =
        deploymentManager1.withScenarioStateStatus(ProcessName(scenarioName), SimpleStateStatus.NotDeployed) {
          deployExampleScenario(scenarioName, LatestVersion)
        }

      result should not be None
    }

    "should deploy scenario from given scenario graph" in {
      val scenarioName  = generateScenarioName()
      val scenario      = prepareScenario(scenarioName)
      val scenarioGraph = graphForExampleScenarioWithAdditionalVariableAdded()

      val result = deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.NotDeployed) {
        deployExistingScenario(scenario, FromGraph(scenarioGraph, None, VersionId(1L)))
      }
      val activities = activityRepository.findActivities(scenario.id).dbioActionValues

      result should not be None
      mapActivitiesToTypeWithVersionAndUser(activities) shouldBe Seq(
        ("CREATED", 1L, "admin"),
        ("MODIFIED", 2L, "Nussknacker"),
        ("DEPLOYED", 2L, "admin"),
      )
    }

    "should deploy given scenario version with scenario update" in {
      val scenarioName      = generateScenarioName()
      val scenario          = prepareScenario(scenarioName)
      val maybeNewVersionId = updateScenario(scenario)
      val scenarioGraph     = graphForExampleScenarioWithAdditionalVariableAdded()

      maybeNewVersionId.value shouldBe VersionId(2)

      val result = deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.NotDeployed) {
        deployExistingScenario(scenario, FromGraph(scenarioGraph, None, VersionId(2)))
      }
      val activities = activityRepository.findActivities(scenario.id).dbioActionValues

      result should not be None
      mapActivitiesToTypeWithVersionAndUser(activities) shouldBe Seq(
        ("CREATED", 1L, "admin"),
        ("MODIFIED", 2L, "admin"),
        ("MODIFIED", 3L, "Nussknacker"),
        ("DEPLOYED", 3L, "admin"),
      )
    }

    "should deploy scenario from given scenario graph without updating scenario if scenario graph is the same" in {
      val scenarioName = generateScenarioName()
      val scenario     = prepareScenario(scenarioName)
      updateScenario(scenario, "newVariable2").value shouldBe VersionId(2)
      updateScenario(scenario, "newVariable3").value shouldBe VersionId(3)
      val scenarioGraph = graphForExampleScenarioWithAdditionalVariableAdded("newVariable2")

      val result = deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.NotDeployed) {
        deployExistingScenario(scenario, FromGraph(scenarioGraph, None, VersionId(2L)))
      }
      val activities = activityRepository.findActivities(scenario.id).dbioActionValues

      result should not be None
      mapActivitiesToTypeWithVersionAndUser(activities) shouldBe Seq(
        ("CREATED", 1L, "admin"),
        ("MODIFIED", 2L, "admin"),
        ("MODIFIED", 3L, "admin"),
        ("DEPLOYED", 2L, "admin"),
      )
    }

    "should deploy scenario from given scenario graph without updating scenario if scenario graph is the same as latest version" in {
      val scenarioName = generateScenarioName()
      val scenario     = prepareScenario(scenarioName)
      updateScenario(scenario, "newVariable2").value shouldBe VersionId(2)
      updateScenario(scenario, "newVariable3").value shouldBe VersionId(3)
      val scenarioGraph = graphForExampleScenarioWithAdditionalVariableAdded("newVariable3")

      val result = deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.NotDeployed) {
        deployExistingScenario(scenario, FromGraph(scenarioGraph, None, VersionId(2L)))
      }
      val activities = activityRepository.findActivities(scenario.id).dbioActionValues

      result should not be None
      mapActivitiesToTypeWithVersionAndUser(activities) shouldBe Seq(
        ("CREATED", 1L, "admin"),
        ("MODIFIED", 2L, "admin"),
        ("MODIFIED", 3L, "admin"),
        ("DEPLOYED", 2L, "admin"),
      )
    }

    "should not deploy unsaved scenario when user hasn't permission to write scenario" in {
      val scenarioName  = generateScenarioName()
      val scenario      = prepareScenario(scenarioName)
      val scenarioGraph = graphForExampleScenarioWithAdditionalVariableAdded()
      val userWithoutWritePermission = TestFactory.user(
        username = "test",
        permissions = List(Permission.Read, Permission.Deploy)
      )

      intercept[TestFailedException] {
        deploymentManager1.withScenarioStateStatus(scenario.name, SimpleStateStatus.NotDeployed) {
          deployExistingScenario(scenario, FromGraph(scenarioGraph, None, VersionId(1)))(userWithoutWritePermission)
        }
      } should matchPattern {
        case ex: TestFailedException if ex.getCause.isInstanceOf[UnauthorizedError] =>
      }
    }
  }

  private def deployExampleScenario(
      scenarioName: String = generateScenarioName(),
      scenarioGraphSource: ScenarioGraphSource = LatestVersion,
  ): Option[ExternalDeploymentId] = {
    val scenario = prepareScenario(scenarioName)
    deployExistingScenario(scenario, scenarioGraphSource)
  }

  private def deployExistingScenario(
      scenario: ProcessIdWithName,
      scenarioGraphSource: ScenarioGraphSource = LatestVersion,
  )(implicit user: LoggedUser): Option[ExternalDeploymentId] = {
    deploymentManager1
      .withWaitForDeployFinish(scenario.name, result = Some(ExternalDeploymentId("1"))) {
        deploymentService
          .processCommand(
            RunDeploymentCommand(
              CommonCommandData(scenario, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              Some(NodesDeploymentData.empty),
              scenarioSource = scenarioGraphSource,
            )
          )
          .futureValue
      }
      .externalDeploymentId
      .futureValue
  }

  private def redeployExampleScenario(scenarioId: ProcessIdWithName): RunDeploymentResult = {
    deploymentManager1
      .withWaitForDeployFinish(scenarioId.name, result = Some(ExternalDeploymentId("1"))) {
        deploymentService
          .processCommand(
            RunRedeploymentCommand(
              CommonCommandData(scenarioId, None, user),
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
              Some(NodesDeploymentData.empty),
              scenarioSource = LatestVersion,
            )
          )
      }
      .futureValue
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    listener.clear()
    deploymentManager1.successfulDeploys.clear()
    deploymentManager2.successfulDeploys.clear()
  }

  private def checkIsFollowingDeploy(status: StateStatus, expected: Boolean) = {
    withClue(status) {
      SimpleStateStatus.isDefaultFollowingDeployStatus(status) shouldBe expected
    }
  }

  private def prepareCanceledScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): (ProcessIdWithName, ProcessActionId) = {
    val (scenario, _)  = prepareDeployedScenario(scenarioName, processingType)
    val cancelActionId = prepareAction(scenario.id, Cancel)
    (scenario, cancelActionId)
  }

  private def prepareDeployedScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): (ProcessIdWithName, ProcessActionId) =
    prepareScenarioWithAction(scenarioName, processingType, Some(Deploy)) match {
      case (scenario, Some(actionId)) => (scenario, actionId)
      case (_, None) => throw new IllegalStateException("Deploy actionId should be defined for deployed scenario")
    }

  private def prepareNotDeployedScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName =
    prepareScenario(scenarioName, processingType)

  private def preparedUnarchivedScenario(
      scenarioName: String,
      actionNameOpt: Option[ScenarioActionName]
  ): (ProcessIdWithName, Option[ProcessActionId]) = {
    val (scenario, actionIdOpt) = prepareArchivedScenario(scenarioName, actionNameOpt)
    writeProcessRepository
      .archive(processId = scenario, isArchived = false)
      .dbioActionValues
    actionRepository
      .addInstantAction(
        scenario.id,
        VersionId.initialVersionId,
        ScenarioActionName.UnArchive,
        None
      )
      .dbioActionValues
    (scenario, actionIdOpt)
  }

  private def prepareArchivedScenario(
      scenarioName: String,
      actionNameOpt: Option[ScenarioActionName],
      processingType: TestProcessingType = Streaming1,
  ): (ProcessIdWithName, Option[ProcessActionId]) = {
    val (scenario, actionIdOpt) = prepareScenarioWithAction(scenarioName, processingType, actionNameOpt)
    archiveScenario(scenario)
    (scenario, actionIdOpt)
  }

  private def archiveScenario(scenario: ProcessIdWithName): Unit = {
    writeProcessRepository
      .archive(processId = scenario, isArchived = true)
      .flatMap(_ =>
        actionRepository.addInstantAction(scenario.id, VersionId.initialVersionId, ScenarioActionName.Archive, None)
      )
      .dbioActionValues
  }

  private def prepareScenariosInVariousStates(): (ProcessIdWithName, ProcessIdWithName, ProcessIdWithName) = {
    val duringDeployScenarioName :: duringCancelScenarioName :: otherScenario :: fragmentName :: Nil =
      (1 to 4).map(_ => generateScenarioName()).toList

    val (duringDeployScenario, _) = preparedUnarchivedScenario(duringDeployScenarioName, None)
    val (duringCancelScenario, _) = prepareDeployedScenario(duringCancelScenarioName)
    actionRepository
      .addInProgressAction(duringDeployScenario.id, ScenarioActionName.Deploy, Some(VersionId.initialVersionId))
      .dbioActionValues
    actionRepository
      .addInProgressAction(duringCancelScenario.id, ScenarioActionName.Cancel, Some(VersionId.initialVersionId))
      .dbioActionValues
    val (deployedScenario, _) = prepareDeployedScenario(otherScenario)
    prepareFragment(fragmentName)

    (duringDeployScenario, duringCancelScenario, deployedScenario)
  }

  private def prepareScenarioWithAction(
      scenarioName: String,
      processingType: TestProcessingType,
      actionNameOpt: Option[ScenarioActionName]
  ): (ProcessIdWithName, Option[ProcessActionId]) = {
    val scenario    = prepareScenario(generateScenarioName(scenarioName), processingType)
    val actionIdOpt = actionNameOpt.map(prepareAction(scenario.id, _))
    (scenario, actionIdOpt)
  }

  private def prepareAction(scenarioId: ProcessId, actionName: ScenarioActionName): ProcessActionId = {
    val comment = Comment.from(actionName.toString.capitalize)
    actionRepository
      .addInstantAction(scenarioId, VersionId.initialVersionId, actionName, comment)
      .map(_.id)
      .dbioActionValues
  }

  private def prepareScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1,
      parallelism: Option[Int] = None
  ): ProcessIdWithName = {
    val baseBuilder = ScenarioBuilder
      .streaming(scenarioName)
    val canonicalProcess = parallelism
      .map(baseBuilder.parallelism)
      .getOrElse(baseBuilder)
      .source("source", ProcessTestData.existingSourceFactory)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
    saveScenario(canonicalProcess, isFragment = false, processingType)
  }

  private def saveScenario(
      scenario: CanonicalProcess,
      isFragment: Boolean,
      processingType: TestProcessingType = Streaming1
  ) = {
    val action = CreateProcessAction(
      processName = scenario.name,
      category = "Category1",
      canonicalProcess = scenario,
      processingType = processingType.stringify,
      isFragment = isFragment,
    )
    writeProcessRepository
      .saveNewProcess(action)
      .map(_.value.processId)
      .map(ProcessIdWithName(_, scenario.name))
      .dbioActionValues
  }

  private def updateScenario(
      scenario: ProcessIdWithName,
      newVariableId: String = "newVariableAddedWithUpdateScenario"
  ): Option[VersionId] = {
    val canonicalProcess = ScenarioBuilder
      .streaming(scenario.name.value)
      .source("source", ProcessTestData.existingSourceFactory)
      .buildSimpleVariable(newVariableId, "newVar", "updated process with new variable".spelTemplate)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
    val action = UpdateProcessAction(
      processId = scenario.id,
      canonicalProcess = canonicalProcess,
      comment = Comment.from("Update Comment"),
      labels = Nil,
      increaseVersionWhenJsonNotChanged = true
    )
    writeProcessRepository
      .updateProcess(action)
      .map(_.newVersion)
      .dbioActionValues
  }

  private def prepareFragment(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    val canonicalProcess = ScenarioBuilder
      .fragment(scenarioName)
      .emptySink("end", "end")

    val action = CreateProcessAction(
      processName = ProcessName(scenarioName),
      category = "Category1",
      canonicalProcess = canonicalProcess,
      processingType = processingType.stringify,
      isFragment = true,
    )

    writeProcessRepository
      .saveNewProcess(action)
      .map(_.value.processId)
      .map(ProcessIdWithName(_, ProcessName(scenarioName)))
      .dbioActionValues
  }

  private def generateScenarioName(prefix: String = "process"): String = {
    s"${prefix}_${UUID.randomUUID()}"
  }

  private def getAllowedActions(status: StateStatus, deploymentManager: MockDeploymentManager = deploymentManager1) = {
    deploymentManager.processStateDefinitionManager.allowedActions(
      ScenarioStatusWithScenarioContext(
        scenarioStatus = status,
        deployedVersionId = None,
        currentlyPresentedVersionId = None
      )
    )
  }

  private def exampleMultipleJobsRunningStatus = ProblemStateStatus.multipleJobsRunning(
    first = DeploymentId(UUID.randomUUID().toString)  -> SimpleStateStatus.Running(VersionId(0), Instant.now()),
    second = DeploymentId(UUID.randomUUID().toString) -> SimpleStateStatus.Running(VersionId(0), Instant.now())
  )

  private def graphForExampleScenarioWithAdditionalVariableAdded(
      variableId: String = "newVariableAddedFromGraph"
  ): ScenarioGraph = {
    ScenarioBuilder
      .streaming("scenario-for-deployment")
      .source("source", ProcessTestData.existingSourceFactory)
      .buildSimpleVariable(variableId, "newVar", "updated process with new variable".spelTemplate)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
      .toScenarioGraph
  }

  private def mapActivitiesToTypeWithVersionAndUser(activities: Seq[ScenarioActivity]): Seq[(String, Long, String)] =
    activities.map {
      case activity: DeploymentRelatedActivity =>
        ("DEPLOYED", activity.scenarioVersionId.value.value, activity.user.name.value)
      case ScenarioActivity.ScenarioCreated(_, _, user, _, scenarioVersionId) =>
        ("CREATED", scenarioVersionId.value.value, user.name.value)
      case ScenarioActivity.ScenarioModified(_, _, user, _, _, scenarioVersionId, _) =>
        ("MODIFIED", scenarioVersionId.value.value, user.name.value)
      case other =>
        ("OTHER", other.scenarioVersionId.value.value, other.user.name.value)
    }

}
