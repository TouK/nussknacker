package pl.touk.nussknacker.ui.process.deployment

import cats.effect.unsafe.IORuntime
import db.util.DBIOActionInstances.DB
import org.apache.pekko.actor.ActorSystem
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, JobsRecoveryOptions, ModelData}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeployedScenariosProviderStub}
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.{StubModelDataWithModelDefinition, TestProcessChangeListener}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory, TestProcessingTypeDataProviderFactory}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.modelDefinition
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.deployment.TestDeploymentServiceFactory.{actorSystem, clock, ec, processingType}
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.reconciliation.ScenarioDeploymentReconciler
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, ScenarioActionRepository}
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import sttp.client3.testing.SttpBackendStub

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class TestDeploymentServiceFactory(dbRef: DbRef) {

  private val dbioRunner                                                    = newDBIOActionRunner(dbRef)
  val fetchingScenarioDBIORepository: DBFetchingProcessRepository[DB]       = newFetchingProcessRepository(dbRef)
  val fetchingScenarioFutureRepository: DBFetchingProcessRepository[Future] = newFutureFetchingScenarioRepository(dbRef)
  val activityRepository: ScenarioActivityRepository = newScenarioActivityRepository(dbRef, clock)
  val actionRepository: ScenarioActionRepository     = newActionProcessRepository(dbRef)
  val listener                                       = new TestProcessChangeListener

  val deploymentManagerDependencies: DeploymentManagerDependencies = new DeploymentManagerDependencies(
    new ProcessingTypeDeployedScenariosProviderStub(List.empty),
    ec,
    IORuntime.global,
    actorSystem,
    SttpBackendStub.asynchronousFuture
  )

  def create(
      deploymentManager: DeploymentManager,
      modelData: ModelData = new StubModelDataWithModelDefinition(modelDefinition()),
      scenarioStateTimeout: Option[FiniteDuration] = None,
      deploymentCommentSettings: Option[DeploymentCommentSettings] = None
  ): TestDeploymentServiceServices = {
    val processingTypeDataProvider = TestProcessingTypeDataProviderFactory.createWithEmptyCombinedData(
      Map(processingType.stringify -> ValueWithRestriction.anyUser(deploymentManager))
    )

    val dmDispatcher = {
      val futureFetchingProcessRepository = newFutureFetchingScenarioRepository(dbRef)
      new DeploymentManagerDispatcher(processingTypeDataProvider, futureFetchingProcessRepository)
    }

    val deploymentsStatusesProvider =
      new EngineSideDeploymentStatusesProvider(dmDispatcher, scenarioStateTimeout)

    val scenarioStatusProvider = {
      new ScenarioStatusProvider(
        deploymentsStatusesProvider,
        dmDispatcher,
        fetchingScenarioDBIORepository,
        actionRepository,
        dbioRunner
      )
    }

    val actionService = {
      new ActionService(
        fetchingScenarioDBIORepository,
        actionRepository,
        dbioRunner,
        listener,
        scenarioStatusProvider,
        deploymentCommentSettings,
        clock
      )
    }

    val deploymentsReconciler =
      new ScenarioDeploymentReconciler(
        TestProcessingTypeDataProviderFactory.createWithEmptyCombinedData(
          Map(
            processingType.stringify -> ValueWithRestriction.anyUser(
              new ScenarioDeploymentReconciler.ProcessingTypeServicesDeps(
                deploymentManager,
                JobsRecoveryOptions.noRecovery,
                TestFactory.scenarioResolver
              )
            )
          )
        ),
        deploymentsStatusesProvider,
        actionRepository,
        fetchingScenarioFutureRepository,
        dbioRunner
      )

    val deploymentService = new DeploymentService(
      dmDispatcher,
      TestFactory.mapProcessingTypeDataProvider(
        Streaming.stringify -> ProcessTestData.testProcessValidator(validator = ProcessValidator.default(modelData))
      ),
      TestFactory.scenarioResolverByProcessingType,
      actionService,
      additionalComponentConfigsByProcessingType,
    )
    TestDeploymentServiceServices(scenarioStatusProvider, actionService, deploymentService, deploymentsReconciler)
  }

}

case class TestDeploymentServiceServices(
    scenarioStatusProvider: ScenarioStatusProvider,
    actionService: ActionService,
    deploymentService: DeploymentService,
    scenarioDeploymentReconciler: ScenarioDeploymentReconciler
)

object TestDeploymentServiceFactory {

  implicit val actorSystem: ActorSystem = ActorSystem("TestDeploymentServiceFactory")
  implicit val ec: ExecutionContext     = actorSystem.dispatcher
  val clock: Clock                      = Clock.systemUTC()

  val processingType: TestProcessingType = TestProcessingType.Streaming

}
