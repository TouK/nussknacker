package pl.touk.nussknacker.engine.api.component

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

//Right now it's not yet clear what this id will be.
final case class ComponentId(value: String)

object ComponentId {
  implicit val encoder: Encoder[ComponentId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ComponentId] = deriveUnwrappedDecoder

  def create(value: String): ComponentId = ComponentId(value.toLowerCase)

  def forBaseComponent(componentType: ComponentType): ComponentId = {
    if (!ComponentType.isBaseComponent(componentType)) {
      throw new IllegalArgumentException(s"Component type: $componentType is not base component.")
    }

    ComponentId.create(componentType.toString)
  }

  //TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
  def apply(processingType: String, name: String, componentType: ComponentType): ComponentId =
    if (ComponentType.isBaseComponent(componentType))
      forBaseComponent(componentType)
    else
      ComponentId.create(s"$processingType-$componentType-$name")
}
