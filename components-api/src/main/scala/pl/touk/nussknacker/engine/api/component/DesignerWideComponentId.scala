package pl.touk.nussknacker.engine.api.component

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

// TODO This class is used as a work around for the problem that the components are duplicated across processing types.
//      We plan to get rid of this. After that, we could replace usages of this class by usage of ComponentId
//      and remove the option to configure it via componentsUiConfig
final case class DesignerWideComponentId private (value: String) extends AnyVal {
  override def toString: String = value
}

object DesignerWideComponentId {
  implicit val encoder: Encoder[DesignerWideComponentId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[DesignerWideComponentId] = deriveUnwrappedDecoder

  implicit val keyEncoder: KeyEncoder[DesignerWideComponentId] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[DesignerWideComponentId] =
    KeyDecoder.decodeKeyString.map(DesignerWideComponentId(_))

  def apply(value: String): DesignerWideComponentId = new DesignerWideComponentId(value.toLowerCase)

  def forBuiltInComponent(componentId: ComponentId): DesignerWideComponentId = {
    if (componentId.`type` != ComponentType.BuiltIn) {
      throw new IllegalArgumentException(s"Component type: ${componentId.`type`} is not built-in component type")
    }
    // Built-in components are the same for each processing type so original id is the same across them
    apply(componentId.toString)
  }

  def default(processingType: String, componentId: ComponentId): DesignerWideComponentId = {
    if (componentId.`type` == ComponentType.BuiltIn) {
      forBuiltInComponent(componentId)
    } else
      apply(s"$processingType-$componentId")
  }

}
