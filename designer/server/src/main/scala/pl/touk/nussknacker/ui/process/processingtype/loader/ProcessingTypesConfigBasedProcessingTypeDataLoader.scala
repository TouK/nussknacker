package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.ui.configloader.{ProcessingTypeConfigs, ProcessingTypeConfigsLoader}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData.SchedulingForProcessingType
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataLoader.toValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataState

import scala.util.Success

class ProcessingTypesConfigBasedProcessingTypeDataLoader(
    processingTypeConfigsLoader: ProcessingTypeConfigsLoader,
    deploymentManagersClassLoader: DeploymentManagersClassLoader
) extends ProcessingTypeDataLoader
    with LazyLogging {

  override def loadProcessingTypeData(
      getModelDependencies: ProcessingType => ModelDependencies,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      dbRef: Option[DbRef],
  ): IO[ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]] = {
    processingTypeConfigsLoader
      .loadProcessingTypeConfigs()
      .map(
        createProcessingTypeData(
          _,
          getModelDependencies,
          getDeploymentManagerDependencies,
          modelClassLoaderProvider,
          dbRef
        )
      )
  }

  private def createProcessingTypeData(
      processingTypesConfig: ProcessingTypeConfigs,
      getModelDependencies: ProcessingType => ModelDependencies,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      dbRef: Option[DbRef],
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] = {
    // This step with splitting DeploymentManagerProvider loading for all processing types
    // and after that creating ProcessingTypeData is done because of the deduplication of deployments
    // See DeploymentManagerProvider.engineSetupIdentity
    val providerWithNameInputData = processingTypesConfig.configByProcessingType.mapValuesNow { processingTypeConfig =>
      val provider = createDeploymentManagerProvider(processingTypeConfig)
      val nameInputData = EngineNameInputData(
        provider.defaultEngineSetupName,
        provider.engineSetupIdentity(processingTypeConfig.deploymentConfig),
        processingTypeConfig.engineSetupName
      )
      (processingTypeConfig, provider, nameInputData)
    }
    modelClassLoaderProvider.validateReloadConsistency(providerWithNameInputData.map { case (processingType, data) =>
      processingType -> ModelClassLoaderDependencies(
        classpath = data._1.classPath,
        workingDirectoryOpt = getModelDependencies(processingType).workingDirectoryOpt
      )
    })

    val engineSetupNames =
      ScenarioParametersDeterminer.determineEngineSetupNames(providerWithNameInputData.mapValuesNow(_._3))
    val processingTypesData = providerWithNameInputData
      .map { case (processingType, (processingTypeConfig, deploymentManagerProvider, _)) =>
        logger.debug(s"Creating Processing Type: $processingType with config: $processingTypeConfig")
        val schedulingForProcessingType =
          if (processingTypeConfig.deploymentConfig.hasPath("scheduling") &&
            processingTypeConfig.deploymentConfig.getBoolean("scheduling.enabled")) {
            SchedulingForProcessingType.Available(
              dbRef.getOrElse(
                throw new RuntimeException(s"dbRef not present, but required for Dm with scheduling enabled")
              ),
            )
          } else {
            SchedulingForProcessingType.NotAvailable
          }

        val modelDependencies = getModelDependencies(processingType)
        val modelClassLoader  = modelClassLoaderProvider.forProcessingTypeUnsafe(processingType)
        val processingTypeData = ProcessingTypeData.createProcessingTypeData(
          processingType,
          ModelData(processingTypeConfig, modelDependencies, modelClassLoader),
          deploymentManagerProvider,
          schedulingForProcessingType,
          getDeploymentManagerDependencies(processingType),
          engineSetupNames(processingType),
          processingTypeConfig.deploymentConfig,
          processingTypeConfig.category,
          modelDependencies.componentDefinitionExtractionMode,
        )
        processingType -> processingTypeData
      }

    // Here all processing types are loaded and we are ready to perform additional configuration validations
    // to assert the loaded configuration is correct (fail-fast approach).
    val combinedData = CombinedProcessingTypeData.create(processingTypesData)

    new ProcessingTypeDataState(
      processingTypesData.mapValuesNow(toValueWithRestriction),
      // We want to fail fast - because of that we don't return Try in CombinedProcessingTypeData.create
      Success(combinedData),
      // We pass here new Object to enforce update of observers
      new Object
    )
  }

  private def createDeploymentManagerProvider(typeConfig: ProcessingTypeConfig): DeploymentManagerProvider = {
    ScalaServiceLoader.loadNamed[DeploymentManagerProvider](
      typeConfig.deploymentManagerType,
      deploymentManagersClassLoader
    )
  }

}
