package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

case class NuExceptionInfo[T <: Throwable](nodeComponentId: Option[NodeComponentInfo], throwable: T, context: Context) extends Serializable

object NodeComponentInfo {
  def apply(nodeId: String, componentName: String, componentType: ComponentType): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentInfo(componentName, componentType)))
  }
}

case class NodeComponentInfo(nodeId: String, componentInfo: Option[ComponentInfo])

case class ComponentInfo(componentName: String, componentType: ComponentType)