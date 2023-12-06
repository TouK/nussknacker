package pl.touk.nussknacker.engine.api.component

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

@JsonCodec
final case class ComponentInfo(`type`: ComponentType, name: String) {
  override def toString: String = `type`.toString + "-" + name
}

// These names are visible on pallet and used as a part of component identifiers (in urls and in stored component usages cache structure)
object BuiltInComponentInfo {

  val Filter: ComponentInfo   = ComponentInfo(ComponentType.BuiltIn, "filter")
  val Split: ComponentInfo    = ComponentInfo(ComponentType.BuiltIn, "split")
  val Choice: ComponentInfo   = ComponentInfo(ComponentType.BuiltIn, "choice")
  val Variable: ComponentInfo = ComponentInfo(ComponentType.BuiltIn, "variable")
  // TODO: change name to record-variable
  val RecordVariable: ComponentInfo           = ComponentInfo(ComponentType.BuiltIn, "mapVariable")
  val FragmentInputDefinition: ComponentInfo  = ComponentInfo(ComponentType.BuiltIn, "input")
  val FragmentOutputDefinition: ComponentInfo = ComponentInfo(ComponentType.BuiltIn, "output")

  val All: List[ComponentInfo] =
    List(Filter, Split, Choice, Variable, RecordVariable, FragmentInputDefinition, FragmentOutputDefinition)

}
