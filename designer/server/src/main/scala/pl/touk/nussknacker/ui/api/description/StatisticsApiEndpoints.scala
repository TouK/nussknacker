package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs
import sttp.model.StatusCode.{InternalServerError, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody
import sttp.tapir._

import java.net.{URI, URL}

class StatisticsApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import StatisticsApiEndpoints.Dtos._
  import StatisticsApiEndpoints.Dtos.StatisticError._

  lazy val statisticUsageEndpoint: SecuredEndpoint[Unit, StatisticError, StatisticUrlResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Statistics URL service")
      .tag("Statistics")
      .get
      .in("statistic" / "usage")
      .out(
        statusCode(Ok).and(
          jsonBody[StatisticUrlResponseDto]
            .example(
              Example.of(
                summary = Some("List of statistics URLs"),
                value = StatisticUrlResponseDto(
                  List(
                    URI
                      .create(
                        "https://stats.nussknacker.io/?fingerprint=development&s_a=0&s_dm_c=0&s_dm_e=0&s_dm_f=" +
                          "1&s_dm_l=0&s_f=0&s_pm_b=0&s_pm_rr=0&s_pm_s=1&s_s=1&source=sources&version=1.15.0-SNAPSHOT"
                      )
                      .toURL
                  )
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[StatisticError](
          oneOfVariantFromMatchType(
            InternalServerError,
            plainBody[InvalidURL.type]
              .example(
                Example.of(
                  summary = Some("Constructed URL is invalid."),
                  value = InvalidURL
                )
              )
          )
        )
      )
      .withSecurity(auth)

}

object StatisticsApiEndpoints {

  object Dtos {
    import TapirCodecs.URLCodec._

    @derive(encoder, decoder, schema)
    final case class StatisticUrlResponseDto private (urls: List[URL])

    sealed trait StatisticError

    object StatisticError {
      final case object InvalidURL extends StatisticError

      private def deserializationException =
        (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

      implicit val invalidURLCodec: Codec[String, InvalidURL.type, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, InvalidURL.type](deserializationException)(e => "Constructed URL is invalid.")
        )
      }

    }

  }

}
