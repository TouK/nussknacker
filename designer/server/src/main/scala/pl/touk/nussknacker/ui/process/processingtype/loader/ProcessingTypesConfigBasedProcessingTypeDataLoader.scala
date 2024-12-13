package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.NussknackerConfig
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataLoader.toValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataState
import pl.touk.nussknacker.engine.util.UrlUtils._

import java.nio.file.Path
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class ProcessingTypesConfigBasedProcessingTypeDataLoader(config: NussknackerConfig)
    extends ProcessingTypeDataLoader
    with LazyLogging {

  override def loadProcessingTypeData(
      getModelDependencies: ProcessingType => ModelDependencies,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
  ): IO[ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]] = {
    for {
      managersDir           <- config.managersDir()
      processingTypesConfig <- config.loadProcessingTypeConfigs()
    } yield {
      // This step with splitting DeploymentManagerProvider loading for all processing types
      // and after that creating ProcessingTypeData is done because of the deduplication of deployments
      // See DeploymentManagerProvider.engineSetupIdentity
      val providerWithNameInputData = processingTypesConfig.mapValuesNow { processingTypeConfig =>
        val provider = createDeploymentManagerProvider(processingTypeConfig, managersDir)
        val nameInputData = EngineNameInputData(
          provider.defaultEngineSetupName,
          provider.engineSetupIdentity(processingTypeConfig.deploymentConfig),
          processingTypeConfig.engineSetupName
        )
        (processingTypeConfig, provider, nameInputData)
      }
      val engineSetupNames =
        ScenarioParametersDeterminer.determineEngineSetupNames(providerWithNameInputData.mapValuesNow(_._3))
      val processingTypesData = providerWithNameInputData
        .map { case (processingType, (processingTypeConfig, deploymentManagerProvider, _)) =>
          logger.debug(s"Creating Processing Type: $processingType with config: $processingTypeConfig")
          val modelDependencies = getModelDependencies(processingType)
          val processingTypeData = ProcessingTypeData.createProcessingTypeData(
            processingType,
            ModelData(processingTypeConfig, modelDependencies),
            deploymentManagerProvider,
            getDeploymentManagerDependencies(processingType),
            engineSetupNames(processingType),
            processingTypeConfig.deploymentConfig,
            processingTypeConfig.category,
            modelDependencies.componentDefinitionExtractionMode
          )
          processingType -> processingTypeData
        }

      // Here all processing types are loaded and we are ready to perform additional configuration validations
      // to assert the loaded configuration is correct (fail-fast approach).
      val combinedData = CombinedProcessingTypeData.create(processingTypesData)

      ProcessingTypeDataState(
        processingTypesData.mapValuesNow(toValueWithRestriction),
        () => combinedData,
        // We pass here new Object to enforce update of observers
        new Object
      )
    }
  }

  private def createDeploymentManagerProvider(
      typeConfig: ProcessingTypeConfig,
      managersDir: Path,
  ): DeploymentManagerProvider = {
    val managersClassLoader =
      new URLClassLoader(managersDir.toUri.toURL.expandFiles(".jar"), this.getClass.getClassLoader)
    ScalaServiceLoader.loadNamed[DeploymentManagerProvider](
      typeConfig.deploymentManagerType,
      managersClassLoader
    ) // todo: close
  }

}
