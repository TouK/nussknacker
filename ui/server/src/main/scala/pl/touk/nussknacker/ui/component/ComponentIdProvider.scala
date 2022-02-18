package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.component.ComponentUtil
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.engine.graph.node.{NodeData, WithComponent}
import pl.touk.nussknacker.restmodel.process.ProcessingType

//TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
trait ComponentIdProvider {
  def createComponentId(processingType: ProcessingType, name: String, componentType: ComponentType): ComponentId
  def nodeToComponentId(processingType: ProcessingType, node: NodeData): Option[ComponentId]
}

class DefaultComponentIdProvider(configs: Map[ProcessingType, ComponentsUiConfig]) extends ComponentIdProvider {
  override def createComponentId(processingType: ProcessingType, name: String, componentType: ComponentType): ComponentId = {
    val defaultComponentId = ComponentId.default(processingType, name, componentType)
    val overriddenComponentId = getOverriddenComponentId(processingType, name, defaultComponentId)

    //We assume that base and currently fragment component's id can't be overridden
    if (defaultComponentId != overriddenComponentId && (ComponentType.isBaseComponent(componentType) || componentType == ComponentType.Fragments)) {
      throw new IllegalArgumentException(s"ComponentId can't be overridden for component type: '$componentType'.")
    }

    overriddenComponentId
  }

  override def nodeToComponentId(processingType: ProcessingType, node: NodeData): Option[ComponentId] =
    ComponentUtil
      .fromNodeData(node)
      .map(componentType => node match {
        case n: WithComponent => createComponentId(processingType, n.componentId, componentType)
        case _ => ComponentId.forBaseComponent(componentType)
      })

  private def getOverriddenComponentId(processingType: ProcessingType, componentName: String, defaultComponentId: ComponentId): ComponentId = {
    def getComponentId(namespace: String) = configs.get(processingType).flatMap(_.get(namespace)).flatMap(_.componentId)

    val componentId = getComponentId(componentName)

    //It's work around for components with the same name and different componentType, eg. kafka-avro
    //where default id is combination of processingType-componentType-name
    val componentIdForDefaultComponentId = getComponentId(defaultComponentId.value)

    componentId
      .orElse(componentIdForDefaultComponentId)
      .getOrElse(defaultComponentId)
  }
}
