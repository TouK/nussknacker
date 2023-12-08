package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

object NodeComponentInfo {

  def apply(nodeId: String, componentType: ComponentType, componentName: String): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentInfo(componentType, componentName)))
  }

}

final case class NodeComponentInfo(nodeId: String, componentInfo: Option[ComponentInfo])
