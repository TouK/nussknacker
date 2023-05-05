package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition

case class ExpressionCompilerModelData(processWithObjectsDefinition: ProcessDefinition[DefinitionExtractor.ObjectWithMethodDef],
                                       dictRegistry: DictRegistry,
                                       modelClassLoader: () => ClassLoader) {

  lazy val typeDefinitions: Set[TypeInfos.ClazzDefinition] = ProcessDefinitionExtractor.extractTypes(processWithObjectsDefinition)

}
