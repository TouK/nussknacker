package pl.touk.nussknacker.engine.api.component

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}

final case class ComponentId private (value: String) extends AnyVal {
  override def toString: String = value
}

object ComponentId {
  implicit val encoder: Encoder[ComponentId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ComponentId] = deriveUnwrappedDecoder

  def apply(value: String): ComponentId = new ComponentId(value.toLowerCase)

  def forBuiltInComponent(componentInfo: ComponentInfo): ComponentId = {
    if (componentInfo.`type` != ComponentType.BuiltIn) {
      throw new IllegalArgumentException(s"Component type: ${componentInfo.`type`} is not built-in component type")
    }
    // Built-in components are the same for each processing type so original id is the same across them
    apply(componentInfo.toString)
  }

  def default(processingType: String, componentInfo: ComponentInfo): ComponentId = {
    if (componentInfo.`type` == ComponentType.BuiltIn) {
      forBuiltInComponent(componentInfo)
    } else
      apply(s"$processingType-$componentInfo")
  }

}
