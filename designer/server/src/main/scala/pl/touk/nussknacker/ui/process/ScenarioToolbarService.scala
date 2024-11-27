package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarButtonConfigType.ToolbarButtonType
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarButtonsConfigVariant.ToolbarButtonVariant
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarPanelTypeConfig.ToolbarPanelType
import pl.touk.nussknacker.ui.config.scenariotoolbar._
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

trait ScenarioToolbarService {
  def getScenarioToolbarSettings(scenario: ScenarioWithDetailsEntity[_]): ScenarioToolbarSettings
}

class ConfigScenarioToolbarService(categoriesScenarioToolbarConfig: CategoriesScenarioToolbarsConfig)
    extends ScenarioToolbarService {

  override def getScenarioToolbarSettings(scenario: ScenarioWithDetailsEntity[_]): ScenarioToolbarSettings = {
    val toolbarConfig = categoriesScenarioToolbarConfig.getConfig(scenario.processCategory)
    ScenarioToolbarSettings.fromConfig(toolbarConfig, scenario)
  }

}

object ScenarioToolbarSettings {

  import ToolbarHelper._

  def fromConfig(
      scenarioToolbarConfig: ScenarioToolbarsConfig,
      scenario: ScenarioWithDetailsEntity[_]
  ): ScenarioToolbarSettings =
    ScenarioToolbarSettings(
      createScenarioToolbarId(scenarioToolbarConfig, scenario),
      scenarioToolbarConfig.topLeft
        .filterNot(tp => verifyCondition(tp.hidden, scenario))
        .map(tp => ToolbarPanel.fromConfig(tp, scenario)),
      scenarioToolbarConfig.bottomLeft
        .filterNot(tp => verifyCondition(tp.hidden, scenario))
        .map(tp => ToolbarPanel.fromConfig(tp, scenario)),
      scenarioToolbarConfig.topRight
        .filterNot(tp => verifyCondition(tp.hidden, scenario))
        .map(tp => ToolbarPanel.fromConfig(tp, scenario)),
      scenarioToolbarConfig.bottomRight
        .filterNot(tp => verifyCondition(tp.hidden, scenario))
        .map(tp => ToolbarPanel.fromConfig(tp, scenario))
    )

}

@JsonCodec
final case class ScenarioToolbarSettings(
    id: String,
    topLeft: List[ToolbarPanel],
    bottomLeft: List[ToolbarPanel],
    topRight: List[ToolbarPanel],
    bottomRight: List[ToolbarPanel]
)

object ToolbarPanel {

  import ToolbarHelper._

  def apply(
      `type`: ToolbarPanelType,
      title: Option[String],
      buttonsVariant: Option[ToolbarButtonVariant],
      buttons: Option[List[ToolbarButton]],
      additionalParams: Option[Map[String, String]]
  ): ToolbarPanel =
    ToolbarPanel(`type`.toString, title, buttonsVariant, buttons, additionalParams)

  def fromConfig(config: ToolbarPanelConfig, scenario: ScenarioWithDetailsEntity[_]): ToolbarPanel =
    ToolbarPanel(
      config.identity,
      config.title.map(t => fillByScenarioData(t, scenario)),
      config.buttonsVariant,
      config.buttons.map(buttons =>
        buttons
          .filterNot(button => {
            verifyCondition(button.hidden, scenario)
          })
          .map(button => ToolbarButton.fromConfig(button, scenario))
      ),
      config.additionalParams
    )

}

@JsonCodec
final case class ToolbarPanel(
    id: String,
    title: Option[String],
    buttonsVariant: Option[ToolbarButtonVariant],
    buttons: Option[List[ToolbarButton]],
    additionalParams: Option[Map[String, String]]
)

object ToolbarButton {

  import ToolbarHelper._

  def fromConfig(config: ToolbarButtonConfig, scenario: ScenarioWithDetailsEntity[_]): ToolbarButton = ToolbarButton(
    config.`type`,
    config.name.map(t => fillByScenarioData(t, scenario)),
    config.title.map(t => fillByScenarioData(t, scenario)),
    config.icon.map(i => fillByScenarioData(i, scenario, urlOption = true)),
    config.url.map(th => fillByScenarioData(th, scenario, urlOption = true)),
    disabled = verifyCondition(config.disabled, scenario),
    config.markdownContent.map(mc => fillByScenarioData(mc, scenario)),
    config.docs.map(d =>
      DocsButtonConfig(
        fillByScenarioData(d.url, scenario, urlOption = true),
        d.label.map(fillByScenarioData(_, scenario))
      )
    ),
  )

}

@JsonCodec
final case class ToolbarButton(
    `type`: ToolbarButtonType,
    name: Option[String],
    title: Option[String],
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

  def createScenarioToolbarId(config: ScenarioToolbarsConfig, scenario: ScenarioWithDetailsEntity[_]): String =
    s"${config.uuidCode}-${if (scenario.isArchived) "archived" else "not-archived"}-${if (scenario.isFragment) "fragment"
      else "scenario"}"

  def fillByScenarioData(text: String, scenario: ScenarioWithDetailsEntity[_], urlOption: Boolean = false): String = {
    val scenarioName = if (urlOption) UriUtils.encodeURIComponent(scenario.name.value) else scenario.name.value

    text
      .replace("$processName", scenarioName)
      .replace("$processId", scenario.processId.value.toString)
  }

  def verifyCondition(condition: Option[ToolbarCondition], scenario: ScenarioWithDetailsEntity[_]): Boolean = {
    condition.nonEmpty && condition.exists(con => {
      if (con.shouldMatchAllOfConditions) {
        verifyFragmentCondition(con, scenario) && verifyArchivedCondition(con, scenario)
      } else {
        verifyFragmentCondition(con, scenario) || verifyArchivedCondition(con, scenario)
      }
    })
  }

  private def verifyFragmentCondition(condition: ToolbarCondition, scenario: ScenarioWithDetailsEntity[_]) =
    verifyCondition(scenario.isFragment, condition.fragment, condition.shouldMatchAllOfConditions)

  private def verifyArchivedCondition(condition: ToolbarCondition, scenario: ScenarioWithDetailsEntity[_]) =
    verifyCondition(scenario.isArchived, condition.archived, condition.shouldMatchAllOfConditions)

  // When we should match all conditions and expected condition is empty (not set) then we ignore this condition
  private def verifyCondition(
      toVerify: Boolean,
      expected: Option[Boolean],
      shouldMatchAllOfConditions: Boolean
  ): Boolean =
    (shouldMatchAllOfConditions && expected.isEmpty) || expected.contains(toVerify)

}
