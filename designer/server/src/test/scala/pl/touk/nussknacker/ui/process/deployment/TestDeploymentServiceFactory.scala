package pl.touk.nussknacker.ui.process.deployment

import db.util.DBIOActionInstances.DB
import org.apache.pekko.actor.ActorSystem
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, JobsRecoverySettings, ModelData}
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.api.db.DbRef
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestProcessingType.Streaming1
import pl.touk.nussknacker.test.mock.{StubModelDataWithModelDefinition, TestProcessChangeListener}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory, TestProcessingTypeDataProviderFactory}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.modelDefinition
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.limits.{GlobalLimitsConfig, LimitsService}
import pl.touk.nussknacker.ui.process.deployment.TestDeploymentServiceFactory.{
  actorSystem,
  clock,
  executionContextWithIORuntime
}
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.reconciliation.ScenarioDeploymentReconciler
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.{
  ScenarioStatusProvider => OldApproachScenarioStatusProvider
}
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.newdeployment.{ScenarioStatusProvider => NewApproachScenarioStatusProvider}
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, ScenarioActionRepository}
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import sttp.client3.testing.SttpBackendStub

import java.time.Clock
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class TestDeploymentServiceFactory(dbRef: DbRef) {

  private val dbioRunner                                                    = newDBIOActionRunner(dbRef)
  val fetchingScenarioDBIORepository: DBFetchingProcessRepository[DB]       = newFetchingProcessRepository(dbRef)
  val fetchingScenarioFutureRepository: DBFetchingProcessRepository[Future] = newFutureFetchingScenarioRepository(dbRef)
  val activityRepository: ScenarioActivityRepository = newScenarioActivityRepository(dbRef, clock)
  val actionRepository: ScenarioActionRepository     = newActionProcessRepository(dbRef)
  val listener                                       = new TestProcessChangeListener

  val deploymentManagerDependencies: DeploymentManagerDependencies = new DeploymentManagerDependencies(
    executionContextWithIORuntime,
    executionContextWithIORuntime.ioRuntime,
    actorSystem,
    SttpBackendStub.asynchronousFuture
  )

  def create(
      deploymentManagers: Map[ProcessingType, DeploymentManager],
      modelData: ModelData = new StubModelDataWithModelDefinition(modelDefinition()),
      scenarioStateTimeout: Option[FiniteDuration] = None,
      deploymentCommentSettings: Option[DeploymentCommentSettings] = None,
      globalLimitsConfig: GlobalLimitsConfig = GlobalLimitsConfig.default,
      processingTypeLimits: LimitsConfig = LimitsConfig.default,
  ): TestDeploymentServiceServices = {

    val deploymentManagerProvider = TestProcessingTypeDataProviderFactory.createWithEmptyCombinedData(
      deploymentManagers
        .map { case (processingType, deploymentManager) =>
          processingType -> ValueWithRestriction.anyUser(deploymentManager)
        }
    )

    val dmDispatcher = {
      val futureFetchingProcessRepository = newFutureFetchingScenarioRepository(dbRef)
      new DeploymentManagerDispatcher(deploymentManagerProvider, futureFetchingProcessRepository)
    }

    val deploymentsStatusesProvider = new EngineSideDeploymentStatusesProvider(dmDispatcher, scenarioStateTimeout)
    val oldApproachScenarioStatusProvider = new OldApproachScenarioStatusProvider(
      deploymentsStatusesProvider,
      dmDispatcher,
      fetchingScenarioDBIORepository,
      actionRepository,
      dbioRunner
    )

    val newApproachScenarioStatusProvider = new NewApproachScenarioStatusProvider(
      deploymentManagerProvider,
      TestFactory.newDeploymentRepository(dbRef, clock),
      dbioRunner
    )

    val processService = newProcessService(
      dbRef,
      clock,
      oldApproachScenarioStatusProvider,
      dmDispatcher
    )

    val actionService = new ActionService(
      fetchingScenarioDBIORepository,
      actionRepository,
      dbioRunner,
      listener,
      oldApproachScenarioStatusProvider,
      deploymentCommentSettings,
      clock,
      processService,
      scenarioParametersServiceProvider(List(Streaming1.stringify))
    )

    val deploymentsReconciler =
      new ScenarioDeploymentReconciler(
        TestProcessingTypeDataProviderFactory.createWithEmptyCombinedData(
          deploymentManagers
            .map { case (processingType, deploymentManager) =>
              processingType -> ValueWithRestriction.anyUser(
                new ScenarioDeploymentReconciler.ProcessingTypeServicesDeps(
                  deploymentManager,
                  EngineSetupName("mock"),
                  JobsRecoverySettings.noRecovery,
                  TestFactory.scenarioResolver(processingType)
                )
              )
            }
        ),
        deploymentsStatusesProvider,
        actionRepository,
        fetchingScenarioFutureRepository,
        dbioRunner
      )

    val fragmentResolver = new FragmentResolver(newFragmentRepository(dbRef))
    val deploymentService = new DeploymentService(
      dispatcher = dmDispatcher,
      processValidator = TestFactory.mapProcessingTypeDataProvider(
        deploymentManagers.map { case (processingType, _) =>
          processingType -> ProcessTestData.testProcessValidator(
            validator = ProcessValidator.default(modelData),
            declaredOutputs = modelData.modelDefinition.declaredOutputs,
            fragmentResolver = fragmentResolver,
            processingType = processingType
          )
        }.toList: _*
      ),
      scenarioResolver = TestFactory.mapProcessingTypeDataProvider(
        deploymentManagers.map { case (processingType, _) =>
          processingType -> new ScenarioResolver(fragmentResolver, processingType)
        }.toList: _*
      ),
      actionService = actionService,
      additionalComponentConfigs = additionalComponentConfigsByProcessingType,
      limitsService = new LimitsService(
        globalLimitsConfig = globalLimitsConfig,
        perProcessingTypesLimitsProvider = TestFactory.mapProcessingTypeDataProvider(
          deploymentManagers.map { case (processingType, _) => processingType -> processingTypeLimits }.toList: _*
        ),
        oldDeploymentsApproachScenarioStatusProvider = oldApproachScenarioStatusProvider,
        newDeploymentsApproachScenarioStatusProvider = newApproachScenarioStatusProvider,
      ),
      processingTypeToActionInfoService = TestFactory.mapProcessingTypeDataProvider(
        deploymentManagers.map { case (processingType, _) =>
          processingType -> newActionInfoService(modelData)
        }.toList: _*
      ),
      TestFactory.newLiveDataRepository(dbRef),
      dbioRunner,
    )
    TestDeploymentServiceServices(
      oldApproachScenarioStatusProvider,
      actionService,
      deploymentService,
      deploymentsReconciler
    )
  }

}

case class TestDeploymentServiceServices(
    scenarioStatusProvider: OldApproachScenarioStatusProvider,
    actionService: ActionService,
    deploymentService: DeploymentService,
    scenarioDeploymentReconciler: ScenarioDeploymentReconciler
)

object TestDeploymentServiceFactory {

  implicit val actorSystem: ActorSystem = ActorSystem("TestDeploymentServiceFactory")
  implicit val executionContextWithIORuntime: ExecutionContextWithIORuntimeAdapter =
    ExecutionContextWithIORuntimeAdapter.unsafeCreateFrom(actorSystem.dispatcher)

  val clock: Clock = Clock.systemUTC()

}
