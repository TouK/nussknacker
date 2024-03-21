package pl.touk.nussknacker.restmodel

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentId,
  DesignerWideComponentId,
  ProcessingMode
}
import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.process.ProcessName
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

  sealed trait AllowedProcessingModes

  object AllowedProcessingModes {
    case object AllProcessingModes                                                  extends AllowedProcessingModes
    final case class FromList(allowedProcessingModes: NonEmptyList[ProcessingMode]) extends AllowedProcessingModes
  }

  implicit val allowedProcessingModesEncoder: Encoder[AllowedProcessingModes] = Encoder.instance {
    case AllowedProcessingModes.AllProcessingModes               => "ALL".asJson
    case AllowedProcessingModes.FromList(allowedProcessingModes) => allowedProcessingModes.asJson
  }

  implicit val allowedProcessingModesDecoder: Decoder[AllowedProcessingModes] = Decoder.instance { c =>
    c.as[List[ProcessingMode]]
      .flatMap {
        case Nil          => Left(DecodingFailure("Invalid value, only ALL or list of strings is allowed", Nil))
        case head :: tail => Right(AllowedProcessingModes.FromList(NonEmptyList(head, tail)))
      }
      .orElse {
        c.as[String].map(_ == "ALL").flatMap {
          case true  => Right(AllowedProcessingModes.AllProcessingModes)
          case false => Left(DecodingFailure("Invalid value, only ALL or list of strings is allowed", Nil))
        }
      }
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
      usageCount: Long,
      allowedProcessingModes: AllowedProcessingModes
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
