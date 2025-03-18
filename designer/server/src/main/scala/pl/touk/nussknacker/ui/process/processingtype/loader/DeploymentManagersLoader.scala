package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.data.ValidatedNel
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.ProcessingTypeConfig.DeploymentManagerType
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, NoSchedulingSupport, SchedulingSupported}
import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.periodic.PeriodicDeploymentManagerDecorator
import pl.touk.nussknacker.ui.process.processingtype.{
  DeploymentData,
  EngineNameInputData,
  ModelClassLoaderProvider,
  ScenarioParametersDeterminer
}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

import java.net.URLClassLoader

object DeploymentManagersLoader {

  def load(
      processingTypeConfigs: ProcessingTypeConfigs,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      modelDataProviders: ProcessingTypeDataProvider[ModelData, _],
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      dbRef: Option[DbRef],
  ): Resource[IO, Map[ProcessingType, DeploymentData]] = {
    processingTypeConfigs.configByProcessingType.toList
      .map { case (processingType, processingTypeConfig) =>
        val deploymentManagerProvider =
          createDeploymentManagerProvider(deploymentManagersClassLoader, processingTypeConfig)
        val nameInputData = EngineNameInputData(
          deploymentManagerProvider.defaultEngineSetupName,
          deploymentManagerProvider.engineSetupIdentity(processingTypeConfig.deploymentConfig),
          processingTypeConfig.engineSetupName
        )

        val schedulingForProcessingType =
          if (processingTypeConfig.deploymentConfig.hasPath("scheduling") &&
            processingTypeConfig.deploymentConfig.getBoolean("scheduling.enabled")) {
            SchedulingForProcessingType.Available(
              dbRef.getOrElse(
                throw new RuntimeException(
                  s"dbRef not present, but required for Deployment Manager with scheduling enabled"
                )
              )
            )
          } else {
            SchedulingForProcessingType.NotAvailable
          }

        val scenarioStateCacheTTL =
          ScenarioStateCachingConfig.extractScenarioStateCacheTTL(processingTypeConfig.deploymentConfig)

        val deploymentManagerDependencies = getDeploymentManagerDependencies(processingType)
        val validDeploymentManager = for {
          deploymentManager <-
            deploymentManagerProvider.createDeploymentManager(
              new BaseModelDataProvider {
                override val modelClassLoader: URLClassLoader =
                  modelClassLoaderProvider.forProcessingTypeUnsafe(processingType)
                override def getCurrentModelData(): BaseModelData = {
                  // This is a hack, we should split deployment data from model data
                  modelDataProviders.forProcessingTypeUnsafe(processingType)(NussknackerInternalUser.instance)
                }
              },
              deploymentManagerDependencies,
              processingTypeConfig.deploymentConfig,
              scenarioStateCacheTTL
            )
          decoratedDeploymentManager = schedulingForProcessingType match {
            case SchedulingForProcessingType.Available(dbRef) =>
              deploymentManager.schedulingSupport match {
                case supported: SchedulingSupported =>
                  PeriodicDeploymentManagerDecorator.decorate(
                    underlying = deploymentManager,
                    schedulingSupported = supported,
                    deploymentConfig = processingTypeConfig.deploymentConfig,
                    dependencies = deploymentManagerDependencies,
                    dbRef = dbRef,
                  )
                case NoSchedulingSupport =>
                  throw new IllegalStateException(
                    s"DeploymentManager ${deploymentManagerProvider.name} does not support periodic execution"
                  )
              }
            case SchedulingForProcessingType.NotAvailable =>
              deploymentManager
          }
        } yield decoratedDeploymentManager

        validDeploymentManager
          .map(deploymentManager => Resource.fromAutoCloseable(IO.pure(deploymentManager)))
          .sequence
          .map { validDeploymentManager =>
            val metaDataInitializer =
              deploymentManagerProvider.metaDataInitializer(processingTypeConfig.deploymentConfig)
            val schedulingPropertiesConfig = schedulingForProcessingType match {
              case SchedulingForProcessingType.Available(_) =>
                PeriodicDeploymentManagerDecorator.additionalScenarioProperties
              case SchedulingForProcessingType.NotAvailable =>
                Map.empty[String, ScenarioPropertyConfig]
            }
            val deploymentScenarioPropertiesConfig = deploymentManagerProvider.scenarioPropertiesConfig(
              processingTypeConfig.deploymentConfig
            ) ++ schedulingPropertiesConfig
            val additionalValidators =
              deploymentManagerProvider.additionalValidators(processingTypeConfig.deploymentConfig)
            processingType -> DeploymentDataWithEngineNameInput(
              processingTypeConfig.deploymentManagerType,
              validDeploymentManager,
              metaDataInitializer,
              deploymentScenarioPropertiesConfig,
              additionalValidators,
              nameInputData
            )
          }
      }
      .sequence
      .map(_.toMap)
      .map { deploymentDataWithEngineNameInputByProcessingType =>
        // We can't determine engine setup name during creation of each DeploymentManager because of the deduplication
        // of deployments. See DeploymentManagerProvider.engineSetupIdentity
        val engineSetupNames = ScenarioParametersDeterminer.determineEngineSetupNames(
          deploymentDataWithEngineNameInputByProcessingType.mapValuesNow(_.nameInputData)
        )

        deploymentDataWithEngineNameInputByProcessingType.map {
          case (
                processingType,
                DeploymentDataWithEngineNameInput(
                  deploymentManagerType,
                  validDeploymentManager,
                  metaDataInitializer,
                  deploymentScenarioPropertiesConfig,
                  additionalValidators,
                  _
                )
              ) =>
            processingType -> new DeploymentData(
              deploymentManagerType,
              validDeploymentManager,
              metaDataInitializer,
              deploymentScenarioPropertiesConfig,
              additionalValidators,
              engineSetupNames(processingType)
            )
        }
      }
  }

  private def createDeploymentManagerProvider(
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      typeConfig: ProcessingTypeConfig
  ): DeploymentManagerProvider = {
    val loadedProvider = ScalaServiceLoader.loadNamed[DeploymentManagerProvider](
      typeConfig.deploymentManagerType.value,
      deploymentManagersClassLoader
    )
    new DeploymentManagerProviderCorrectClassloaderHandler(loadedProvider, deploymentManagersClassLoader)
  }

  private final case class DeploymentDataWithEngineNameInput(
      deploymentManagerType: DeploymentManagerType,
      validDeploymentManager: ValidatedNel[String, DeploymentManager],
      metaDataInitializer: MetaDataInitializer,
      deploymentScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      additionalValidators: List[CustomProcessValidator],
      nameInputData: EngineNameInputData
  )

  private sealed trait SchedulingForProcessingType

  private object SchedulingForProcessingType {

    case object NotAvailable extends SchedulingForProcessingType

    final case class Available(dbRef: DbRef) extends SchedulingForProcessingType

  }

}
