package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import sttp.tapir.Schema

import java.net.URI
import java.time.Instant

package object component {

  import io.circe.generic.extras.semiauto._
  import pl.touk.nussknacker.engine.api.CirceUtil._
  import pl.touk.nussknacker.restmodel.codecs.URICodecs._

  type NodeId = String

  @ConfiguredJsonCodec sealed trait NodeUsageData {
    def nodeId: NodeId
  }

  object NodeUsageData {
    final case class FragmentUsageData(fragmentNodeId: String, nodeId: NodeId) extends NodeUsageData

    final case class ScenarioUsageData(nodeId: NodeId) extends NodeUsageData
  }

  object ComponentLink {
    val DocumentationId           = "documentation"
    val DocumentationTile: String = "Documentation"
    val documentationIcon: URI    = URI.create("/assets/icons/documentation.svg")

    def createDocumentationLink(docUrl: String): ComponentLink =
      ComponentLink(DocumentationId, DocumentationTile, documentationIcon, URI.create(docUrl))
  }

  @JsonCodec
  final case class ComponentLink(id: String, title: String, icon: URI, url: URI)

  object ComponentListElement {
    def sortMethod(component: ComponentListElement): (String, String) = (component.name, component.id.value)
  }

  @JsonCodec
  final case class ComponentListElement(
      id: DesignerWideComponentId,
      name: String,
      icon: String,
      componentType: ComponentType,
      componentGroupName: ComponentGroupName,
      categories: List[String],
      links: List[ComponentLink],
      usageCount: Long
  ) {
    def componentId: ComponentId = ComponentId(componentType, name)
  }

  @JsonCodec
  final case class ComponentUsagesInScenario(
      name: ProcessName,
      nodesUsagesData: List[NodeUsageData],
      isFragment: Boolean,
      processCategory: String,
      modificationDate: Instant,
      modifiedAt: Instant,
      modifiedBy: String,
      createdAt: Instant,
      createdBy: String,
      lastAction: Option[ProcessAction]
  )

  implicit val uriSchema: Schema[URI] = Schema.string
}
