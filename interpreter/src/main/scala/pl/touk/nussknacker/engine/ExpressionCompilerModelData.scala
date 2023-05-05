package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes

case class ExpressionCompilerModelData(modelDefinitionWithTypes: ModelDefinitionWithTypes,
                                       dictRegistry: DictRegistry,
                                       modelClassLoader: () => ClassLoader)

object ExpressionCompilerModelData {

  def apply(modelData: ModelData): ExpressionCompilerModelData = ExpressionCompilerModelData(
    modelData.modelDefinitionWithTypes,
    modelData.dictServices.dictRegistry,
    () => modelData.modelClassLoader.classLoader)

}