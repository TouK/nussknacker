package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}

package object component {

  @JsonCodec
  final case class ComponentAction(id: String, title: String, icon: String, url: String)

  object ComponentListElement {

    //TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
    def createComponentId(processingType: ProcessingType, name: String, componentType: ComponentType): String =
      if (ComponentType.isBaseComponent(componentType)) componentType.toString else s"$processingType-$componentType-$name"

    def sortMethod(component: ComponentListElement): (String, String) = (component.name, component.id)
  }

  @JsonCodec
  final case class ComponentListElement(id: String, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], actions: List[ComponentAction])

}
