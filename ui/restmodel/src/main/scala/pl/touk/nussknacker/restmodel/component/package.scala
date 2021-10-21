package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}

package object component {

  @JsonCodec
  final case class ComponentAction(id: String, title: String, icon: String, url: Option[String])

  object ComponentAction {
    val ComponentIdTemplate = "$componentId"
    val ComponentNameTemplate = "$componentName"

    def apply(id: String, title: String, icon: String, componentId: String, componentName: String, url: Option[String] = None): ComponentAction =
      ComponentAction(
        id,
        fillByComponentData(title, componentId, componentName),
        fillByComponentData(icon, componentId, componentName),
        url.map(u => fillByComponentData(u, componentId, componentName))
      )

    def fillByComponentData(text: String, componentId: String, componentName: String): String = {
      text
        .replace(ComponentIdTemplate, componentId)
        .replace(ComponentNameTemplate, componentName)
    }
  }

  object ComponentListElement {

    //TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
    def createComponentId(processingType: ProcessingType, name: String, componentType: ComponentType): String =
      if (ComponentType.isBaseComponent(componentType)) componentType.toString else s"$processingType-$componentType-$name"

    def sortMethod(component: ComponentListElement): (String, String) = (component.name, component.id)
  }

  @JsonCodec
  final case class ComponentListElement(id: String, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], actions: List[ComponentAction])

}
