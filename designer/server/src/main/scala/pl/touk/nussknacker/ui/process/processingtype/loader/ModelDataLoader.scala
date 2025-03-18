package pl.touk.nussknacker.ui.process.processingtype.loader

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ModelData, ModelDependencies}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.process.processingtype.{
  ModelClassLoaderDependencies,
  ModelClassLoaderProvider,
  ValueWithRestriction
}
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataStateFactory.ModelDataWithProcessingTypeDataInput
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataState

object ModelDataLoader extends LazyLogging {

  def load(
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
            processingTypeConfig.category,
            modelDependencies.componentDefinitionExtractionMode
          ),
          Set(processingTypeConfig.category)
        )
    }
    ProcessingTypeDataState.withUninitializedCombinedData(modelDataByProcessingType)
  }

}
