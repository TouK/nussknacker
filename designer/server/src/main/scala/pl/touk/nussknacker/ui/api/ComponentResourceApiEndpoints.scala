package pl.touk.nussknacker.ui.api

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.restmodel.component.{
  ComponentLink,
  ComponentListElement,
  ComponentUsagesInScenario,
  NodeUsageData
}
import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class ComponentResourceApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import ComponentResourceApiEndpoints.Dtos._

  val componentsListEndpoint: SecuredEndpoint[Unit, Unit, ComponentsListSuccessfulResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Listing components")
      .tag("Components")
      .withSecurity(auth)
      .get
      .in("componentss")
      .out(
        statusCode(Ok).and(
          jsonBody[ComponentsListSuccessfulResponseDto]
            .example(
              Example.of(
                summary = Some("List of available components"),
                value = ComponentsListSuccessfulResponseDto(
                  List(
                    ComponentListElementDto(
                      id = "streaming-dev-processor-accountservice",
                      name = "accountService",
                      icon = "/assets/components/Processor.svg",
                      componentType = "processor",
                      componentGroupName = "services",
                      categories = List(
                        "Category1",
                        "Category2"
                      ),
                      links = List(
                        LinkDto(
                          id = "documentation",
                          title = "Documentation",
                          icon = "/assets/icons/documentation.svg",
                          url = "accountServiceDocs"
                        )
                      ),
                      usageCount = 1
                    )
                  )
                )
              )
            )
        )
      )

  val componentUsageEndpoint: SecuredEndpoint[String, String, ComponentUsageSuccessfulResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Show component usage")
      .tag("Components")
      .get
      .in("componentss" / path[String]("id") / "usages")
      .out(
        statusCode(Ok).and(
          jsonBody[ComponentUsageSuccessfulResponseDto]
        )
      )
      .errorOut(
        statusCode(NotFound).and(
          stringBody
            .example("Component {id} not exist.")
        )
      )
      .withSecurity(auth)

}

object ComponentResourceApiEndpoints {

  object Dtos {

    final case class ComponentsListSuccessfulResponseDto(
        components: List[ComponentListElementDto]
    )

    object ComponentsListSuccessfulResponseDto {

      implicit val responseSchema: Schema[ComponentsListSuccessfulResponseDto] =
        implicitly[Schema[List[ComponentListElementDto]]].as

      implicit val circeDecoder: Decoder[ComponentsListSuccessfulResponseDto] = {
        Decoder.instance { c =>
          for {
            list <- c.value.as[List[ComponentListElementDto]]
          } yield ComponentsListSuccessfulResponseDto(list)
        }
      }

      implicit val circeEncoder: Encoder[ComponentsListSuccessfulResponseDto] = {
        Encoder.encodeJson.contramap { componentList =>
          componentList.components.asJson
        }
      }

    }

    @derive(decoder, encoder, schema)
    final case class ComponentListElementDto private (
        id: String,
        name: String,
        icon: String,
        componentType: String,
        componentGroupName: String,
        categories: List[String],
        links: List[LinkDto],
        usageCount: Long
    )

    object ComponentListElementDto {

      def apply(componentListElement: ComponentListElement) =
        new ComponentListElementDto(
          id = componentListElement.id.value,
          name = componentListElement.name,
          icon = componentListElement.icon,
          componentType = componentListElement.componentType.toString,
          componentGroupName = componentListElement.componentGroupName.value,
          categories = componentListElement.categories,
          links = componentListElement.links.map(link => LinkDto(link)),
          usageCount = componentListElement.usageCount
        )

    }

    @derive(decoder, encoder, schema)
    final case class LinkDto(
        id: String,
        title: String,
        icon: String,
        url: String
    )

    object LinkDto {

      def apply(link: ComponentLink) =
        new LinkDto(
          id = link.id,
          title = link.title,
          icon = link.icon.toString,
          url = link.url.toString
        )

    }

    @derive(decoder)
    final case class ComponentUsageSuccessfulResponseDto(
        usages: List[ComponentUsagesInScenarioDto]
    )

    object ComponentUsageSuccessfulResponseDto {
      //      def apply(usages: List[ComponentUsagesInScenario]) = {
      //        val value = usages.map(usage => ComponentUsagesInScenarioDto(usage))
      //
      //        new ComponentUsageSuccessfulResponseDto(value)
      //      }

      implicit val componentUsagesSchema: Schema[ComponentUsageSuccessfulResponseDto] =
        implicitly[Schema[List[ComponentUsagesInScenarioDto]]].as

      implicit val circeEncoder: Encoder[ComponentUsageSuccessfulResponseDto] = {
        Encoder.encodeJson.contramap { usagesList =>
          usagesList.usages.asJson
        }
      }

    }

    @derive(encoder, decoder, schema)
    final case class ComponentUsagesInScenarioDto(
        id: String,
        name: String,
        processId: Long,
        nodesUsagesData: List[Map[String, String]],
        isFragment: Boolean,
        processCategory: String,
        modificationDate: String,
        modifiedAt: String,
        modifiedBy: String,
        createdAt: String,
        createdBy: String,
        lastAction: Option[ProcessActionDto]
    )

    object ComponentUsagesInScenarioDto {

      def apply(usage: ComponentUsagesInScenario): ComponentUsagesInScenarioDto = {
//        val lastAction: Option[ProcessAction] =
//          usage.lastAction match  {
//            case "None" => None
//            case action => Option(action)
//          }
//        usage.lastAction
        new ComponentUsagesInScenarioDto(
          id = usage.id,
          name = usage.name.value,
          processId = usage.processId.value,
          nodesUsagesData = usage.nodesUsagesData.map(node => NodeUsageDataDto(node, usage.isFragment)),
          isFragment = usage.isFragment,
          processCategory = usage.processCategory,
          modificationDate = usage.modificationDate.toString,
          modifiedAt = usage.modifiedAt.toString,
          modifiedBy = usage.modifiedBy,
          createdAt = usage.createdAt.toString,
          createdBy = usage.createdBy,
          lastAction = usage.lastAction match {
            case None         => None
            case Some(action) => Option(ProcessActionDto(action))
          }
        )
      }

    }

    type NodeUsageDataDto = Map[String, String]

    object NodeUsageDataDto {

      def apply(nodeUsage: NodeUsageData, isFragment: Boolean): NodeUsageDataDto = {
        val usage: String = if (isFragment) {
          "FragmentUsageData"
        } else {
          "ScenarioUsageData"
        }
        Map.apply("nodeId" -> nodeUsage.nodeId, "type" -> usage)
      }

    }

    @derive(encoder, decoder, schema)
    final case class ProcessActionDto(
        id: String,
        processId: Long,
        processVersionId: Long,
        user: String,
        createdAt: String,
        performedAt: String,
        actionType: String,
        state: String,
        failureMessage: Option[String],
        commentId: Option[Long],
        comment: Option[String],
        buildInfo: Map[String, String]
    )

    object ProcessActionDto {

      def apply(processAction: ProcessAction) =
        new ProcessActionDto(
          id = processAction.id.toString,
          processId = processAction.processId.value,
          processVersionId = processAction.processVersionId.value,
          user = processAction.user,
          createdAt = processAction.createdAt.toString,
          performedAt = processAction.performedAt.toString,
          actionType = processAction.actionType.toString,
          state = processAction.state.toString,
          failureMessage = processAction.failureMessage,
          commentId = processAction.commentId,
          comment = processAction.comment,
          buildInfo = processAction.buildInfo
        )

    }

  }

}
