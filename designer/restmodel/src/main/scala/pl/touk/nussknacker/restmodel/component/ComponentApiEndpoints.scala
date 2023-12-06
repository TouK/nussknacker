package pl.touk.nussknacker.restmodel.component

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.component.NodeUsageData.ScenarioUsageData
import pl.touk.nussknacker.security.AuthCredentials
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.EndpointIO.Example

import java.net.URI
import java.time.Instant
import java.util.UUID

class ComponentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import ComponentApiEndpoints.ComponentCodec._

  val componentsListEndpoint: SecuredEndpoint[Unit, Unit, List[ComponentListElement], Any] =
    baseNuApiEndpoint
      .summary("Listing components")
      .tag("Components")
      .withSecurity(auth)
      .get
      .in("components")
      .out(
        statusCode(Ok).and(
          jsonBody[List[ComponentListElement]]
            .example(
              Example.of(
                summary = Some("List of available components"),
                value = List(
                  ComponentListElement(
                    id = ComponentId("request-response-embedded-customnode-collect"),
                    name = "collect",
                    icon = "/assets/components/CustomNde.svg",
                    componentType = ComponentType.CustomNode,
                    componentGroupName = ComponentGroupName("custom"),
                    categories = List("RequestResponse"),
                    links = List(
                      ComponentLink(
                        id = "documentation",
                        title = "Documentation",
                        icon = URI.create("/assets/icons/documentation.svg"),
                        url = URI.create(
                          "https://nussknacker.io/documentation/docs/scenarios_authoring/RRDataSourcesAndSinks/#collect"
                        )
                      )
                    ),
                    usageCount = 2
                  )
                )
              )
            )
        )
      )

  val componentUsageEndpoint: SecuredEndpoint[ComponentId, String, List[ComponentUsagesInScenario], Any] =
    baseNuApiEndpoint
      .summary("Show component usage")
      .tag("Components")
      .get
      .in("components" / path[ComponentId]("id") / "usages")
      .out(
        statusCode(Ok).and(
          jsonBody[List[ComponentUsagesInScenario]]
            .example(
              Example.of(
                summary = Some("List component usages"),
                value = List(
                  ComponentUsagesInScenario(
                    id = "scenario1",
                    name = ProcessName("scenario1"),
                    processId = ProcessId(1),
                    nodesUsagesData = List(
                      ScenarioUsageData("csv-source")
                    ),
                    isFragment = false,
                    processCategory = "Category1",
                    modificationDate = Instant.parse("2023-11-29T08:54:22.520866Z"),
                    modifiedAt = Instant.parse("2023-11-29T08:54:22.520866Z"),
                    modifiedBy = "admin",
                    createdAt = Instant.parse("2023-11-14T11:09:28.078800Z"),
                    createdBy = "admin",
                    lastAction = Some(
                      ProcessAction(
                        id = ProcessActionId(UUID.fromString("45c0f3f5-3ef7-4dc2-92d4-8bb826ec0ca9")),
                        processId = ProcessId(1),
                        processVersionId = VersionId(1),
                        user = "admin",
                        createdAt = Instant.parse("2023-11-29T08:54:22.520866Z"),
                        performedAt = Instant.parse("2023-11-29T08:54:22.520866Z"),
                        actionType = ProcessActionType.Deploy,
                        state = ProcessActionState.Finished,
                        failureMessage = None,
                        commentId = None,
                        comment = None,
                        buildInfo = Map.empty
                      )
                    )
                  )
                )
              )
            )
            .example(
              Example.of(
                summary = Some("List component usages with no last Action"),
                value = List(
                  ComponentUsagesInScenario(
                    id = "scenario1",
                    name = ProcessName("scenario1"),
                    processId = ProcessId(1),
                    nodesUsagesData = List(
                      ScenarioUsageData("csv-source")
                    ),
                    isFragment = false,
                    processCategory = "Category1",
                    modificationDate = Instant.parse("2023-11-29T08:54:22.520866Z"),
                    modifiedAt = Instant.parse("2023-11-29T08:54:22.520866Z"),
                    modifiedBy = "admin",
                    createdAt = Instant.parse("2023-11-14T11:09:28.078800Z"),
                    createdBy = "admin",
                    lastAction = None
                  )
                )
              )
            )
        )
      )
      .errorOut(
        statusCode(NotFound).and(
          stringBody
            .example(s"Component {id} not exist.")
        )
      )
      .withSecurity(auth)

  object ComponentApiEndpoints {

    object ComponentCodec {
      def encode(componentId: ComponentId): String = componentId.value

      def decode(s: String): DecodeResult[ComponentId] = {
        val componentId = ComponentId.apply(s)
        DecodeResult.Value(componentId)
      }

      implicit val componentIdCodec: PlainCodec[ComponentId] = Codec.string.mapDecode(decode)(encode)
    }

  }

}
