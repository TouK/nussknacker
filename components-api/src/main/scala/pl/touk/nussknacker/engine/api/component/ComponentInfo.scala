package pl.touk.nussknacker.engine.api.component

import io.circe.KeyEncoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

// TODO: rename to ComponentId
@JsonCodec
final case class ComponentInfo(`type`: ComponentType, name: String) {
  override def toString: String = `type`.toString + "-" + name
}

object ComponentInfo {

  implicit val keyEncoder: KeyEncoder[ComponentInfo] = KeyEncoder.encodeKeyString.contramap(_.toString)

}

// These names are visible on pallet and used as a part of component identifiers (in urls and in stored component usages cache structure)
object BuiltInComponentInfo {

  val Filter: ComponentInfo                   = ComponentInfo(ComponentType.BuiltIn, "filter")
  val Split: ComponentInfo                    = ComponentInfo(ComponentType.BuiltIn, "split")
  val Choice: ComponentInfo                   = ComponentInfo(ComponentType.BuiltIn, "choice")
  val Variable: ComponentInfo                 = ComponentInfo(ComponentType.BuiltIn, "variable")
  val RecordVariable: ComponentInfo           = ComponentInfo(ComponentType.BuiltIn, "record-variable")
  val FragmentInputDefinition: ComponentInfo  = ComponentInfo(ComponentType.BuiltIn, "input")
  val FragmentOutputDefinition: ComponentInfo = ComponentInfo(ComponentType.BuiltIn, "output")

  val All: List[ComponentInfo] =
    List(Filter, Split, Choice, Variable, RecordVariable, FragmentInputDefinition, FragmentOutputDefinition)

}
