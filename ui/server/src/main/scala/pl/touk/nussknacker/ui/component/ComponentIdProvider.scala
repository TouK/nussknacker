package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.graph.node.{NodeData, WithComponent}
import pl.touk.nussknacker.restmodel.process.ProcessingType

trait ComponentIdProvider {
  def createComponentId(processingType: ProcessingType, name: String, componentType: ComponentType): ComponentId
  def forBaseComponent(componentType: ComponentType): ComponentId
  def nodeToComponentId(processingType: ProcessingType, node: NodeData): Option[ComponentId]
}

object DefaultComponentIdProvider extends ComponentIdProvider {
  //TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
  override def createComponentId(processingType: ProcessingType, name: String, componentType: ComponentType): ComponentId =
    if (ComponentType.isBaseComponent(componentType))
      forBaseComponent(componentType)
    else
      ComponentId(s"$processingType-$componentType-$name")

  override def forBaseComponent(componentType: ComponentType): ComponentId = {
    if (!ComponentType.isBaseComponent(componentType)) {
      throw new IllegalArgumentException(s"Component type: $componentType is not base component.")
    }

    ComponentId(componentType.toString)
  }

  override def nodeToComponentId(processingType: ProcessingType, node: NodeData): Option[ComponentId] =
    ComponentType
      .fromNodeData(node)
      .map(componentType => node match {
        case n: WithComponent => createComponentId(processingType, n.componentId, componentType)
        case _ => createComponentId(processingType, componentType.toString, componentType)
      })
}
