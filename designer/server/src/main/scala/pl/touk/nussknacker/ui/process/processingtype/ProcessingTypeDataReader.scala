package pl.touk.nussknacker.ui.process.processingtype

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.process.deployment.{
  AllDeployedScenarioService,
  DefaultProcessingTypeDeploymentService,
  DeploymentService
}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataReader.{
  selectedScenarioTypeConfigurationPath,
  toValueWithPermission
}
import pl.touk.nussknacker.ui.process.processingtype.ValueAccessPermission.UserWithAccessRightsToCategory

import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeDataReader extends ProcessingTypeDataReader {

  val selectedScenarioTypeConfigurationPath = "selectedScenarioType"

  def toValueWithPermission(processingTypeData: ProcessingTypeData): ValueWithPermission[ProcessingTypeData] = {
    val accessPermission = UserWithAccessRightsToCategory(processingTypeData.category)
    ValueWithPermission(processingTypeData, accessPermission)
  }

}

trait ProcessingTypeDataReader extends LazyLogging {

  def loadProcessingTypeData(
      config: ConfigWithUnresolvedVersion,
      deploymentService: DeploymentService,
      createAllDeployedScenarioService: ProcessingType => AllDeployedScenarioService,
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
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
      .map { case (processingType, (typeConfig, provider, _)) =>
        val processingTypeData = createProcessingTypeData(
          processingType,
          typeConfig,
          provider,
          engineSetupNames(processingType),
          deploymentService,
          createAllDeployedScenarioService,
          additionalUIConfigProvider
        )
        processingType -> processingTypeData
      }

    // Here all processing types are loaded and we are ready to perform additional configuration validations
    // to assert the loaded configuration is correct (fail-fast approach).
    val combinedData = createCombinedData(processingTypesData)

    ProcessingTypeDataState(
      processingTypesData.mapValuesNow(toValueWithPermission),
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
      deploymentManagerProvider: DeploymentManagerProvider,
      engineSetupName: EngineSetupName,
      deploymentService: DeploymentService,
      createAllDeployedScenarioService: ProcessingType => AllDeployedScenarioService,
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
  ): ProcessingTypeData = {
    logger.debug(s"Creating Processing Type: $processingType with config: $processingTypeConfig")
    val allDeployedScenarioService = createAllDeployedScenarioService(processingType)
    implicit val processTypeDeploymentService: ProcessingTypeDeploymentService =
      new DefaultProcessingTypeDeploymentService(processingType, deploymentService, allDeployedScenarioService)
    ProcessingTypeData.createProcessingTypeData(
      processingType,
      deploymentManagerProvider,
      engineSetupName,
      processingTypeConfig,
      additionalUIConfigProvider
    )
  }

  protected def createCombinedData(
      valueMap: Map[ProcessingType, ProcessingTypeData],
  ): CombinedProcessingTypeData = {
    CombinedProcessingTypeData.create(valueMap)
  }

}
