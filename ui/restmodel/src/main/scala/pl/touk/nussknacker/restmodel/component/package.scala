package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessAction}

import java.net.URI
import java.time.LocalDateTime

package object component {

  import pl.touk.nussknacker.restmodel.codecs.URICodecs._

  @JsonCodec
  final case class ComponentLink(id: String, title: String, icon: URI, url: URI)

  object ComponentListElement {
    def sortMethod(component: ComponentListElement): (String, String) = (component.name, component.id.value)
  }

  @JsonCodec
  final case class ComponentListElement(id: ComponentId, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], links: List[ComponentLink], usageCount: Long)

  object ComponentUsagesInScenario {
    def apply(process: BaseProcessDetails[_], nodesId: List[String]): ComponentUsagesInScenario = ComponentUsagesInScenario(
      id = process.id, //Right now we assume that scenario id is name..
      name = process.idWithName.name,
      processId = process.processId,
      nodesId = nodesId,
      isArchived = process.isArchived,
      isSubprocess = process.isSubprocess,
      processCategory = process.processCategory,
      modificationDate = process.modificationDate, //TODO: Deprecated, please use modifiedAt
      modifiedAt = process.modifiedAt,
      modifiedBy = process.modifiedBy,
      createdAt = process.createdAt,
      createdBy = process.createdBy,
      lastAction = process.lastAction
    )
  }

  @JsonCodec
  final case class ComponentUsagesInScenario(id: String, name: ProcessName, processId: ProcessId, nodesId: List[String], isArchived: Boolean, isSubprocess: Boolean, processCategory: String, modificationDate: LocalDateTime, modifiedAt: LocalDateTime, modifiedBy: String, createdAt: LocalDateTime, createdBy: String, lastAction: Option[ProcessAction])

}
