package pl.touk.nussknacker.ui.notifications

import akka.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions._
import pl.touk.nussknacker.ui.process.deployment.{DeploymentManagerDispatcher, DeploymentServiceImpl, ScenarioResolver}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository.{
  DBIOActionRunner,
  DbProcessActionRepository,
  ScenarioWithDetailsEntity
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class NotificationServiceTest
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with MockitoSugar
    with WithHsqlDbTesting
    with EitherValuesDetailedMessage
    with OptionValues
    with DBIOActionValues {

  private implicit val system: ActorSystem            = ActorSystem(getClass.getSimpleName)
  override protected val dbioRunner: DBIOActionRunner = DBIOActionRunner(testDbRef)

  private var currentInstant: Instant = Instant.ofEpochMilli(0)
  private val clock: Clock            = clockForInstant(() => currentInstant)
  private val processRepository       = TestFactory.newFetchingProcessRepository(testDbRef)
  private val writeProcessRepository  = TestFactory.newWriteProcessRepository(testDbRef)
  private val actionRepository =
    new DbProcessActionRepository(testDbRef, ProcessingTypeDataProvider.withEmptyCombinedData(Map.empty))

  private val expectedRefreshAfterSuccess = List(DataToRefresh.versions, DataToRefresh.activity, DataToRefresh.state)
  private val expectedRefreshAfterFail    = List(DataToRefresh.state)

  test("Should return only events for user in given time") {
    val processName       = ProcessName("fooProcess")
    val id                = saveSampleProcess(processName)
    val processIdWithName = ProcessIdWithName(id, processName)

    val deploymentManager                        = mock[DeploymentManager]
    val (deploymentService, notificationService) = createServices(deploymentManager)

    def notificationsFor(user: LoggedUser): List[Notification] =
      notificationService.notifications(user, global).futureValue

    def deployProcess(
        givenDeployResult: Try[Option[ExternalDeploymentId]],
        user: LoggedUser
    ): Option[ExternalDeploymentId] = {
      when(
        deploymentManager.processCommand(any[RunDeploymentCommand])
      ).thenReturn(Future.fromTry(givenDeployResult))
      when(deploymentManager.processStateDefinitionManager).thenReturn(SimpleProcessStateDefinitionManager)
      when(deploymentManager.customActionsDefinitions).thenReturn(Nil)
      deploymentService.deployProcessAsync(processIdWithName, None, None)(user, global).flatten.futureValue
    }

    val userForSuccess = TestFactory.adminUser("successUser", "successUser")
    val userForFail    = TestFactory.adminUser("failUser", "failUser")

    deployProcess(Success(None), userForSuccess)
    notificationsFor(userForSuccess).map(_.toRefresh) shouldBe List(expectedRefreshAfterSuccess)

    an[Exception] shouldBe thrownBy {
      deployProcess(Failure(new RuntimeException("Failure")), userForFail)
    }

    notificationsFor(userForFail).map(_.toRefresh) shouldBe List(expectedRefreshAfterFail)

    notificationsFor(userForFail).map(_.toRefresh) shouldBe List(
      expectedRefreshAfterFail
    )

    currentInstant = currentInstant.plus(1, ChronoUnit.HOURS)
    notificationsFor(userForFail).map(_.toRefresh) shouldBe Symbol("empty")
  }

  test("should refresh after action execution finished") {
    val processName       = ProcessName("process-execution-finished")
    val id                = saveSampleProcess(processName)
    val processIdWithName = ProcessIdWithName(id, processName)

    val deploymentManager                        = mock[DeploymentManager]
    val (deploymentService, notificationService) = createServices(deploymentManager)

    var passedDeploymentId = Option.empty[DeploymentId]
    def deployProcess(
        givenDeployResult: Try[Option[ExternalDeploymentId]],
        user: LoggedUser
    ): Option[ExternalDeploymentId] = {
      when(
        deploymentManager.processCommand(any[RunDeploymentCommand])
      ).thenAnswer { invocation =>
        passedDeploymentId = Some(invocation.getArgument[RunDeploymentCommand](0).deploymentData.deploymentId)
        Future.fromTry(givenDeployResult)
      }
      when(deploymentManager.processStateDefinitionManager).thenReturn(SimpleProcessStateDefinitionManager)
      when(deploymentManager.customActionsDefinitions).thenReturn(Nil)
      deploymentService.deployProcessAsync(processIdWithName, None, None)(user, global).flatten.futureValue
    }

    val user = TestFactory.adminUser("fooUser", "fooUser")
    deployProcess(Success(None), user)
    val notificationsAfterDeploy = notificationService.notifications(user, global).futureValue
    notificationsAfterDeploy should have length 1
    val deployNotificationId = notificationsAfterDeploy.head.id

    deploymentService
      .markActionExecutionFinished(passedDeploymentId.value.toActionIdOpt.value)
      .futureValue
    val notificationAfterExecutionFinished = notificationService.notifications(user, global).futureValue
    // old notification about deployment is replaced by notification about deployment execution finished which has other id
    notificationAfterExecutionFinished should have length 1
    notificationAfterExecutionFinished.head.id should not equal deployNotificationId
  }

  private val notDeployed =
    SimpleProcessStateDefinitionManager.processState(StatusDetails(SimpleStateStatus.NotDeployed, None))

  private def createServices(deploymentManager: DeploymentManager) = {
    when(
      deploymentManager.getProcessState(any[ProcessIdWithName], any[Option[ProcessAction]])(any[DataFreshnessPolicy])
    )
      .thenReturn(Future.successful(WithDataFreshnessStatus.fresh(notDeployed)))
    val managerDispatcher = mock[DeploymentManagerDispatcher]
    when(managerDispatcher.deploymentManager(any[String])(any[LoggedUser])).thenReturn(Some(deploymentManager))
    when(managerDispatcher.deploymentManagerUnsafe(any[String])(any[LoggedUser])).thenReturn(deploymentManager)
    val config              = NotificationConfig(20 minutes)
    val notificationService = new NotificationServiceImpl(actionRepository, dbioRunner, config, clock)
    val deploymentService = new DeploymentServiceImpl(
      managerDispatcher,
      processRepository,
      actionRepository,
      dbioRunner,
      mock[ProcessingTypeDataProvider[UIProcessValidator, _]],
      mock[ProcessingTypeDataProvider[ScenarioResolver, _]],
      mock[ProcessChangeListener],
      None,
      None,
      clock
    ) {
      override protected def validateBeforeDeploy(
          processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
          deployedScenarioData: DeployedScenarioData
      )(implicit user: LoggedUser, ec: ExecutionContext): Future[Unit] = Future.successful(())

      override protected def prepareDeployedScenarioData(
          processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
          actionId: ProcessActionId,
          additionalDeploymentData: Map[String, String] = Map.empty
      )(implicit user: LoggedUser, ec: ExecutionContext): Future[DeployedScenarioData] = {
        Future.successful(
          DeployedScenarioData(
            processDetails.toEngineProcessVersion,
            prepareDeploymentData(user.toManagerUser, DeploymentId.fromActionId(actionId), additionalDeploymentData),
            processDetails.json
          )
        )
      }
    }
    (deploymentService, notificationService)
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
        processingType = "Streaming",
        isFragment = false,
        forwardedUserName = None
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
