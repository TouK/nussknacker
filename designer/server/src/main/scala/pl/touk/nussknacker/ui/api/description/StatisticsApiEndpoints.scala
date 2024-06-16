package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
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

    sealed abstract class StatisticName(override val entryName: String) extends EnumEntry

    object StatisticName extends Enum[StatisticName] with CirceEnum[StatisticName] {
      case object SearchScenariosByName                extends StatisticName("ssbn")
      case object FilterScenariosByStatus              extends StatisticName("fsbs")
      case object FilterScenariosByProcessingMode      extends StatisticName("fsbpm")
      case object FilterScenariosByCategory            extends StatisticName("fsbc")
      case object FilterScenariosByAuthor              extends StatisticName("fsba")
      case object FilterScenariosByOther               extends StatisticName("fsbo")
      case object SortScenariosBySortOption            extends StatisticName("ssbso")
      case object SearchComponentsByName               extends StatisticName("scbn")
      case object FilterComponentsByGroup              extends StatisticName("fcbg")
      case object FilterComponentsByProcessingMode     extends StatisticName("fcbpm")
      case object FilterComponentsByCategory           extends StatisticName("fcbc")
      case object FilterComponentsByMultipleCategories extends StatisticName("fcbmc")
      case object FilterComponentsByUsages             extends StatisticName("fcbu")
      case object ClickComponentUsages                 extends StatisticName("ccu")
      case object SearchComponentUsagesByName          extends StatisticName("scubn")
      case object FilterComponentUsagesByStatus        extends StatisticName("fcubs")
      case object FilterComponentUsagesByCategory      extends StatisticName("fcubc")
      case object FilterComponentUsagesByAuthor        extends StatisticName("fcuba")
      case object FilterComponentUsagesByOther         extends StatisticName("fcubo")
      case object ClickScenarioFromComponentUsages     extends StatisticName("csfcu")
      case object ClickGlobalMetricsTab                extends StatisticName("cgmt")
      case object ClickActionDeploy                    extends StatisticName("cad")
      case object ClickActionMetrics                   extends StatisticName("cam")
      case object ClickViewZoomIn                      extends StatisticName("cvzi")
      case object ClickViewZoomOut                     extends StatisticName("cvzo")
      case object ClickViewReset                       extends StatisticName("cvr")
      case object ClickEditUndo                        extends StatisticName("ceu")
      case object ClickEditRedo                        extends StatisticName("cer")
      case object ClickEditCopy                        extends StatisticName("cec")
      case object ClickEditPaste                       extends StatisticName("cep")
      case object ClickEditDelete                      extends StatisticName("ced")
      case object ClickEditLayout                      extends StatisticName("cel")
      case object ClickScenarioProperties              extends StatisticName("csp")
      case object ClickScenarioCompare                 extends StatisticName("csco")
      case object ClickScenarioMigrate                 extends StatisticName("csm")
      case object ClickScenarioImport                  extends StatisticName("csi")
      case object ClickScenarioJson                    extends StatisticName("csj")
      case object ClickScenarioPdf                     extends StatisticName("cspd")
      case object ClickScenarioArchive                 extends StatisticName("csa")
      case object ClickTestGenerated                   extends StatisticName("ctg")
      case object ClickTestAdhoc                       extends StatisticName("cta")
      case object ClickTestFromFile                    extends StatisticName("ctff")
      case object ClickTestGenerateFile                extends StatisticName("ctgt")
      case object ClickTestHide                        extends StatisticName("cth")
      case object ClickMoreScenarioDetails             extends StatisticName("cmsd")
      case object ClickExpandPanel                     extends StatisticName("cexp")
      case object ClickCollapsePanel                   extends StatisticName("ccp")
      case object MoveToolbarPanel                     extends StatisticName("mtp")
      case object SearchNodesInScenario                extends StatisticName("snis")
      case object SearchComponentsInScenario           extends StatisticName("scis")
      case object ClickOlderVersion                    extends StatisticName("cov")
      case object ClickNewerVersion                    extends StatisticName("cnv")
      case object ClickNodeDocumentation               extends StatisticName("cnd")
      case object ClickComponentsTab                   extends StatisticName("cct")
      case object ClickScenarioSave                    extends StatisticName("css")
      case object ClickTestCounts                      extends StatisticName("ctc")
      case object ClickScenarioCancel                  extends StatisticName("csc")
      case object ClickScenarioArchiveToggle           extends StatisticName("csat")
      case object ClickScenarioUnarchive               extends StatisticName("csu")
      case object ClickScenarioCustomAction            extends StatisticName("csca")
      case object ClickScenarioCustomLink              extends StatisticName("cscl")
      case object DoubleClickRangeSelectNodes          extends StatisticName("dcrsn")
      case object KeyboardAndClickRangeSelectNodes     extends StatisticName("kacrsn")
      case object KeyboardCopyNode                     extends StatisticName("kcon")
      case object KeyboardPasteNode                    extends StatisticName("kpn")
      case object KeyboardCutNode                      extends StatisticName("kcn")
      case object KeyboardSelectAllNodes               extends StatisticName("ksan")
      case object KeyboardRedoScenarioChanges          extends StatisticName("krsc")
      case object KeyboardUndoScenarioChanges          extends StatisticName("kusc")
      case object KeyboardDeleteNodes                  extends StatisticName("kdn")
      case object KeyboardDeselectAllNodes             extends StatisticName("kdan")
      case object KeyboardFocusSearchNodeField         extends StatisticName("kfsnf")

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
