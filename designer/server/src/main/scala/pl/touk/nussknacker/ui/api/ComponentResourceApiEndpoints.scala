package pl.touk.nussknacker.ui.api

import derevo.circe.decoder
import derevo.derive
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.restmodel.component.{ComponentListElement, ComponentUsagesInScenario}
import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody

import java.net.URI

class ComponentResourceApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import ComponentResourceApiEndpoints.Dtos._

  val componentsListEndpoint: SecuredEndpoint[Unit, Unit, ComponentsListSuccessfulResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Listing components")
      .tag("Components")
      .withSecurity(auth)
      .get
      .in("components")
      .out(
        statusCode(Ok).and(
          jsonBody[ComponentsListSuccessfulResponseDto]
        )
      )

  val componentUsageEndpoint: SecuredEndpoint[String, String, ComponentUsageSuccessfulResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Show component usage")
      .tag("Components")
      .get
      .in("components" / path[String]("id") / "usages")
      .out(
        statusCode(Ok).and(
          jsonBody[ComponentUsageSuccessfulResponseDto]
        )
      )
      .errorOut(
        statusCode(NotFound).and(
          stringBody
            .example("Component this not exist.")
        )
      )
      .withSecurity(auth)

}

object ComponentResourceApiEndpoints {

  object Dtos {

    implicit val uriSchema: Schema[URI] = Schema.string

    final case class ComponentsListSuccessfulResponseDto(
        components: List[ComponentListElement]
    )

    object ComponentsListSuccessfulResponseDto {

      implicit val responseSchema: Schema[ComponentsListSuccessfulResponseDto] = {
        implicitly[Schema[List[ComponentListElement]]].as
      }

      implicit val circeDecoder: Decoder[ComponentsListSuccessfulResponseDto] = {
        Decoder.instance { c =>
          for {
            list <- c.value.as[List[ComponentListElement]]
          } yield ComponentsListSuccessfulResponseDto(list)
        }
      }

      implicit val circeEncoder: Encoder[ComponentsListSuccessfulResponseDto] = {
        Encoder.encodeJson.contramap { componentList =>
          componentList.components.asJson
        }
      }

    }

    @derive(decoder)
    final case class ComponentUsageSuccessfulResponseDto(
        usages: List[ComponentUsagesInScenario]
    )

    object ComponentUsageSuccessfulResponseDto {

      implicit val componentUsagesSchema: Schema[ComponentUsageSuccessfulResponseDto] =
        implicitly[Schema[List[ComponentUsagesInScenario]]].as

      implicit val circeEncoder: Encoder[ComponentUsageSuccessfulResponseDto] = {
        Encoder.encodeJson.contramap { usagesList =>
          usagesList.usages.asJson
        }
      }

    }

  }

}
