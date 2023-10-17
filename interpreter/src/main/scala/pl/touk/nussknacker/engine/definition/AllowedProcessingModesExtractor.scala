package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{Component, ComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.node.NodeData

object AllowedProcessingModesExtractor {

  def componentsProcessingModes(
      modelDefinition: ProcessDefinition[DefinitionExtractor.ObjectWithMethodDef],
      category: String
  ): Map[ComponentId, Option[Set[ProcessingMode]]] = {
    // It is a little bit hacky. We don't need a real ComponentId on this step but only some ComponentType + component name
    // Unfortunately, we have a separate ComponentType for Processor and Enricher and it makes it difficult to pick one for
    // Service. Because of that we use this dumb component id provider
    val dumbComponentIdProvider = new ComponentIdProvider {
      override def createComponentId(
          processingType: String,
          name: Option[String],
          componentType: ComponentType
      ): ComponentId = ComponentId(s"$componentType-${name.get}")

      override def nodeToComponentId(processingType: String, node: NodeData): Option[ComponentId] = ???
    }
    modelDefinition
      .withComponentIds(dumbComponentIdProvider, "dumpProcessingType")
      .allComponentsDefinitions
      .filter(_._2.availableForCategory(category))
      .map { case (componentIdWithName, definition) =>
        val allowedProcessingModes = definition.obj match {
          case component: Component =>
            component.allowedProcessingModes
          case other =>
            throw new IllegalArgumentException(
              s"Component definition for component: $componentIdWithName which doesn't extends Component class: ${other.getClass.getName}"
            )
        }
        componentIdWithName.id -> allowedProcessingModes
      }
      .toMap
  }

}
