package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.data.ValidatedNel
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.ProcessingTypeConfig.DeploymentManagerType
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.classloader.{DeploymentManagersClassLoader, ModelClassLoader}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.process.periodic.{PeriodicDeploymentManagerDecorator, SchedulingDependencies}
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

object DeploymentManagersLoader {

  def load(
      processingTypeConfigs: ProcessingTypeConfigs,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      modelDataProviders: ProcessingTypeDataProvider[ModelData, _],
      deploymentManagerDependencies: DeploymentManagerDependencies,
      schedulingDepsProvider: Option[ProcessingType => SchedulingDependencies],
  ): Resource[IO, Map[ProcessingType, ValueWithRestriction[DeploymentData]]] = {
    processingTypeConfigs.configByProcessingType.toList
      .map { case (processingType, processingTypeConfig) =>
        loadDeploymentDataWithEngineNameInput(
          processingType,
          processingTypeConfig,
          deploymentManagersClassLoader,
          modelClassLoaderProvider,
          modelDataProviders,
          deploymentManagerDependencies,
          schedulingDepsProvider.map(_(processingType)),
        ).map(processingType -> _)
      }
      .sequence
      .map(_.toMap)
      // We can't determine engine setup name during creation of each DeploymentManager because of the deduplication
      // of deployments. See DeploymentManagerProvider.engineSetupIdentity
      .map(toDeploymentDataWithRestriction)
  }

  private def loadDeploymentDataWithEngineNameInput(
      processingType: ProcessingType,
      processingTypeConfig: ProcessingTypeConfig,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      modelDataProviders: ProcessingTypeDataProvider[ModelData, _],
      deploymentManagerDependencies: DeploymentManagerDependencies,
      schedulingDepsOpt: Option[SchedulingDependencies],
  ): Resource[IO, DeploymentDataWithEngineNameInput] = {
    val deploymentManagerProvider =
      createDeploymentManagerProvider(deploymentManagersClassLoader, processingTypeConfig)
    val nameInputData = EngineNameInputData(
      deploymentManagerProvider.defaultEngineSetupName,
      deploymentManagerProvider.engineSetupIdentity(processingTypeConfig.deploymentConfig),
      processingTypeConfig.engineSetupName
    )

    val scenarioStateCacheTTL =
      ScenarioStateCachingConfig.extractScenarioStateCacheTTL(processingTypeConfig.deploymentConfig)

    val validDeploymentManager = for {
      deploymentManager <-
        deploymentManagerProvider.createDeploymentManager(
          new BaseModelDataProvider {
            override val modelClassLoader: ModelClassLoader =
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
      // TODO: separate scheduling from Deployment Managers - it complicates this logic
      (decoratedDeploymentManager, schedulingPropertiesConfig) = PeriodicDeploymentManagerDecorator
        .decorateIfSchedulingSupported(
          deploymentManager,
          processingTypeConfig.deploymentConfig,
          deploymentManagerDependencies,
          schedulingDepsOpt
        )
    } yield (decoratedDeploymentManager, schedulingPropertiesConfig)

    validDeploymentManager
      .map { case (deploymentManager, schedulingPropertiesConfig) =>
        Resource.fromAutoCloseable(IO.pure(deploymentManager)).map((_, schedulingPropertiesConfig))
      }
      .sequence
      .map { validDeploymentManagerAndSchedulingProperties =>
        val validDeploymentManager = validDeploymentManagerAndSchedulingProperties.map(_._1)
        val schedulingProperties   = validDeploymentManagerAndSchedulingProperties.map(_._2).valueOr(Map.empty)
        val metaDataInitializer =
          deploymentManagerProvider.metaDataInitializer(processingTypeConfig.deploymentConfig)
        val deploymentScenarioPropertiesConfig = deploymentManagerProvider.scenarioPropertiesConfig(
          processingTypeConfig.deploymentConfig
        ) ++ schedulingProperties
        val additionalValidators =
          deploymentManagerProvider.additionalValidators(processingTypeConfig.deploymentConfig)
        val jobsRecoverySettings = deploymentManagerProvider.jobsRecoverySettings(processingTypeConfig.deploymentConfig)
        DeploymentDataWithEngineNameInput(
          processingTypeConfig.deploymentManagerType,
          validDeploymentManager,
          metaDataInitializer,
          deploymentScenarioPropertiesConfig,
          additionalValidators,
          jobsRecoverySettings,
          nameInputData,
          processingTypeConfig.category
        )
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

  private def toDeploymentDataWithRestriction(
      deploymentDataWithEngineNameInputByProcessingType: Map[ProcessingType, DeploymentDataWithEngineNameInput]
  ): Map[ProcessingType, ValueWithRestriction[DeploymentData]] = {
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
              jobsRecoverySettings,
              _,
              category
            )
          ) =>
        val deploymentData = new DeploymentData(
          deploymentManagerType = deploymentManagerType,
          validDeploymentManager = validDeploymentManager,
          metaDataInitializer = metaDataInitializer,
          deploymentScenarioPropertiesConfig = deploymentScenarioPropertiesConfig,
          additionalValidators = additionalValidators,
          jobsRecoverySettings = jobsRecoverySettings,
          engineSetupName = engineSetupNames(processingType)
        )
        processingType -> ValueWithRestriction.userWithAccessRightsToAnyOfCategories(deploymentData, Set(category))
    }
  }

  private final case class DeploymentDataWithEngineNameInput(
      deploymentManagerType: DeploymentManagerType,
      validDeploymentManager: ValidatedNel[String, DeploymentManager],
      metaDataInitializer: MetaDataInitializer,
      deploymentScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      additionalValidators: List[CustomProcessValidator],
      jobsRecoverySettings: JobsRecoverySettings,
      nameInputData: EngineNameInputData,
      category: String
  )

}
