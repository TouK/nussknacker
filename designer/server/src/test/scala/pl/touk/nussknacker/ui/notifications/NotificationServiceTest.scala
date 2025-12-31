package pl.touk.nussknacker.ui.notifications

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.ScenarioActivity.ScenarioDeployed
import pl.touk.nussknacker.engine.api.deployment.ScenarioComment.WithoutContent
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId, LatestVersion}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.domain.TestFactory.scenarioParametersServiceProvider
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.limits.{GlobalLimitsConfig, LimitsService}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.notifications.NotificationService.NotificationsScope
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.{
  ScenarioStatusProvider => OldApproachScenarioStatusProvider
}
import pl.touk.nussknacker.ui.process.newdeployment.{ScenarioStatusProvider => NewApproachScenarioStatusProvider}
import pl.touk.nussknacker.ui.process.repository.{
  DBIOActionRunner,
  DbScenarioActionRepository,
  ScenarioWithDetailsEntity
}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository.activities.DbScenarioActivityRepository
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.InMemoryTimeseriesRepository

import java.time.{Clock, Duration, Instant, ZoneId}
import java.time.temporal.ChronoUnit
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class NotificationServiceTest
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with MockitoSugar
    with WithHsqlDbTesting
    with EitherValuesDetailedMessage
    with OptionValues
    with DBIOActionValues
    with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName)
  private implicit val executionContextWithIORuntime: ExecutionContextWithIORuntimeAdapter =
    ExecutionContextWithIORuntimeAdapter.unsafeCreateFrom(system.dispatcher)

  override protected val dbioRunner: DBIOActionRunner = DBIOActionRunner(testDbRef)

  private var currentInstant: Instant    = Instant.ofEpochMilli(0)
  private val clock: Clock               = clockForInstant(() => currentInstant)
  private val processRepository          = TestFactory.newFutureFetchingScenarioRepository(testDbRef)
  private val dbProcessRepository        = TestFactory.newFetchingProcessRepository(testDbRef)
  private val writeProcessRepository     = TestFactory.newWriteProcessRepository(testDbRef, clock)
  private val scenarioActivityRepository = DbScenarioActivityRepository.create(testDbRef, clock)

  private val scenarioActivityService = new FetchScenarioActivityService(
    scenarioActivityRepository = scenarioActivityRepository,
    fetchingProcessRepository = processRepository,
    dbioActionRunner = dbioRunner
  )

  private val actionRepository = DbScenarioActionRepository.create(testDbRef)

  private val expectedRefreshAfterSuccess = List(DataToRefresh.activity, DataToRefresh.state)
  private val expectedRefreshAfterFail    = List(DataToRefresh.state)

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate().futureValue
    executionContextWithIORuntime.close()
  }

  test("Should return only events for user in given time") {
    val processName       = ProcessName("fooProcess")
    val id                = saveSampleProcess(processName)
    val processIdWithName = ProcessIdWithName(id, processName)

    val deploymentManager                           = mock[DeploymentManager]
    val (deploymentService, _, notificationService) = createServices(deploymentManager, deploymentStatuses = List.empty)

    def notificationsFor(user: LoggedUser): List[Notification] =
      notificationService
        .notifications(NotificationsScope.NotificationsForLoggedUser(user))
        .futureValue

    def deployProcess(
        givenDeployResult: Try[Option[ExternalDeploymentId]],
        user: LoggedUser
    ): Option[ExternalDeploymentId] = {
      when(deploymentManager.liveDataPreviewSupport).thenReturn(NoLiveDataPreviewSupport)
      when(
        deploymentManager.processCommand(any[DMRunDeploymentCommand])
      ).thenReturn(Future.fromTry(givenDeployResult))
      when(deploymentManager.processStateDefinitionManager).thenReturn(SimpleProcessStateDefinitionManager)
      deploymentService
        .processCommand(
          RunDeploymentCommand(
            commonData = CommonCommandData(processIdWithName, None, user),
            nodesDeploymentData = Some(NodesDeploymentData.empty),
            stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            scenarioSource = LatestVersion,
          )
        )
        .futureValue
        .externalDeploymentId
        .futureValue
    }

    val userForSuccess = TestFactory.adminUser("successUser", "successUser")
    val userForFail    = TestFactory.adminUser("failUser", "failUser")

    deployProcess(Success(None), userForSuccess)
    notificationsFor(userForSuccess).map(_.toRefresh) shouldBe List(expectedRefreshAfterSuccess)

    val givenException = new RuntimeException("Failure")
    intercept[TestFailedException] {
      deployProcess(Failure(givenException), userForFail)
    } should matchPattern {
      case ex: TestFailedException if ex.getCause == givenException =>
    }

    notificationsFor(userForFail).map(_.toRefresh) shouldBe List(expectedRefreshAfterFail)

    notificationsFor(userForFail).map(_.toRefresh) shouldBe List(
      expectedRefreshAfterFail
    )

    currentInstant = currentInstant.plus(1, ChronoUnit.HOURS)
    notificationsFor(userForFail).map(_.toRefresh) shouldBe Symbol("empty")
  }

  test("Should return events for user and for scenario in given time") {
    val processName       = ProcessName("fooProcess")
    val id                = saveSampleProcess(processName)
    val processIdWithName = ProcessIdWithName(id, processName)

    val deploymentManager                           = mock[DeploymentManager]
    val (deploymentService, _, notificationService) = createServices(deploymentManager, deploymentStatuses = List.empty)

    def notificationsFor(user: LoggedUser): List[Notification] =
      notificationService
        .notifications(NotificationsScope.NotificationsForLoggedUserAndScenario(user, processName))
        .futureValue

    def deployProcess(
        givenDeployResult: Try[Option[ExternalDeploymentId]],
        user: LoggedUser
    ): Option[ExternalDeploymentId] = {
      when(deploymentManager.liveDataPreviewSupport).thenReturn(NoLiveDataPreviewSupport)
      when(
        deploymentManager.processCommand(any[DMRunDeploymentCommand])
      ).thenReturn(Future.fromTry(givenDeployResult))
      when(deploymentManager.processStateDefinitionManager).thenReturn(SimpleProcessStateDefinitionManager)
      deploymentService
        .processCommand(
          RunDeploymentCommand(
            commonData = CommonCommandData(processIdWithName, None, user),
            nodesDeploymentData = Some(NodesDeploymentData.empty),
            stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            scenarioSource = LatestVersion,
          )
        )
        .futureValue
        .externalDeploymentId
        .futureValue
    }

    val firstUser  = TestFactory.adminUser("firstUser", "firstUser")
    val secondUser = TestFactory.adminUser("secondUser", "secondUser")

    deployProcess(Success(None), firstUser)
    deployProcess(Success(None), secondUser)

    val notifications = notificationsFor(secondUser)
    notifications shouldBe List(
      Notification(
        notifications.head.id,
        Some(processName),
        "Deployment finished",
        None,
        List(DataToRefresh.activity, DataToRefresh.state)
      ),
      Notification(
        notifications(1).id,
        Some(processName),
        "SCENARIO_CREATED",
        None,
        List(DataToRefresh.activity)
      ),
      Notification(
        notifications(2).id,
        Some(processName),
        "SCENARIO_DEPLOYED",
        None,
        List(DataToRefresh.activity, DataToRefresh.state)
      ),
      Notification(
        notifications(3).id,
        Some(processName),
        "SCENARIO_DEPLOYED",
        None,
        List(DataToRefresh.activity, DataToRefresh.state)
      )
    )
  }

  test("should refresh after action execution finished") {
    val processName       = ProcessName("process-execution-finished")
    val id                = saveSampleProcess(processName)
    val processIdWithName = ProcessIdWithName(id, processName)

    val deploymentManager = mock[DeploymentManager]
    val (deploymentService, actionService, notificationService) =
      createServices(deploymentManager, deploymentStatuses = List.empty)

    var passedDeploymentId = Option.empty[DeploymentId]

    def deployProcess(
        givenDeployResult: Try[Option[ExternalDeploymentId]],
        user: LoggedUser
    ): Option[ExternalDeploymentId] = {
      when(deploymentManager.liveDataPreviewSupport).thenReturn(NoLiveDataPreviewSupport)
      when(
        deploymentManager.processCommand(any[DMRunDeploymentCommand])
      ).thenAnswer { invocation =>
        passedDeploymentId = Some(invocation.getArgument[DMRunDeploymentCommand](0).deploymentData.deploymentId)
        Future.fromTry(givenDeployResult)
      }
      when(deploymentManager.processStateDefinitionManager).thenReturn(SimpleProcessStateDefinitionManager)
      deploymentService
        .processCommand(
          RunDeploymentCommand(
            commonData = CommonCommandData(processIdWithName, None, user),
            nodesDeploymentData = Some(NodesDeploymentData.empty),
            stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            scenarioSource = LatestVersion,
          )
        )
        .futureValue
        .externalDeploymentId
        .futureValue
    }

    val user = TestFactory.adminUser("fooUser", "fooUser")
    deployProcess(Success(None), user)
    val notificationsAfterDeploy =
      notificationService
        .notifications(NotificationsScope.NotificationsForLoggedUser(user))
        .futureValue

    notificationsAfterDeploy should have length 1
    val deployNotificationId = notificationsAfterDeploy.head.id

    actionService
      .markActionExecutionFinished(Streaming.stringify, passedDeploymentId.value.toActionIdOpt.value)
      .futureValue
    val notificationAfterExecutionFinished =
      notificationService
        .notifications(NotificationsScope.NotificationsForLoggedUser(user))
        .futureValue
    // old notification about deployment is replaced by notification about deployment execution finished which has other id
    notificationAfterExecutionFinished should have length 1
    notificationAfterExecutionFinished.head.id should not equal deployNotificationId
  }

  test("should notify about redeployment") {
    val processName       = ProcessName("redeploy")
    val id                = saveSampleProcess(processName)
    val processIdWithName = ProcessIdWithName(id, processName)

    val deploymentManager = mock[DeploymentManager]
    val deployId = scenarioActivityRepository
      .addActivity(
        ScenarioDeployed(
          ScenarioId(id.value),
          ScenarioActivityId.random,
          ScenarioUser.internalNuUser,
          Instant.ofEpochMilli(0),
          Some(ScenarioVersionId(1)),
          WithoutContent(UserName("foo"), Instant.ofEpochMilli(0)),
          DeploymentResult.Success(Instant.ofEpochMilli(0))
        )
      )
      .dbioActionValues
    val (deploymentService, actionService, notificationService) = createServices(
      deploymentManager,
      deploymentStatuses = List(
        DeploymentStatusDetails(
          SimpleStateStatus.Running(VersionId(1), Instant.ofEpochMilli(0)),
          Some(DeploymentId(deployId.value.toString))
        )
      )
    )

    var passedDeploymentId = Option.empty[DeploymentId]

    def redeployProcess(
        givenDeployResult: Try[Option[ExternalDeploymentId]],
        user: LoggedUser
    ): Option[ExternalDeploymentId] = {
      when(deploymentManager.liveDataPreviewSupport).thenReturn(NoLiveDataPreviewSupport)
      when(
        deploymentManager.processCommand(any[DMRunDeploymentCommand])
      ).thenAnswer { invocation =>
        passedDeploymentId = Some(invocation.getArgument[DMRunDeploymentCommand](0).deploymentData.deploymentId)
        Future.fromTry(givenDeployResult)
      }
      when(deploymentManager.processStateDefinitionManager).thenReturn(SimpleProcessStateDefinitionManager)
      deploymentService
        .processCommand(
          RunRedeploymentCommand(
            commonData = CommonCommandData(processIdWithName, None, user),
            nodesDeploymentData = Some(NodesDeploymentData.empty),
            stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
            scenarioSource = LatestVersion,
          )
        )
        .futureValue
        .externalDeploymentId
        .futureValue
    }

    val user = TestFactory.adminUser("fooUser", "fooUser")
    redeployProcess(Success(None), user)
    val notificationsAfterDeploy =
      notificationService
        .notifications(NotificationsScope.NotificationsForLoggedUser(user))
        .futureValue

    notificationsAfterDeploy should have length 1
    val redeployNotificationId = notificationsAfterDeploy.head.id

    actionService
      .markActionExecutionFinished(Streaming.stringify, passedDeploymentId.value.toActionIdOpt.value)
      .futureValue
    val notificationAfterExecutionFinished =
      notificationService
        .notifications(NotificationsScope.NotificationsForLoggedUser(user))
        .futureValue
    // old notification about deployment is replaced by notification about deployment execution finished which has other id
    notificationAfterExecutionFinished should have length 1
    notificationAfterExecutionFinished.head.id should not equal redeployNotificationId
  }

  private def createServices(
      deploymentManager: DeploymentManager,
      deploymentStatuses: List[DeploymentStatusDetails]
  ) = {
    when(deploymentManager.getScenarioDeploymentsStatuses(any[ProcessName])(any[DataFreshnessPolicy]))
      .thenAnswer((_: InvocationOnMock) => Future.successful(WithDataFreshnessStatus.fresh(deploymentStatuses)))
    val managerDispatcher = mock[DeploymentManagerDispatcher]
    when(managerDispatcher.deploymentManager(any[String])(any[LoggedUser])).thenReturn(Some(deploymentManager))
    when(managerDispatcher.deploymentManagerUnsafe(any[String])(any[LoggedUser])).thenReturn(deploymentManager)
    val config                       = NotificationConfig(20 minutes)
    val globalNotificationRepository = InMemoryTimeseriesRepository[Notification](Duration.ofHours(1), clock)
    val notificationService = new NotificationServiceImpl(
      scenarioActivityService,
      actionRepository,
      globalNotificationRepository,
      dbioRunner,
      config,
      clock
    )
    val oldApproachScenarioStatusProvider = new OldApproachScenarioStatusProvider(
      new EngineSideDeploymentStatusesProvider(managerDispatcher, scenarioStateTimeout = None),
      managerDispatcher,
      dbProcessRepository,
      actionRepository,
      dbioRunner
    )
    val newApproachScenarioStatusProvider = new NewApproachScenarioStatusProvider(
      TestFactory.mapProcessingTypeDataProvider(Streaming.stringify -> ((): Unit)),
      TestFactory.newDeploymentRepository(testDbRef, clock),
      dbioRunner
    )
    val processService = mock[ProcessService]
    val actionService = new ActionService(
      dbProcessRepository,
      actionRepository,
      dbioRunner,
      mock[ProcessChangeListener],
      oldApproachScenarioStatusProvider,
      None,
      clock,
      processService,
      scenarioParametersServiceProvider()
    )
    val actionInfoService = mock[ActionInfoService]
    val deploymentService = new DeploymentService(
      managerDispatcher,
      TestFactory.processValidatorByProcessingType(),
      TestFactory.scenarioResolverByProcessingType(),
      actionService,
      TestFactory.additionalComponentConfigsByProcessingType,
      new LimitsService(
        globalLimitsConfig = GlobalLimitsConfig.default,
        perProcessingTypesLimitsProvider =
          TestFactory.mapProcessingTypeDataProvider(Streaming.stringify -> LimitsConfig.default),
        oldDeploymentsApproachScenarioStatusProvider = oldApproachScenarioStatusProvider,
        newDeploymentsApproachScenarioStatusProvider = newApproachScenarioStatusProvider,
      ),
      TestFactory.mapProcessingTypeDataProvider(Streaming.stringify -> actionInfoService),
      TestFactory.newLiveDataRepository(testDbRef),
      dbioRunner,
    ) {

      override protected def validateUsingDeploymentManager(
          scenarioDetails: ScenarioWithDetailsEntity[CanonicalProcess],
          runDeploymentCommand: DMRunDeploymentCommand,
      )(implicit user: LoggedUser): Future[Unit] = Future.successful(())
    }
    (deploymentService, actionService, notificationService)
  }

  private def saveSampleProcess(processName: ProcessName) = {
    val sampleScenario = ScenarioBuilder
      .streaming(processName.value)
      .source("source", ProcessTestData.existingSourceFactory)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
    val action =
      CreateProcessAction(
        processName = processName,
        category = "Default",
        canonicalProcess = sampleScenario,
        processingType = Streaming.stringify,
        isFragment = false,
      )
    writeProcessRepository
      .saveNewProcess(action)(TestFactory.adminUser())
      .map(_.value.processId)
      .dbioActionValues
  }

  private def clockForInstant(currentInstant: () => Instant): Clock = {
    new Clock {
      override def getZone: ZoneId = ZoneId.systemDefault()

      override def withZone(zone: ZoneId): Clock = ???

      override def instant(): Instant = currentInstant()
    }
  }

}
