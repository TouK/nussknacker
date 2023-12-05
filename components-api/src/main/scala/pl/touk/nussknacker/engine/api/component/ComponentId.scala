package pl.touk.nussknacker.engine.api.component

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

final case class ComponentId private (value: String) extends AnyVal {
  override def toString: String = value
}

object ComponentId {
  implicit val encoder: Encoder[ComponentId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ComponentId] = deriveUnwrappedDecoder

  def apply(value: String): ComponentId = new ComponentId(value.toLowerCase)

  // FIXME: switch to ComponentInfo based variant
  def forBaseComponent(legacyComponentType: ComponentType): ComponentId = {
    if (!ComponentType.isBaseComponent(legacyComponentType)) {
      throw new IllegalArgumentException(s"Component type: $legacyComponentType is not base component.")
    }
    val componentInfo = translate("", legacyComponentType)
    apply(componentInfo.toString)
  }

  // FIXME: switch to ComponentInfo based variant
  def default(processingType: String, name: String, legacyComponentType: ComponentType): ComponentId = {
    val componentInfo = translate(name, legacyComponentType)
    default(processingType, componentInfo)
  }

  def default(processingType: String, componentInfo: ComponentInfo): ComponentId = {
    if (componentInfo.componentType == RealComponentType.BuiltIn) {
      // Hardcoded components are the same for each processing type so original id can be the same across them
      apply(componentInfo.toString)
    } else
      apply(s"$processingType-$componentInfo")
  }

  private def translate(name: String, legacyComponentType: ComponentType): ComponentInfo = {
    legacyComponentType match {
      case ComponentType.Filter   => ComponentInfo(BuiltInComponentNames.Filter, RealComponentType.BuiltIn)
      case ComponentType.Split    => ComponentInfo(BuiltInComponentNames.Split, RealComponentType.BuiltIn)
      case ComponentType.Switch   => ComponentInfo(BuiltInComponentNames.Choice, RealComponentType.BuiltIn)
      case ComponentType.Variable => ComponentInfo(BuiltInComponentNames.Variable, RealComponentType.BuiltIn)
      case ComponentType.MapVariable =>
        ComponentInfo(BuiltInComponentNames.RecordVariable, RealComponentType.BuiltIn)
      case ComponentType.Processor  => ComponentInfo(name, RealComponentType.Service)
      case ComponentType.Enricher   => ComponentInfo(name, RealComponentType.Service)
      case ComponentType.Sink       => ComponentInfo(name, RealComponentType.Sink)
      case ComponentType.Source     => ComponentInfo(name, RealComponentType.Source)
      case ComponentType.Fragments  => ComponentInfo(name, RealComponentType.Fragment)
      case ComponentType.CustomNode => ComponentInfo(name, RealComponentType.CustomComponent)
      case ComponentType.FragmentInput =>
        ComponentInfo(BuiltInComponentNames.FragmentInputDefinition, RealComponentType.BuiltIn)
      case ComponentType.FragmentOutput =>
        ComponentInfo(BuiltInComponentNames.FragmentOutputDefinition, RealComponentType.BuiltIn)
    }
  }

}
