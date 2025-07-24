package pl.touk.nussknacker.engine.process.scenariotesting

import pl.touk.nussknacker.engine.{ModelConfig, RuntimeMode}
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentType,
  DesignerWideComponentId,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.node.ComponentIdExtractor
import pl.touk.nussknacker.engine.process.compiler.{ComponentDefinitionContext, FlinkProcessCompilerDataFactory}

abstract class StubbedFlinkProcessCompilerDataFactory(
    process: CanonicalProcess,
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: ModelConfig,
    runtimeMode: RuntimeMode,
    configsFromProviderWithDictionaryEditor: Map[DesignerWideComponentId, ComponentAdditionalConfig],
    nodesDeploymentData: NodesDeploymentData,
    processListeners: List[ProcessListener],
) extends FlinkProcessCompilerDataFactory(
      creator,
      extractModelDefinition,
      modelConfig,
      runtimeMode,
      configsFromProviderWithDictionaryEditor,
      nodesDeploymentData,
      processListeners,
    ) {

  override protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition,
      definitionContext: ComponentDefinitionContext,
  ): ModelDefinition = {
    val allStartNodesData = process.allStartNodes.toList
      .flatMap(_.headOption)
      .map(_.data)

    val usedSourceIds = allStartNodesData
      .collect { case source: Source =>
        ComponentIdExtractor.fromScenarioNode(source)
      }
      .flatten
      .toSet

    val processedComponents = originalModelDefinition.components.components.map {
      case source if usedSourceIds.contains(source.id) =>
        prepareSourceFactory(source, definitionContext)
      case service if service.componentType == ComponentType.Service =>
        prepareService(service, definitionContext)
      case other => other
    }

    val fragmentParametersDefinitionExtractor = new FragmentParametersDefinitionExtractor(
      definitionContext.userCodeClassLoader,
      definitionContext.classDefinitions,
      modelConfig.globalParametersConfig
    )
    val fragmentSourceDefinitionPreparer = new StubbedFragmentSourceDefinitionPreparer(
      fragmentParametersDefinitionExtractor
    )

    val stubbedSourceForFragments =
      allStartNodesData.collect { case frag: FragmentInputDefinition =>
        // We create source definition only to reuse prepareSourceFactory method.
        // Source will have fragment component type to avoid collisions with normal sources
        val fragmentSourceDef = fragmentSourceDefinitionPreparer.createSourceDefinition(frag.id, frag)
        prepareSourceFactory(fragmentSourceDef, definitionContext)
      }

    originalModelDefinition
      .copy(components = originalModelDefinition.components.copy(components = processedComponents))
      .withComponents(stubbedSourceForFragments)
  }

  protected def prepareService(
      service: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation

  protected def prepareSourceFactory(
      sourceFactory: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation

}
