package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.RealComponentType.RealComponentType

object NodeComponentInfo {

  def apply(nodeId: String, componentName: String, componentType: RealComponentType): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentInfo(componentName, componentType)))
  }

}

final case class NodeComponentInfo(nodeId: String, componentInfo: Option[ComponentInfo])

final case class ComponentInfo(componentName: String, componentType: RealComponentType) {
  override def toString: String = componentType.toString + "-" + componentName
}

// These names are visible on pallet and used as a part of component identifiers (in urls and in stored component usages cache structure)
object BuiltInComponentNames {

  val Filter = "filter"
  val Split  = "split"
  // FIXME: change to choice
  val Choice   = "switch"
  val Variable = "variable"
  // FIXME: change to record-variable
  val RecordVariable           = "mapVariable"
  val FragmentInputDefinition  = "input"
  val FragmentOutputDefinition = "output"

}
