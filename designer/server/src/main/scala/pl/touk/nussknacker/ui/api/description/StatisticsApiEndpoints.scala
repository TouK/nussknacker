package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.{CirceEnum, Enum, EnumEntry}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs
import sttp.model.StatusCode.{InternalServerError, NoContent, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.codec.enumeratum._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.net.{URI, URL}

class StatisticsApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import StatisticsApiEndpoints.Dtos._

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
                        "https://stats.nussknacker.io/?a_n=1&a_t=0&a_v=0&c=3&c_n=82&c_t=0&c_v=0&f_m=0&f_v=0&" +
                          "fingerprint=development&n_m=2&n_ma=0&n_mi=2&n_v=1&s_a=0&s_dm_c=1&s_dm_e=1&s_dm_f=2&" +
                          "s_dm_l=0&s_f=1&s_pm_b=0&s_pm_rr=1&s_pm_s=3&s_s=3&source=sources&u_ma=0&u_mi=0&u_v=0&v_m=2&" +
                          "v_ma=1&v_mi=3&v_v=2&version=1.15.0-SNAPSHOT"
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
            plainBody[StatisticError]
              .example(
                Example.of(
                  summary = Some("Statistics generation failed."),
                  value = CannotGenerateStatisticError
                )
              )
          )
        )
      )
      .withSecurity(auth)

  lazy val registerStatisticsEndpoint: SecuredEndpoint[RegisterStatisticsRequestDto, Unit, Unit, Any] =
    baseNuApiEndpoint
      .summary("Register statistics service")
      .tag("Statistics")
      .post
      .in("statistic" and jsonBody[RegisterStatisticsRequestDto])
      .out(statusCode(NoContent))
      .withSecurity(auth)

}

object StatisticsApiEndpoints {

  object Dtos {
    import TapirCodecs.URLCodec._

    @derive(encoder, decoder, schema)
    final case class StatisticUrlResponseDto private (urls: List[URL])

    @derive(encoder, decoder, schema)
    final case class RegisterStatisticsRequestDto private (statistics: List[StatisticDto])

    @derive(encoder, decoder, schema)
    final case class StatisticDto private (name: StatisticName, value: Long)

    sealed trait StatisticName extends EnumEntry with UpperSnakecase

    object StatisticName extends Enum[StatisticName] with CirceEnum[StatisticName] {
      case object ScenarioFilter                         extends StatisticName
      case object SearchScenario                         extends StatisticName
      case object ComponentsList                         extends StatisticName
      case object SearchComponents                       extends StatisticName
      case object SearchComponentsWithGroup              extends StatisticName
      case object SearchComponentsWithProcessingMode     extends StatisticName
      case object SearchComponentsWithCategory           extends StatisticName
      case object SearchComponentsWithMultipleCategories extends StatisticName
      case object SearchComponentsWithUsages             extends StatisticName
      case object SearchComponentsUsagesList             extends StatisticName
      case object SearchComponentsUsagesListWithStatus   extends StatisticName
      case object SearchComponentsUsagesListWithCategory extends StatisticName
      case object SearchComponentsUsagesListWithAuthor   extends StatisticName
      case object SearchComponentsUsagesListWithOther    extends StatisticName
      case object GoToScenarioFromComponentsUsagesList   extends StatisticName
      case object GlobalMetrics                          extends StatisticName
      case object ActionDeploy                           extends StatisticName
      case object ActionMetrics                          extends StatisticName
      case object ViewZoomIn                             extends StatisticName
      case object ViewReset                              extends StatisticName
      case object EditUndo                               extends StatisticName
      case object EditRedo                               extends StatisticName
      case object EditCopy                               extends StatisticName
      case object EditPaste                              extends StatisticName
      case object EditDelete                             extends StatisticName
      case object EditLayout                             extends StatisticName
      case object ScenarioProperties                     extends StatisticName
      case object ScenarioCompare                        extends StatisticName
      case object ScenarioMigrate                        extends StatisticName
      case object ScenarioImport                         extends StatisticName
      case object ScenarioJson                           extends StatisticName
      case object ScenarioPdf                            extends StatisticName
      case object ScenarioArchive                        extends StatisticName
      case object TestGenerated                          extends StatisticName
      case object TestAdhoc                              extends StatisticName
      case object TestFromFile                           extends StatisticName
      case object TestGenerateFile                       extends StatisticName
      case object TestHide                               extends StatisticName
      case object MoreScenarioDetails                    extends StatisticName
      case object RollUpPanel                            extends StatisticName
      case object ExpandPanel                            extends StatisticName
      case object MovePanel                              extends StatisticName
      case object SearchNodesInScenario                  extends StatisticName
      case object SearchComponentsInScenario             extends StatisticName
      case object SwitchToOlderVersion                   extends StatisticName
      case object SwitchToNewerVersion                   extends StatisticName
      case object FiredKeyStroke                         extends StatisticName
      case object NodeDocumentation                      extends StatisticName

      override def values = findValues
    }

    sealed trait StatisticError
    final case object CannotGenerateStatisticError extends StatisticError

    object StatisticError {

      private def deserializationException =
        (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

      implicit val errorCodec: Codec[String, StatisticError, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, StatisticError](deserializationException)(_ => "Statistics generation failed.")
        )
      }

    }

  }

}
