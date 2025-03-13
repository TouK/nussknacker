package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData.SchedulingForProcessingType
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataState
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

import scala.util.Success

object ProcessingTypeDataLoader extends LazyLogging {

  def loadModelData(
      processingTypeConfigs: ProcessingTypeConfigs,
      getModelDependencies: ProcessingType => ModelDependencies,
      modelClassLoaderProvider: ModelClassLoaderProvider,
  ): ProcessingTypeDataState[ModelDataWithProcessingTypeDataInput, _] = {
    modelClassLoaderProvider.validateReloadConsistency(processingTypeConfigs.configByProcessingType.map {
      case (processingType, processingTypeConfig) =>
        processingType -> ModelClassLoaderDependencies(
          classpath = processingTypeConfig.classPath,
          workingDirectoryOpt = getModelDependencies(processingType).workingDirectoryOpt
        )
    })
    val modelDataByProcessingType = processingTypeConfigs.configByProcessingType.map {
      case (processingType, processingTypeConfig) =>
        val modelConfig = processingTypeConfig.modelConfig
        logger.debug(
          s"Creating Model for processingType: $processingType with config: $modelConfig and category: ${processingTypeConfig.category}"
        )
        val modelDependencies = getModelDependencies(processingType)
        val modelClassLoader  = modelClassLoaderProvider.forProcessingTypeUnsafe(processingType)
        val modelData         = ModelData(processingTypeConfig, modelDependencies, modelClassLoader)
        processingType -> ValueWithRestriction.userWithAccessRightsToAnyOfCategories(
          ModelDataWithProcessingTypeDataInput(
            modelData,
            processingTypeConfig,
            modelDependencies.componentDefinitionExtractionMode
          ),
          Set(processingTypeConfig.category)
        )
    }
    ProcessingTypeDataState.withUninitializedCombinedData(modelDataByProcessingType)
  }

  // FIXME abr DeploymentManager memoization
  def toFinalProcessingTypeData(
      modelDataWithInputByProcessingType: Map[ProcessingType, ValueWithRestriction[
        ModelDataWithProcessingTypeDataInput
      ]],
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      dbRef: Option[DbRef],
  ): Validated[
    ScenarioParametersConfigurationError,
    ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]
  ] = {
    // It is a hack, we skip restriction and then again add these restriction. We do this because:
    // - Model and DeploymentManager are combined into ProcessingTypeData, we should split them eventually
    // - This combining causes that we have to do deduplication and determine CombinedProcessingTypeData based on all ProcessingTypeData
    // - We don't want to reload DeploymentManager in reload API
    val modelDataWithProcessingTypeConfigWithSkippedRestrictionsByProcessingType =
      modelDataWithInputByProcessingType.mapValuesNow(
        _.valueWithAllowedAccess(Permission.Read)(NussknackerInternalUser.instance).get
      )

    // This step with splitting DeploymentManagerProvider loading for all processing types
    // and after that creating ProcessingTypeData is done because of the deduplication of deployments
    // See DeploymentManagerProvider.engineSetupIdentity
    val providerWithNameInputData =
      modelDataWithProcessingTypeConfigWithSkippedRestrictionsByProcessingType.mapValuesNow { modelDataWithInput =>
        val provider =
          createDeploymentManagerProvider(deploymentManagersClassLoader, modelDataWithInput.processingTypeConfig)
        val nameInputData = EngineNameInputData(
          provider.defaultEngineSetupName,
          provider.engineSetupIdentity(modelDataWithInput.processingTypeConfig.deploymentConfig),
          modelDataWithInput.processingTypeConfig.engineSetupName
        )
        (provider, nameInputData, modelDataWithInput)
      }

    val engineSetupNames =
      ScenarioParametersDeterminer.determineEngineSetupNames(providerWithNameInputData.mapValuesNow(_._2))
    val processingTypesData = providerWithNameInputData
      .map { case (processingType, (deploymentManagerProvider, _, modelDataWithInput)) =>
        logger.debug(
          s"Creating Processing Type: $processingType with config: ${modelDataWithInput.processingTypeConfig}"
        )
        val schedulingForProcessingType =
          if (modelDataWithInput.processingTypeConfig.deploymentConfig.hasPath("scheduling") &&
            modelDataWithInput.processingTypeConfig.deploymentConfig.getBoolean("scheduling.enabled")) {
            SchedulingForProcessingType.Available(
              dbRef.getOrElse(
                throw new RuntimeException(s"dbRef not present, but required for Dm with scheduling enabled")
              ),
            )
          } else {
            SchedulingForProcessingType.NotAvailable
          }

        val processingTypeData = ProcessingTypeData.createProcessingTypeData(
          processingType,
          modelDataWithInput.modelData,
          deploymentManagerProvider,
          schedulingForProcessingType,
          getDeploymentManagerDependencies(processingType),
          engineSetupNames(processingType),
          modelDataWithInput.processingTypeConfig.deploymentConfig,
          modelDataWithInput.processingTypeConfig.category,
          modelDataWithInput.componentDefinitionExtractionMode,
        )
        processingType -> processingTypeData
      }

    // Here all processing types are loaded and we are ready to perform additional configuration validations
    // to assert the loaded configuration is correct (fail-fast approach).
    CombinedProcessingTypeData.create(processingTypesData).map { combinedData =>
      new ProcessingTypeDataState(
        processingTypesData.mapValuesNow(toValueWithRestriction),
        // We return Success instead of passing here Try result of CombinedProcessingTypeData.create because
        // we want to break reloading logic instead of creating a new state with failing combined date
        // Try in ProcessingTypeDataState.combinedDataTry is only for initial state purpose when this data are not initialized yet
        Success(combinedData)
      )
    }
  }

  private def createDeploymentManagerProvider(
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      typeConfig: ProcessingTypeConfig
  ): DeploymentManagerProvider = {
    val loadedProvider = ScalaServiceLoader.loadNamed[DeploymentManagerProvider](
      typeConfig.deploymentManagerType,
      deploymentManagersClassLoader
    )
    new DeploymentManagerProviderCorrectClassloaderHandler(loadedProvider, deploymentManagersClassLoader)
  }

  final case class ModelDataWithProcessingTypeDataInput(
      modelData: ModelData,
      processingTypeConfig: ProcessingTypeConfig,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ) extends AutoCloseable {

    override def close(): Unit = {
      modelData.close()
    }

  }

  def toValueWithRestriction(processingTypeData: ProcessingTypeData): ValueWithRestriction[ProcessingTypeData] = {
    ValueWithRestriction.userWithAccessRightsToAnyOfCategories(processingTypeData, Set(processingTypeData.category))
  }

}
