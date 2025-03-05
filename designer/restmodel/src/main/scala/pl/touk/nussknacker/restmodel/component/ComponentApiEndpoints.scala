package pl.touk.nussknacker.restmodel.component

import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentType,
  DesignerWideComponentId,
  ProcessingMode
}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.component.NodeUsageData.ScenarioUsageData
import pl.touk.nussknacker.security.AuthCredentials
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir._
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.EndpointIO.Example
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody

import java.net.URI
import java.time.Instant

class ComponentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  implicit val scenarioNameSchema: Schema[ProcessName]              = Schema.string[ProcessName]
  implicit val scenarioIdSchema: Schema[ProcessId]                  = Schema.schemaForLong.as[ProcessId]
  implicit val versionIdSchema: Schema[VersionId]                   = Schema.schemaForLong.as[VersionId]
  implicit val actionIdSchema: Schema[ProcessActionId]              = Schema.schemaForUUID.as[ProcessActionId]
  implicit val componentGroupNameSchema: Schema[ComponentGroupName] = Schema.string[ComponentGroupName]

  import ComponentApiEndpoints.ComponentCodec._

  val componentsListEndpoint
      : SecuredEndpoint[(Option[Boolean], Option[Boolean]), Unit, List[ComponentListElement], Any] =
    baseNuApiEndpoint
      .summary("Listing components")
      .tag("Components")
      .withSecurity(auth)
      .get
      .in("components")
      .in(query[Option[Boolean]]("skipUsages"))
      .in(query[Option[Boolean]]("skipFragments"))
      .out(
        statusCode(Ok).and(
          jsonBody[List[ComponentListElement]]
            .example(
              Example.of(
                summary = Some("List of available components"),
                value = List(
                  ComponentListElement(
                    id = DesignerWideComponentId("request-response-embedded-customnode-collect"),
                    name = "collect",
                    icon = "/assets/components/CustomNode.svg",
                    componentType = ComponentType.CustomComponent,
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
                    usageCount = 2,
                    allowedProcessingModes = AllowedProcessingModes.SetOf(ProcessingMode.RequestResponse)
                  )
                )
              )
            )
        )
      )

  val componentUsageEndpoint: SecuredEndpoint[DesignerWideComponentId, String, List[ComponentUsagesInScenario], Any] =
    baseNuApiEndpoint
      .summary("Show component usage")
      .tag("Components")
      .get
      .in("components" / path[DesignerWideComponentId]("id") / "usages")
      .out(
        statusCode(Ok).and(
          jsonBody[List[ComponentUsagesInScenario]]
            .example(
              Example.of(
                summary = Some("List component usages"),
                value = List(
                  ComponentUsagesInScenario(
                    name = ProcessName("scenario1"),
                    nodesUsagesData = List(
                      ScenarioUsageData("csv-source")
                    ),
                    isFragment = false,
                    processCategory = "Category1",
                    modificationDate = Instant.parse("2023-11-29T08:54:22.520866Z"),
                    modifiedAt = Instant.parse("2023-11-29T08:54:22.520866Z"),
                    modifiedBy = "admin",
                    createdAt = Instant.parse("2023-11-14T11:09:28.078800Z"),
                    createdBy = "admin"
                  )
                )
              )
            )
            .example(
              Example.of(
                summary = Some("List component usages with no last Action"),
                value = List(
                  ComponentUsagesInScenario(
                    name = ProcessName("scenario1"),
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
      def encode(determineDesignerWideId: DesignerWideComponentId): String = determineDesignerWideId.value

      def decode(s: String): DecodeResult[DesignerWideComponentId] = {
        val componentId = DesignerWideComponentId.apply(s)
        DecodeResult.Value(componentId)
      }

      implicit val componentIdCodec: PlainCodec[DesignerWideComponentId] = Codec.string.mapDecode(decode)(encode)
    }

  }

}
