package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.restmodel.component.ComponentType.ComponentType

package object component {

  @JsonCodec
  final case class ComponentAction(id: String, title: String, icon: String, url: String)

  object ComponentListElement {

    //TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
    def createComponentId(processingType: ProcessingType, name: String, componentType: ComponentType): ComponentId =
      if (ComponentType.isBaseComponent(componentType))
        ComponentId(componentType.toString)
      else
        ComponentId(s"$processingType-$componentType-$name".toLowerCase)

    def sortMethod(component: ComponentListElement): (String, String) = (component.name, component.id.value)
  }

  @JsonCodec
  final case class ComponentListElement(id: ComponentId, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String])

}
