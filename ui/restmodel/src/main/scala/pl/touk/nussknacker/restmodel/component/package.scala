package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentGroupName
import pl.touk.nussknacker.restmodel.component.ComponentType.ComponentType

import java.util.UUID

package object component {

  @JsonCodec
  final case class ComponentAction(name: String, title: Option[String], icon: String, url: String)

  object ComponentListElement {

    //FIXME: it's temporary solution to create id based on code/ name / componentType until we provide ComponentId
    def createComponentUUID(code: Int, name: String, componentType: ComponentType): UUID =
      if (ComponentType.isBaseComponent(componentType)) UUID.nameUUIDFromBytes(componentType.toString.getBytes)
      else UUID.nameUUIDFromBytes(s"$code-$componentType-$name".getBytes)

    def sortMethod(component: ComponentListElement): (String, UUID) = (component.name, component.uuid)
  }

  @JsonCodec
  final case class ComponentListElement(uuid: UUID, name: String, icon: String, componentType: ComponentType,
                                        componentGroupName: ComponentGroupName, categories: List[String],
                                        actions: List[ComponentAction], usageCount: Int)

}
