package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorSystem
import cats.effect.unsafe.IORuntime
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.DeploymentManagerDependencies
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentManager,
  ProcessingTypeActionServiceStub,
  ProcessingTypeDeployedScenariosProviderStub
}
import pl.touk.nussknacker.test.mock.TestProcessChangeListener
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.deployment.TestDeploymentServiceFactory.{actorSystem, clock, ec, processingType}
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, DbioRepository, ScenarioActionRepository}
import sttp.client3.testing.SttpBackendStub

import java.time.Clock
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class TestDeploymentServiceFactory(dbRef: DbRef) {

  private val dbioRunner                                         = newDBIOActionRunner(dbRef)
  val fetchingProcessRepository: DBFetchingProcessRepository[DB] = newFetchingProcessRepository(dbRef)
  val activityRepository: ScenarioActivityRepository             = newScenarioActivityRepository(dbRef, clock)
  val actionRepository: ScenarioActionRepository                 = newActionProcessRepository(dbRef)
  val listener                                                   = new TestProcessChangeListener

  val deploymentManagerDependencies: DeploymentManagerDependencies = DeploymentManagerDependencies(
    new ProcessingTypeDeployedScenariosProviderStub(List.empty),
    new ProcessingTypeActionServiceStub,
    new RepositoryBasedScenarioActivityManager(activityRepository, dbioRunner),
    ec,
    IORuntime.global,
    actorSystem,
    SttpBackendStub.asynchronousFuture
  )

  def create(
      deploymentManager: DeploymentManager,
      scenarioStateTimeout: Option[FiniteDuration] = None,
      deploymentCommentSettings: Option[DeploymentCommentSettings] = None
  ): TestDeploymentServiceServices = {
    val processingTypeDataProvider = ProcessingTypeDataProvider.withEmptyCombinedData(
      Map(processingType -> ValueWithRestriction.anyUser(deploymentManager))
    )

    val dmDispatcher = {
      val futureFetchingProcessRepository = newFutureFetchingScenarioRepository(dbRef)
      new DeploymentManagerDispatcher(processingTypeDataProvider, futureFetchingProcessRepository)
    }

    val scenarioStatusProvider = {
      val deploymentsStatusesProvider =
        new EngineSideDeploymentStatusesProvider(dmDispatcher, scenarioStateTimeout)
      new ScenarioStatusProvider(
        deploymentsStatusesProvider,
        dmDispatcher,
        fetchingProcessRepository,
        actionRepository,
        dbioRunner
      )
    }

    val actionService = {
      new ActionService(
        fetchingProcessRepository,
        actionRepository,
        dbioRunner,
        listener,
        scenarioStatusProvider,
        deploymentCommentSettings,
        modelInfoProvider,
        clock
      )
    }

    val deploymentService = new DeploymentService(
      dmDispatcher,
      processValidatorByProcessingType,
      TestFactory.scenarioResolverByProcessingType,
      actionService,
      additionalComponentConfigsByProcessingType,
    )
    TestDeploymentServiceServices(scenarioStatusProvider, actionService, deploymentService)
  }

}

case class TestDeploymentServiceServices(
    scenarioStatusProvider: ScenarioStatusProvider,
    actionService: ActionService,
    deploymentService: DeploymentService,
)

object TestDeploymentServiceFactory {

  implicit val actorSystem: ActorSystem = ActorSystem("TestDeploymentServiceFactory")
  implicit val ec: ExecutionContext     = actorSystem.dispatcher
  val clock: Clock                      = Clock.systemUTC()

  val processingType = "streaming"

}
