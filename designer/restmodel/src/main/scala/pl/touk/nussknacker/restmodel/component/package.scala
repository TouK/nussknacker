package pl.touk.nussknacker.restmodel

import io.circe.Encoder
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import pl.touk.nussknacker.engine.api.CirceUtil._
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails

import java.net.URI
import java.time.Instant

package object component {

  import pl.touk.nussknacker.restmodel.codecs.URICodecs._

  type NodeId = String

  @ConfiguredJsonCodec sealed trait NodeMetadata
  object NodeMetadata {
    case class FragmentNodeMetadata(fragmentNodeId: String, nodeId: NodeId) extends NodeMetadata

    case class ScenarioNodeMetadata(nodeId: NodeId) extends NodeMetadata
  }


  object ComponentLink {
    val DocumentationId = "documentation"
    val DocumentationTile: String = "Documentation"
    val documentationIcon: URI = URI.create("/assets/icons/documentation.svg")

    def createDocumentationLink(docUrl: String): ComponentLink =
      ComponentLink(DocumentationId, DocumentationTile, documentationIcon, URI.create(docUrl))
  }

  @JsonCodec
  final case class ComponentLink(id: String, title: String, icon: URI, url: URI)

  object ComponentListElement {
    def sortMethod(component: ComponentListElement): (String, String) = (component.name, component.id.value)
  }

  @JsonCodec
  final case class ComponentListElement(id: ComponentId,
                                        name: String,
                                        icon: String,
                                        componentType: ComponentType,
                                        componentGroupName: ComponentGroupName,
                                        categories: List[String],
                                        links: List[ComponentLink],
                                        usageCount: Long)

  object ComponentUsagesInScenario {
    def apply(process: BaseProcessDetails[_], nodesId: List[NodeMetadata]): ComponentUsagesInScenario = ComponentUsagesInScenario(
      id = process.id, //Right now we assume that scenario id is name..
      name = process.idWithName.name,
      processId = process.processId,
      nodesId = nodesId,
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
  final case class ComponentUsagesInScenario(id: String,
                                             name: ProcessName,
                                             processId: ProcessId,
                                             nodesId: List[NodeMetadata],
                                             isSubprocess: Boolean,
                                             processCategory: String,
                                             modificationDate: Instant,
                                             modifiedAt: Instant,
                                             modifiedBy: String,
                                             createdAt: Instant,
                                             createdBy: String,
                                             lastAction: Option[ProcessAction])

}
