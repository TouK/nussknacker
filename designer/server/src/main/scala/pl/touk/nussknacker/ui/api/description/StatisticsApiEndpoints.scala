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
      case object SearchScenariosByName                extends StatisticName("f_ssbn")
      case object FilterScenariosByStatus              extends StatisticName("f_fsbs")
      case object FilterScenariosByProcessingMode      extends StatisticName("f_fsbpm")
      case object FilterScenariosByCategory            extends StatisticName("f_fsbc")
      case object FilterScenariosByAuthor              extends StatisticName("f_fsba")
      case object FilterScenariosByOther               extends StatisticName("f_fsbo")
      case object SortScenariosBySortOption            extends StatisticName("f_ssbso")
      case object SearchComponentsByName               extends StatisticName("f_scbn")
      case object FilterComponentsByGroup              extends StatisticName("f_fcbg")
      case object FilterComponentsByProcessingMode     extends StatisticName("f_fcbpm")
      case object FilterComponentsByCategory           extends StatisticName("f_fcbc")
      case object FilterComponentsByMultipleCategories extends StatisticName("f_fcbmc")
      case object FilterComponentsByUsages             extends StatisticName("f_fcbu")
      case object ClickComponentUsages                 extends StatisticName("f_ccu")
      case object SearchComponentUsagesByName          extends StatisticName("f_scubn")
      case object FilterComponentUsagesByStatus        extends StatisticName("f_fcubs")
      case object FilterComponentUsagesByCategory      extends StatisticName("f_fcubc")
      case object FilterComponentUsagesByAuthor        extends StatisticName("f_fcuba")
      case object FilterComponentUsagesByOther         extends StatisticName("f_fcubo")
      case object ClickScenarioFromComponentUsages     extends StatisticName("f_csfcu")
      case object ClickGlobalMetricsTab                extends StatisticName("f_cgmt")
      case object ClickActionDeploy                    extends StatisticName("f_cad")
      case object ClickActionMetrics                   extends StatisticName("f_cam")
      case object ClickViewZoomIn                      extends StatisticName("f_cvzi")
      case object ClickViewZoomOut                     extends StatisticName("f_cvzo")
      case object ClickViewReset                       extends StatisticName("f_cvr")
      case object ClickEditUndo                        extends StatisticName("f_ceu")
      case object ClickEditRedo                        extends StatisticName("f_cer")
      case object ClickEditCopy                        extends StatisticName("f_cec")
      case object ClickEditPaste                       extends StatisticName("f_cep")
      case object ClickEditDelete                      extends StatisticName("f_ced")
      case object ClickEditLayout                      extends StatisticName("f_cel")
      case object ClickScenarioProperties              extends StatisticName("f_csp")
      case object ClickScenarioCompare                 extends StatisticName("f_csco")
      case object ClickScenarioMigrate                 extends StatisticName("f_csm")
      case object ClickScenarioImport                  extends StatisticName("f_csi")
      case object ClickScenarioJson                    extends StatisticName("f_csj")
      case object ClickScenarioPdf                     extends StatisticName("f_cspd")
      case object ClickScenarioArchive                 extends StatisticName("f_csa")
      case object ClickTestGenerated                   extends StatisticName("f_ctg")
      case object ClickTestAdhoc                       extends StatisticName("f_cta")
      case object ClickTestFromFile                    extends StatisticName("f_ctff")
      case object ClickTestGenerateFile                extends StatisticName("f_ctgt")
      case object ClickTestHide                        extends StatisticName("f_cth")
      case object ClickMoreScenarioDetails             extends StatisticName("f_cmsd")
      case object ClickExpandPanel                     extends StatisticName("f_cexp")
      case object ClickCollapsePanel                   extends StatisticName("f_ccp")
      case object MoveToolbarPanel                     extends StatisticName("f_mtp")
      case object SearchNodesInScenario                extends StatisticName("f_snis")
      case object SearchComponentsInScenario           extends StatisticName("f_scis")
      case object ClickOlderVersion                    extends StatisticName("f_cov")
      case object ClickNewerVersion                    extends StatisticName("f_cnv")
      case object ClickNodeDocumentation               extends StatisticName("f_cnd")
      case object ClickComponentsTab                   extends StatisticName("f_cct")
      case object ClickScenarioSave                    extends StatisticName("f_css")
      case object ClickTestCounts                      extends StatisticName("f_ctc")
      case object ClickScenarioCancel                  extends StatisticName("f_csc")
      case object ClickScenarioArchiveToggle           extends StatisticName("f_csat")
      case object ClickScenarioUnarchive               extends StatisticName("f_csu")
      case object ClickScenarioCustomAction            extends StatisticName("f_csca")
      case object ClickScenarioCustomLink              extends StatisticName("f_cscl")
      case object DoubleClickRangeSelectNodes          extends StatisticName("f_dcrsn")
      case object KeyboardAndClickRangeSelectNodes     extends StatisticName("f_kacrsn")
      case object KeyboardCopyNode                     extends StatisticName("f_kcon")
      case object KeyboardPasteNode                    extends StatisticName("f_kpn")
      case object KeyboardCutNode                      extends StatisticName("f_kcn")
      case object KeyboardSelectAllNodes               extends StatisticName("f_ksan")
      case object KeyboardRedoScenarioChanges          extends StatisticName("f_krsc")
      case object KeyboardUndoScenarioChanges          extends StatisticName("f_kusc")
      case object KeyboardDeleteNodes                  extends StatisticName("f_kdn")
      case object KeyboardDeselectAllNodes             extends StatisticName("f_kdan")
      case object KeyboardFocusSearchNodeField         extends StatisticName("f_kfsnf")

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
