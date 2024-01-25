package pl.touk.nussknacker.engine.api.component

import io.circe.{Encoder, KeyEncoder}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

final case class ComponentId(`type`: ComponentType, name: String) extends Ordered[ComponentId] {
  override def toString: String                = `type`.toString + "-" + name
  override def compare(that: ComponentId): Int = name.compareTo(that.name)
}

object ComponentId {

  implicit val keyEncoder: KeyEncoder[ComponentId] = KeyEncoder.encodeKeyString.contramap(_.toString)

  implicit val encoder: Encoder[ComponentId] = Encoder.encodeString.contramap(_.toString)

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
