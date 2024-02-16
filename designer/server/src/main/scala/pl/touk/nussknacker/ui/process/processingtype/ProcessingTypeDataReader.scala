package pl.touk.nussknacker.ui.process.processingtype

import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataReader.{
  selectedScenarioTypeConfigurationPath,
  toValueWithRestriction
}

object ProcessingTypeDataReader extends ProcessingTypeDataReader {

  val selectedScenarioTypeConfigurationPath = "selectedScenarioType"

  def toValueWithRestriction(processingTypeData: ProcessingTypeData): ValueWithRestriction[ProcessingTypeData] = {
    ValueWithRestriction.userWithAccessRightsToAnyOfCategories(processingTypeData, Set(processingTypeData.category))
  }

}

trait ProcessingTypeDataReader extends LazyLogging {

  def loadProcessingTypeData(
      config: ConfigWithUnresolvedVersion,
      getModelDependencies: ProcessingType => ModelDependencies,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] = {
    val processingTypesConfig      = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(config)
    val selectedScenarioTypeFilter = createSelectedScenarioTypeFilter(config) tupled
    val filteredProcessingTypes    = processingTypesConfig.filter(selectedScenarioTypeFilter)
    // This step with splitting DeploymentManagerProvider loading for all processing types
    // and after that creating ProcessingTypeData is done because of the deduplication of deployments
    // See DeploymentManagerProvider.engineSetupIdentity
    val providerWithNameInputData = filteredProcessingTypes.mapValuesNow { processingTypeConfig =>
      val provider = createDeploymentManagerProvider(processingTypeConfig)
      val nameInputData = EngineNameInputData(
        provider.defaultEngineSetupName,
        provider.engineSetupIdentity(processingTypeConfig.deploymentConfig),
        None // FIXME (next PRs) add deployment config and tests for that
      )
      (processingTypeConfig, provider, nameInputData)
    }
    val engineSetupNames =
      ScenarioParametersDeterminer.determineEngineSetupNames(providerWithNameInputData.mapValuesNow(_._3))
    val processingTypesData = providerWithNameInputData
      .map { case (processingType, (processingTypeConfig, deploymentManagerProvider, _)) =>
        logger.debug(s"Creating Processing Type: $processingType with config: $processingTypeConfig")
        val processingTypeData = createProcessingTypeData(
          processingType,
          processingTypeConfig,
          getModelDependencies(processingType),
          deploymentManagerProvider,
          getDeploymentManagerDependencies(processingType),
          engineSetupNames(processingType),
        )
        processingType -> processingTypeData
      }

    // Here all processing types are loaded and we are ready to perform additional configuration validations
    // to assert the loaded configuration is correct (fail-fast approach).
    val combinedData = createCombinedData(processingTypesData)

    ProcessingTypeDataState(
      processingTypesData.mapValuesNow(toValueWithRestriction),
      () => combinedData,
      // We pass here new Object to enforce update of observers
      new Object
    )
  }

  // TODO Replace selectedScenarioType property by mechanism allowing to configure multiple scenario types with
  //      different processing mode and engine configurations. This mechanism should also allow to have some scenario types
  //      configurations that are invalid (e.g. some mandatory field is not configured)
  private def createSelectedScenarioTypeFilter(
      config: ConfigWithUnresolvedVersion
  ): (ProcessingType, ProcessingTypeConfig) => Boolean = {
    val selectedScenarioTypeOpt = config.resolved.getAs[String](selectedScenarioTypeConfigurationPath)
    (processingType, _) => selectedScenarioTypeOpt.forall(_ == processingType)
  }

  protected def createDeploymentManagerProvider(typeConfig: ProcessingTypeConfig): DeploymentManagerProvider = {
    ScalaServiceLoader.loadNamed[DeploymentManagerProvider](typeConfig.deploymentManagerType)
  }

  protected def createProcessingTypeData(
      processingType: ProcessingType,
      processingTypeConfig: ProcessingTypeConfig,
      modelDependencies: ModelDependencies,
      deploymentManagerProvider: DeploymentManagerProvider,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      engineSetupName: EngineSetupName
  ): ProcessingTypeData = {
    ProcessingTypeData.createProcessingTypeData(
      processingType,
      ModelData(processingTypeConfig, modelDependencies),
      deploymentManagerProvider,
      deploymentManagerDependencies,
      engineSetupName,
      processingTypeConfig.deploymentConfig,
      processingTypeConfig.category
    )
  }

  protected def createCombinedData(
      valueMap: Map[ProcessingType, ProcessingTypeData],
  ): CombinedProcessingTypeData = {
    CombinedProcessingTypeData.create(valueMap)
  }

}
