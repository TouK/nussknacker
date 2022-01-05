package pl.touk.nussknacker.restmodel

import io.circe.Encoder
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveEncoder
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessAction}

import java.time.LocalDateTime
import scala.language.implicitConversions

package object component {

  object ComponentLink {
    implicit def encoder(implicit encodeLink: Encoder[NuLink]): Encoder[ComponentLink] = deriveEncoder[ComponentLink]
  }

  final case class ComponentLink(id: String, title: String, icon: NuIcon, url: NuLink)

  object ComponentListElement {
    implicit def encoder(implicit encodeLink: Encoder[NuLink]): Encoder[ComponentListElement] = deriveEncoder[ComponentListElement]
    def sortMethod(component: ComponentListElement): (String, String) = (component.name, component.id.value)
  }

  final case class ComponentListElement(id: ComponentId, name: String, icon: NuIcon, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], links: List[ComponentLink], usageCount: Long)

  object ComponentUsagesInScenario {
    def apply(process: BaseProcessDetails[_], nodesId: List[String]): ComponentUsagesInScenario = ComponentUsagesInScenario(
      id = process.id, //Right now we assume that scenario id is name..
      name = process.idWithName.name,
      processId = process.processId,
      nodesId = nodesId,
      isArchived = process.isArchived,
      isSubprocess = process.isSubprocess,
      processCategory = process.processCategory,
      modificationDate = process.modificationDate,
      createdAt = process.createdAt,
      createdBy = process.createdBy,
      lastAction = process.lastAction
    )
  }

  @JsonCodec
  final case class ComponentUsagesInScenario(id: String, name: ProcessName, processId: ProcessId, nodesId: List[String], isArchived: Boolean, isSubprocess: Boolean, processCategory: String, modificationDate: LocalDateTime, createdAt: LocalDateTime, createdBy: String, lastAction: Option[ProcessAction])

}
