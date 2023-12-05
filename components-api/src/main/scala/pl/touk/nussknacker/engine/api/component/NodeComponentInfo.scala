package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.RealComponentType.RealComponentType

object NodeComponentInfo {

  def apply(nodeId: String, componentName: String, componentType: RealComponentType): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentInfo(componentName, componentType)))
  }

}

case class NodeComponentInfo(nodeId: String, componentInfo: Option[ComponentInfo])

case class ComponentInfo(componentName: String, componentType: RealComponentType)

object BaseComponentNames {

  val Filter = "filter"
  val Split  = "split"
  // FIXME: change to choice
  val Choice   = "switch"
  val Variable = "variable"
  // FIXME: change to record-variable
  val RecordVariable = "mapVariable"

}
