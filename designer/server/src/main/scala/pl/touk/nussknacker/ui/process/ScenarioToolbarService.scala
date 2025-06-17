package pl.touk.nussknacker.ui.process

import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.ui.config.scenariotoolbar._
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.{ImpersonatedUser, LoggedUser, RealLoggedUser}

trait ScenarioToolbarService {

  def getScenarioToolbarSettings(scenario: ScenarioWithDetailsEntity[_])(
      implicit loggedUser: LoggedUser
  ): ScenarioToolbarSettings

}

class ConfigScenarioToolbarService(categoriesScenarioToolbarConfig: CategoriesScenarioToolbarsConfig)
    extends ScenarioToolbarService {

  override def getScenarioToolbarSettings(
      scenario: ScenarioWithDetailsEntity[_]
  )(implicit loggedUser: LoggedUser): ScenarioToolbarSettings = {
    val toolbarConfig = categoriesScenarioToolbarConfig.getConfig(scenario.processCategory)
    ScenarioToolbarSettings.fromConfig(toolbarConfig, scenario)
  }

}

object ScenarioToolbarSettings {

  import ToolbarHelper._

  def fromConfig(
      scenarioToolbarConfig: ScenarioToolbarsConfig,
      scenario: ScenarioWithDetailsEntity[_],
  )(implicit loggedUser: LoggedUser): ScenarioToolbarSettings = {
    val id = createScenarioToolbarId(scenarioToolbarConfig, scenario)
    val user = loggedUser match {
      case realLoggedUser: RealLoggedUser        => realLoggedUser
      case ImpersonatedUser(impersonatedUser, _) => impersonatedUser
    }
    new ScenarioToolbarSettings(
      id,
      scenarioToolbarConfig.panelAreasConfig.toList.flatMap { case (panelAreaName, panels) =>
        Option(
          panels
            .filterNot(panel => verifyCondition(panel.hidden, scenario, user))
            .map(ToolbarPanel.fromConfig(_, scenario, user))
        ).filterNot(_.isEmpty).map(panelAreaName -> _)
      }.toMap
    )
  }

}

final class ScenarioToolbarSettings(id: String, panelAreas: Map[String, List[ToolbarPanel]]) {
  def asJson: Json = panelAreas.asJson.asObject.get.add("id", Json.fromString(id)).toJson
}

object ToolbarPanel {

  import ToolbarHelper._

  def fromConfig(
      config: ToolbarPanelConfig,
      scenario: ScenarioWithDetailsEntity[_],
      user: RealLoggedUser,
  ): ToolbarPanel =
    ToolbarPanel(
      config.identity,
      config.title.map(t => fillByScenarioData(t, scenario)),
      config.buttonsVariant,
      config.buttons.map(buttons =>
        buttons
          .filterNot(button => verifyCondition(button.hidden, scenario, user))
          .map(button => ToolbarButton.fromConfig(button, scenario, user))
      ),
      config.additionalParams
    )

}

@JsonCodec
final case class ToolbarPanel(
    id: String,
    title: Option[String],
    buttonsVariant: Option[String],
    buttons: Option[List[ToolbarButton]],
    additionalParams: Option[Map[String, String]]
)

object ToolbarButton {

  import ToolbarHelper._

  def fromConfig(
      config: ToolbarButtonConfig,
      scenario: ScenarioWithDetailsEntity[_],
      user: RealLoggedUser,
  ): ToolbarButton = {
    val (title, titleOverride) = config.customTitleForUserRole match {
      case Some(titleForUserRole) =>
        user.roles.collectFirst { case role if titleForUserRole.contains(role) => titleForUserRole(role) } match {
          case Some(title) => (Some(title), true)
          case None        => (config.title, false)
        }
      case None =>
        (config.title, false)
    }
    ToolbarButton(
      config.`type`,
      config.name.map(t => fillByScenarioData(t, scenario)),
      title.map(t => fillByScenarioData(t, scenario)),
      titleOverride,
      config.icon.map(i => fillByScenarioData(i, scenario, urlOption = true)),
      config.url.map(th => fillByScenarioData(th, scenario, urlOption = true)),
      disabled = verifyCondition(config.disabled, scenario, user),
      config.markdownContent.map(mc => fillByScenarioData(mc, scenario)),
      config.docs.map(d =>
        DocsButtonConfig(
          fillByScenarioData(d.url, scenario, urlOption = true),
          d.label.map(fillByScenarioData(_, scenario))
        )
      ),
    )
  }

}

@JsonCodec
final case class ToolbarButton(
    `type`: String,
    name: Option[String],
    title: Option[String],
    titleOverride: Boolean,
    icon: Option[String],
    url: Option[String],
    disabled: Boolean,
    markdownContent: Option[String] = None,
    docs: Option[DocsButtonConfig] = None
)

@JsonCodec
final case class DocsButtonConfig(
    url: String,
    label: Option[String]
)

private[process] object ToolbarHelper {

  def createScenarioToolbarId(config: ScenarioToolbarsConfig, scenario: ScenarioWithDetailsEntity[_]): String = {
    s"${config.uuidCode}-${if (scenario.isArchived) "archived" else "not-archived"}-${if (scenario.isFragment) "fragment"
      else "scenario"}"
  }

  def fillByScenarioData(text: String, scenario: ScenarioWithDetailsEntity[_], urlOption: Boolean = false): String = {
    val scenarioName = if (urlOption) UriUtils.encodeURIComponent(scenario.name.value) else scenario.name.value

    text
      .replace("$processName", scenarioName)
      .replace("$processId", scenario.processId.value.toString)
  }

  def verifyCondition(
      condition: Option[ToolbarCondition],
      scenario: ScenarioWithDetailsEntity[_],
      user: RealLoggedUser,
  ): Boolean = {
    condition.nonEmpty && condition.exists(con => {
      if (con.shouldMatchAllOfConditions) {
        verifyFragmentCondition(con, scenario) &&
        verifyArchivedCondition(con, scenario) &&
        verifyAlwaysCondition(con) &&
        verifyUserRolesCondition(con, user)
      } else {
        verifyFragmentCondition(con, scenario) ||
        verifyArchivedCondition(con, scenario) ||
        verifyAlwaysCondition(con) ||
        verifyUserRolesCondition(con, user)
      }
    })
  }

  private def verifyFragmentCondition(condition: ToolbarCondition, scenario: ScenarioWithDetailsEntity[_]) =
    verifyCondition(scenario.isFragment, condition.fragment, condition.shouldMatchAllOfConditions)

  private def verifyArchivedCondition(condition: ToolbarCondition, scenario: ScenarioWithDetailsEntity[_]) =
    verifyCondition(scenario.isArchived, condition.archived, condition.shouldMatchAllOfConditions)

  private def verifyAlwaysCondition(condition: ToolbarCondition) =
    (condition.shouldMatchAllOfConditions && condition.always.isEmpty) || condition.always.contains(true)

  private def verifyUserRolesCondition(condition: ToolbarCondition, loggedUser: RealLoggedUser) =
    (condition.shouldMatchAllOfConditions && condition.oneOfUserRoles.isEmpty) ||
      condition.oneOfUserRoles.getOrElse(Set.empty).intersect(loggedUser.roles).nonEmpty

  // When we should match all conditions and expected condition is empty (not set) then we ignore this condition
  private def verifyCondition(
      toVerify: Boolean,
      expected: Option[Boolean],
      shouldMatchAllOfConditions: Boolean
  ): Boolean =
    (shouldMatchAllOfConditions && expected.isEmpty) || expected.contains(toVerify)

}
