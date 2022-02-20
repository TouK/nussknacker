package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

object NodeComponentInfo {
  def apply(nodeId: String, componentName: String, componentType: ComponentType): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentInfo(componentName, componentType)))
  }

  def forBaseNode(nodeId: String, componentType: ComponentType): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentInfo(componentType.toString, componentType)))
  }
}

case class NodeComponentInfo(nodeId: String, componentInfo: Option[ComponentInfo])

case class ComponentInfo(componentName: String, componentType: ComponentType)
