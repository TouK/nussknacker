package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import sttp.model.StatusCode.Ok
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{PublicEndpoint, statusCode}

class StatisticsApiEndpoints extends BaseEndpointDefinitions {

  import StatisticsApiEndpoints.Dtos._

  lazy val statisticUrlEndpoint: PublicEndpoint[Unit, Unit, StatisticUrlResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Statistics URL service")
      .tag("Statistics")
      .get
      .in("statistic" / "url")
      .out(
        statusCode(Ok).and(
          jsonBody[StatisticUrlResponseDto]
            .example(
              Example.of(
                summary = Some("List of statistics URLs"),
                value = StatisticUrlResponseDto(
                  List(
                    "https://stats.nussknacker.io/?fingerprint=development&s_a=0&s_dm_c=0&s_dm_e=0&s_dm_f=1&" +
                      "s_dm_l=0&s_f=0&s_pm_b=0&s_pm_rr=0&s_pm_s=1&s_s=1&source=sources&version=1.15.0-SNAPSHOT"
                  )
                )
              )
            )
        )
      )

}

object StatisticsApiEndpoints {

  object Dtos {
    @derive(encoder, decoder, schema)
    final case class StatisticUrlResponseDto private (urls: List[String])
  }

}
