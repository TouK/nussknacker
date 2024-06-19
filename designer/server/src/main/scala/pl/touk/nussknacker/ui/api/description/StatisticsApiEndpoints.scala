package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
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

    sealed abstract class StatisticName(val value: String, val shortName: String)
        extends StringEnumEntry
        with UpperSnakecase

    object StatisticName extends StringEnum[StatisticName] with StringCirceEnum[StatisticName] {

      case object SearchScenariosByName   extends StatisticName("SEARCH_SCENARIOS_BY_NAME", "f_ssbn")
      case object FilterScenariosByStatus extends StatisticName("FILTER_SCENARIOS_BY_STATUS", "f_fsbs")
      case object FilterScenariosByProcessingMode
          extends StatisticName("FILTER_SCENARIOS_BY_PROCESSING_MODE", "f_fsbpm")
      case object FilterScenariosByCategory extends StatisticName("FILTER_SCENARIOS_BY_CATEGORY", "f_fsbc")
      case object FilterScenariosByAuthor   extends StatisticName("FILTER_SCENARIOS_BY_AUTHOR", "f_fsba")
      case object FilterScenariosByOther    extends StatisticName("FILTER_SCENARIOS_BY_OTHER", "f_fsbo")
      case object SortScenariosBySortOption extends StatisticName("SORT_SCENARIOS_BY_SORT_OPTION", "f_ssbso")
      case object SearchComponentsByName    extends StatisticName("SEARCH_COMPONENTS_BY_NAME", "f_scbn")
      case object FilterComponentsByGroup   extends StatisticName("FILTER_COMPONENTS_BY_GROUP", "f_fcbg")
      case object FilterComponentsByProcessingMode
          extends StatisticName("FILTER_COMPONENTS_BY_PROCESSING_MODE", "f_fcbpm")
      case object FilterComponentsByCategory extends StatisticName("FILTER_COMPONENTS_BY_CATEGORY", "f_fcbc")
      case object FilterComponentsByMultipleCategories
          extends StatisticName("FILTER_COMPONENTS_BY_MULTIPLE_CATEGORIES", "f_fcbmc")
      case object FilterComponentsByUsages      extends StatisticName("FILTER_COMPONENTS_BY_USAGES", "f_fcbu")
      case object ClickComponentUsages          extends StatisticName("CLICK_COMPONENT_USAGES", "f_ccu")
      case object SearchComponentUsagesByName   extends StatisticName("SEARCH_COMPONENT_USAGES_BY_NAME", "f_scubn")
      case object FilterComponentUsagesByStatus extends StatisticName("FILTER_COMPONENT_USAGES_BY_STATUS", "f_fcubs")
      case object FilterComponentUsagesByCategory
          extends StatisticName("FILTER_COMPONENT_USAGES_BY_CATEGORY", "f_fcubc")
      case object FilterComponentUsagesByAuthor extends StatisticName("FILTER_COMPONENT_USAGES_BY_AUTHOR", "f_fcuba")
      case object FilterComponentUsagesByOther  extends StatisticName("FILTER_COMPONENT_USAGES_BY_OTHER", "f_fcubo")
      case object ClickScenarioFromComponentUsages
          extends StatisticName("CLICK_SCENARIO_FROM_COMPONENT_USAGES", "f_csfcu")
      case object ClickGlobalMetricsTab       extends StatisticName("CLICK_GLOBAL_METRICS_TAB", "f_cgmt")
      case object ClickActionDeploy           extends StatisticName("CLICK_ACTION_DEPLOY", "f_cad")
      case object ClickActionMetrics          extends StatisticName("CLICK_ACTION_METRICS", "f_cam")
      case object ClickViewZoomIn             extends StatisticName("CLICK_VIEW_ZOOM_IN", "f_cvzi")
      case object ClickViewZoomOut            extends StatisticName("CLICK_VIEW_ZOOM_OUT", "f_cvzo")
      case object ClickViewReset              extends StatisticName("CLICK_VIEW_RESET", "f_cvr")
      case object ClickEditUndo               extends StatisticName("CLICK_EDIT_UNDO", "f_ceu")
      case object ClickEditRedo               extends StatisticName("CLICK_EDIT_REDO", "f_cer")
      case object ClickEditCopy               extends StatisticName("CLICK_EDIT_COPY", "f_cec")
      case object ClickEditPaste              extends StatisticName("CLICK_EDIT_PASTE", "f_cep")
      case object ClickEditDelete             extends StatisticName("CLICK_EDIT_DELETE", "f_ced")
      case object ClickEditLayout             extends StatisticName("CLICK_EDIT_LAYOUT", "f_cel")
      case object ClickScenarioProperties     extends StatisticName("CLICK_SCENARIO_PROPERTIES", "f_csp")
      case object ClickScenarioCompare        extends StatisticName("CLICK_SCENARIO_COMPARE", "f_csco")
      case object ClickScenarioMigrate        extends StatisticName("CLICK_SCENARIO_MIGRATE", "f_csm")
      case object ClickScenarioImport         extends StatisticName("CLICK_SCENARIO_IMPORT", "f_csi")
      case object ClickScenarioJson           extends StatisticName("CLICK_SCENARIO_JSON", "f_csj")
      case object ClickScenarioPdf            extends StatisticName("CLICK_SCENARIO_PDF", "f_cspd")
      case object ClickScenarioArchive        extends StatisticName("CLICK_SCENARIO_ARCHIVE", "f_csa")
      case object ClickTestGenerated          extends StatisticName("CLICK_TEST_GENERATED", "f_ctg")
      case object ClickTestAdhoc              extends StatisticName("CLICK_TEST_ADHOC", "f_cta")
      case object ClickTestFromFile           extends StatisticName("CLICK_TEST_FROM_FILE", "f_ctff")
      case object ClickTestGenerateFile       extends StatisticName("CLICK_TEST_GENERATE_FILE", "f_ctgt")
      case object ClickTestHide               extends StatisticName("CLICK_TEST_HIDE", "f_cth")
      case object ClickMoreScenarioDetails    extends StatisticName("CLICK_MORE_SCENARIO_DETAILS", "f_cmsd")
      case object ClickExpandPanel            extends StatisticName("CLICK_EXPAND_PANEL", "f_cexp")
      case object ClickCollapsePanel          extends StatisticName("CLICK_COLLAPSE_PANEL", "f_ccp")
      case object MoveToolbarPanel            extends StatisticName("MOVE_TOOLBAR_PANEL", "f_mtp")
      case object SearchNodesInScenario       extends StatisticName("SEARCH_NODES_IN_SCENARIO", "f_snis")
      case object SearchComponentsInScenario  extends StatisticName("SEARCH_COMPONENTS_IN_SCENARIO", "f_scis")
      case object ClickOlderVersion           extends StatisticName("CLICK_OLDER_VERSION", "f_cov")
      case object ClickNewerVersion           extends StatisticName("CLICK_NEWER_VERSION", "f_cnv")
      case object ClickNodeDocumentation      extends StatisticName("CLICK_NODE_DOCUMENTATION", "f_cnd")
      case object ClickComponentsTab          extends StatisticName("CLICK_COMPONENTS_TAB", "f_cct")
      case object ClickScenarioSave           extends StatisticName("CLICK_SCENARIO_SAVE", "f_css")
      case object ClickTestCounts             extends StatisticName("CLICK_TEST_COUNTS", "f_ctc")
      case object ClickScenarioCancel         extends StatisticName("CLICK_SCENARIO_CANCEL", "f_csc")
      case object ClickScenarioArchiveToggle  extends StatisticName("CLICK_SCENARIO_ARCHIVE_TOGGLE", "f_csat")
      case object ClickScenarioUnarchive      extends StatisticName("CLICK_SCENARIO_UNARCHIVE", "f_csu")
      case object ClickScenarioCustomAction   extends StatisticName("CLICK_SCENARIO_CUSTOM_ACTION", "f_csca")
      case object ClickScenarioCustomLink     extends StatisticName("CLICK_SCENARIO_CUSTOM_LINK", "f_cscl")
      case object DoubleClickRangeSelectNodes extends StatisticName("DOUBLE_CLICK_RANGE_SELECT_NODES", "f_dcrsn")
      case object KeyboardAndClickRangeSelectNodes
          extends StatisticName("KEYBOARD_AND_CLICK_RANGE_SELECT_NODES", "f_kacrsn")
      case object KeyboardCopyNode             extends StatisticName("KEYBOARD_COPY_NODE", "f_kcon")
      case object KeyboardPasteNode            extends StatisticName("KEYBOARD_PASTE_NODE", "f_kpn")
      case object KeyboardCutNode              extends StatisticName("KEYBOARD_CUT_NODE", "f_kcn")
      case object KeyboardSelectAllNodes       extends StatisticName("KEYBOARD_SELECT_ALL_NODES", "f_ksan")
      case object KeyboardRedoScenarioChanges  extends StatisticName("KEYBOARD_REDO_SCENARIO_CHANGES", "f_krsc")
      case object KeyboardUndoScenarioChanges  extends StatisticName("KEYBOARD_UNDO_SCENARIO_CHANGES", "f_kusc")
      case object KeyboardDeleteNodes          extends StatisticName("KEYBOARD_DELETE_NODES", "f_kdn")
      case object KeyboardDeselectAllNodes     extends StatisticName("KEYBOARD_DESELECT_ALL_NODES", "f_kdan")
      case object KeyboardFocusSearchNodeField extends StatisticName("KEYBOARD_FOCUS_SEARCH_NODE_FIELD", "f_kfsnf")

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
