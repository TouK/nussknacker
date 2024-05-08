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
    final case class StatisticDto private (name: StatisticName)

    sealed trait StatisticName extends EnumEntry with UpperSnakecase

    object StatisticName extends Enum[StatisticName] with CirceEnum[StatisticName] {
      case object SearchScenariosByName                extends StatisticName
      case object FilterScenariosByStatus              extends StatisticName
      case object FilterScenariosByProcessingMode      extends StatisticName
      case object FilterScenariosByCategory            extends StatisticName
      case object FilterScenariosByAuthor              extends StatisticName
      case object FilterScenariosByOther               extends StatisticName
      case object SortScenarios                        extends StatisticName
      case object SearchComponentsByName               extends StatisticName
      case object FilterComponentsByGroup              extends StatisticName
      case object FilterComponentsByProcessingMode     extends StatisticName
      case object FilterComponentsByCategory           extends StatisticName
      case object FilterComponentsByMultipleCategories extends StatisticName
      case object FilterComponentsByUsages             extends StatisticName
      case object ClickComponentUsages                 extends StatisticName
      case object SearchComponentUsagesByName          extends StatisticName
      case object FilterComponentUsagesByStatus        extends StatisticName
      case object FilterComponentUsagesByCategory      extends StatisticName
      case object FilterComponentUsagesByAuthor        extends StatisticName
      case object FilterComponentUsagesByOther         extends StatisticName
      case object ClickScenarioFromComponentUsages     extends StatisticName
      case object ClickGlobalMetrics                   extends StatisticName
      case object ClickActionDeploy                    extends StatisticName
      case object ClickActionMetrics                   extends StatisticName
      case object ClickViewZoomIn                      extends StatisticName
      case object ClickViewReset                       extends StatisticName
      case object ClickEditUndo                        extends StatisticName
      case object ClickEditRedo                        extends StatisticName
      case object ClickEditCopy                        extends StatisticName
      case object ClickEditPaste                       extends StatisticName
      case object ClickEditDelete                      extends StatisticName
      case object ClickEditLayout                      extends StatisticName
      case object ClickScenarioProperties              extends StatisticName
      case object ClickScenarioCompare                 extends StatisticName
      case object ClickScenarioMigrate                 extends StatisticName
      case object ClickScenarioImport                  extends StatisticName
      case object ClickScenarioJson                    extends StatisticName
      case object ClickScenarioPdf                     extends StatisticName
      case object ClickScenarioArchive                 extends StatisticName
      case object ClickTestGenerated                   extends StatisticName
      case object ClickTestAdhoc                       extends StatisticName
      case object ClickTestFromFile                    extends StatisticName
      case object ClickTestGenerateFile                extends StatisticName
      case object ClickTestHide                        extends StatisticName
      case object ClickMoreScenarioDetails             extends StatisticName
      case object ClickRollUpPanel                     extends StatisticName
      case object ClickExpandPanel                     extends StatisticName
      case object MovePanel                            extends StatisticName
      case object SearchNodesInScenario                extends StatisticName
      case object SearchComponentsInScenario           extends StatisticName
      case object ClickOlderVersion                    extends StatisticName
      case object ClickNewerVersion                    extends StatisticName
      case object FiredKeyStroke                       extends StatisticName
      case object ClickNodeDocumentation               extends StatisticName

      override def values = findValues
    }

    sealed trait StatisticError
    final case object CannotGenerateStatisticError extends StatisticError

    object StatisticError {

      implicit val errorCodec: Codec[String, StatisticError, CodecFormat.TextPlain] =
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[StatisticError](_ => "Statistics generation failed.")

    }

  }

}
