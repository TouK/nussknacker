package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

object NodeComponentInfo {

  // TODO: remove this method - in every place of usage, should be accessible ComponentId
  def apply(nodeId: String, componentType: ComponentType, componentName: String): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentId(componentType, componentName)))
  }

}

// TODO: componentInfo -> componentId + simple serialization
final case class NodeComponentInfo(nodeId: String, componentInfo: Option[ComponentId])
