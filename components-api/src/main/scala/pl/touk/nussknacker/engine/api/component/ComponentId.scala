package pl.touk.nussknacker.engine.api.component

import io.circe.KeyEncoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

// TODO: serialize always as a string-string
@JsonCodec
final case class ComponentId(`type`: ComponentType, name: String) {
  override def toString: String = `type`.toString + "-" + name
}

object ComponentId {

  implicit val keyEncoder: KeyEncoder[ComponentId] = KeyEncoder.encodeKeyString.contramap(_.toString)

}

// These names are visible on pallet and used as a part of component identifiers (in urls and in stored component usages cache structure)
object BuiltInComponentId {

  val Filter: ComponentId                   = ComponentId(ComponentType.BuiltIn, "filter")
  val Split: ComponentId                    = ComponentId(ComponentType.BuiltIn, "split")
  val Choice: ComponentId                   = ComponentId(ComponentType.BuiltIn, "choice")
  val Variable: ComponentId                 = ComponentId(ComponentType.BuiltIn, "variable")
  val RecordVariable: ComponentId           = ComponentId(ComponentType.BuiltIn, "record-variable")
  val FragmentInputDefinition: ComponentId  = ComponentId(ComponentType.BuiltIn, "input")
  val FragmentOutputDefinition: ComponentId = ComponentId(ComponentType.BuiltIn, "output")

  val FragmentDefinitionComponents: List[ComponentId] = List(FragmentInputDefinition, FragmentOutputDefinition)

  val AllAvailableForScenario: List[ComponentId] = List(Filter, Split, Choice, Variable, RecordVariable)

  val All: List[ComponentId] = AllAvailableForScenario ::: FragmentDefinitionComponents

  val AllAvailableForFragment: List[ComponentId] = All

}
