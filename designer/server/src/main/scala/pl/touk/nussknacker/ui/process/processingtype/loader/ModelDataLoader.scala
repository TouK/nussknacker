package pl.touk.nussknacker.ui.process.processingtype.loader

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ModelData, ModelDependencies, ProcessingTypeConfig}
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
    validateReloadConsistency(processingTypeConfigs, getModelDependencies, modelClassLoaderProvider)
    val modelDataByProcessingType = processingTypeConfigs.configByProcessingType.map {
      case (processingType, processingTypeConfig) =>
        val modelDataWithInput: ModelDataWithProcessingTypeDataInput =
          loadModelDataWithInput(getModelDependencies, modelClassLoaderProvider, processingType, processingTypeConfig)
        processingType -> ValueWithRestriction.userWithAccessRightsToAnyOfCategories(
          modelDataWithInput,
          Set(processingTypeConfig.category)
        )
    }
    ProcessingTypeDataState.withUninitializedCombinedData(modelDataByProcessingType)
  }

  private def validateReloadConsistency(
      processingTypeConfigs: ProcessingTypeConfigs,
      getModelDependencies: ProcessingType => ModelDependencies,
      modelClassLoaderProvider: ModelClassLoaderProvider
  ): Unit = {
    modelClassLoaderProvider.validateReloadConsistency(processingTypeConfigs.configByProcessingType.map {
      case (processingType, processingTypeConfig) =>
        processingType -> ModelClassLoaderDependencies(
          classpath = processingTypeConfig.classPath,
          workingDirectoryOpt = getModelDependencies(processingType).workingDirectoryOpt
        )
    })
  }

  private def loadModelDataWithInput(
      getModelDependencies: ProcessingType => ModelDependencies,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      processingType: ProcessingType,
      processingTypeConfig: ProcessingTypeConfig
  ): ModelDataWithProcessingTypeDataInput = {
    val modelConfig = processingTypeConfig.modelConfig
    logger.debug(
      s"Creating Model for processingType: $processingType with config: $modelConfig and category: ${processingTypeConfig.category}"
    )
    val modelDependencies = getModelDependencies(processingType)
    val modelClassLoader  = modelClassLoaderProvider.forProcessingTypeUnsafe(processingType)
    val modelData         = ModelData(processingTypeConfig, modelDependencies, modelClassLoader)
    val modelDataWithInput = new ModelDataWithProcessingTypeDataInput(
      modelData,
      processingTypeConfig.category,
      processingTypeConfig.activeScenariosLimit,
      modelDependencies.componentDefinitionExtractionMode
    )
    modelDataWithInput
  }

}
