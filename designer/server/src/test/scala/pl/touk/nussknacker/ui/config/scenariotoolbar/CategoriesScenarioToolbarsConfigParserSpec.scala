package pl.touk.nussknacker.ui.config.scenariotoolbar

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class CategoriesScenarioToolbarsConfigParserSpec extends AnyFlatSpec with Matchers {

  import ToolbarButtonsConfigVariant._
  import ToolbarPanelTypeConfig._
  import org.scalatest.prop.TableDrivenPropertyChecks._

  private val scenarioToolbarConfig = CategoriesScenarioToolbarsConfigParser.parse(
    ConfigFactory.parseString("""
      |processToolbarConfig {
      |  defaultConfig {
      |    topLeft: [
      |      { type: "tips-panel", hidden: { fragment: true } }
      |    ]
      |    topRight: [
      |      {
      |        type: "process-actions-panel"
      |        title: "Process Actions"
      |        buttons: [
      |          { type: "process-save", icon: "/assets/buttons/save.svg", name: "save", disabled: { archived: true } }
      |          { type: "process-deploy" }
      |          { type: "custom-link", name: "metrics", title: "Metrics for process $processName", icon: "/assets/buttons/metrics.svg", url: "/metrics/$processName" }
      |        ]
      |      }
      |    ]
      |  }
      |  categoryConfig {
      |    "Category2" {
      |      uuid: 58f1acff-d864-4d66-9f86-0fa7319f7043
      |      topRight: [
      |        {
      |          type: "process-actions-panel"
      |          title: "Process Actions Right"
      |          buttonsVariant: "small"
      |          buttons: [
      |            { type: "process-save" }
      |          ]
      |        }
      |      ]
      |      bottomRight: [
      |        { type: "activities-panel" }
      |      ]
      |    }
      |  }
      |}
      |""".stripMargin)
  )

  it should "properly create scenario toolbar configuration" in {
    val defaultToolbarConfig = ScenarioToolbarsConfig(
      None,
      List(ToolbarPanelConfig(TipsPanel, None, None, None, None, Some(ToolbarCondition(Some(true), None, None)))),
      Nil,
      List(
        ToolbarPanelConfig(
          ProcessActionsPanel,
          None,
          Some("Process Actions"),
          None,
          Some(
            List(
              ToolbarButtonConfig(
                ToolbarButtonConfigType.ProcessSave,
                Some("save"),
                None,
                Some("/assets/buttons/save.svg"),
                None,
                None,
                Some(ToolbarCondition(None, Some(true), None))
              ),
              ToolbarButtonConfig(
                ToolbarButtonConfigType.ProcessDeploy,
                None,
                None,
                None,
                None,
                None,
                None
              ),
              ToolbarButtonConfig(
                ToolbarButtonConfigType.CustomLink,
                Some("metrics"),
                Some("Metrics for process $processName"),
                Some("/assets/buttons/metrics.svg"),
                Some("/metrics/$processName"),
                None,
                None
              )
            )
          ),
          None
        )
      ),
      Nil
    )

    val categoryToolbarConfig = ScenarioToolbarsConfig(
      Some(UUID.fromString("58f1acff-d864-4d66-9f86-0fa7319f7043")),
      List(ToolbarPanelConfig(TipsPanel, None, None, None, None, Some(ToolbarCondition(Some(true), None, None)))),
      Nil,
      List(
        ToolbarPanelConfig(
          ProcessActionsPanel,
          None,
          Some("Process Actions Right"),
          Some(Small),
          Some(
            List(
              ToolbarButtonConfig(ToolbarButtonConfigType.ProcessSave, None, None, None, None, None, None)
            )
          ),
          None
        )
      ),
      List(ToolbarPanelConfig(ActivitiesPanel, None, None, None, None, None))
    )

    val testingConfigs = Table(
      ("category", "expected"),
      ("NotExistCategory", defaultToolbarConfig),
      ("Category2", categoryToolbarConfig)
    )

    forAll(testingConfigs) { (category, expected) =>
      scenarioToolbarConfig.getConfig(category) shouldBe expected
    }
  }

  it should "raise exception when configuration contains errors" in {
    val configMap = ConfigFactory
      .parseString("""
        |"MissingUrlError" {
        |  type: "process-actions-panel"
        |  title: "Process Actions"
        |  buttons: [
        |   { type: "custom-link", name: "metrics", icon: "/assets/buttons/metrics.svg" }
        |  ]
        |},
        |"MissingNameError" {
        |  type: "process-actions-panel"
        |  title: "Process Actions"
        |  buttons: [
        |   { type: "custom-link", url: "/metrics", icon: "/assets/buttons/metrics.svg" }
        |  ]
        |},
        |"RedundantUrlError" {
        |  type: "process-actions-panel"
        |  title: "Process Actions"
        |  buttons: [
        |    { type: "process-save", title: "metrics", icon: "/assets/buttons/save.svg", url: "no-url" }
        |  ]
        |},
        |"MissingButtonsError" {
        |  id: "button1"
        |  type: "buttons-panel"
        |  title: "Buttons Info"
        |},
        |"RedundantButtonsError" {
        |  type: "tips-panel"
        |  hidden: { fragment: true }
        |  buttons: []
        |},
        |"RedundantButtonsVariantError" {
        |  type: "tips-panel"
        |  hidden: { fragment: true }
        |  buttonsVariant: "small"
        |},
        |"MissingIdError" {
        |  type: "buttons-panel"
        |  title: "Buttons Info"
        |},
        |"RedundantIdError" {
        |  type: "tips-panel"
        |  hidden: { fragment: true }
        |  id: "BadId"
        |}
        |""".stripMargin)
      .root()

    def wrapWithConfig(configName: String): Config = {
      ConfigFactory.parseString(s"""
          |processToolbarConfig {
          |  defaultConfig {
          |    topLeft: [
          |      ${configMap.get(configName).render()}
          |    ]
          |  }
          |}
          |""".stripMargin)
    }

    val errorTestingConfigs = Table(
      ("configName", "message"),
      ("MissingUrlError", "Button custom-link requires param: 'url'."),
      ("MissingNameError", "Button custom-link requires param: 'name'."),
      ("MissingIdError", "Toolbar buttons-panel requires param: 'id'."),
      ("MissingButtonsError", "Toolbar buttons-panel requires non empty param: 'buttons'."),
      ("RedundantButtonsError", "Toolbar tips-panel doesn't contain param: 'buttons'."),
      ("RedundantIdError", "Toolbar tips-panel doesn't contain param: 'id'."),
      ("RedundantUrlError", "Button process-save doesn't contain param: 'url'."),
      ("RedundantButtonsVariantError", "Toolbar tips-panel doesn't contain param: 'buttonsVariant'.")
    )
    forAll(errorTestingConfigs) { (configName, message) =>
      intercept[IllegalArgumentException] {
        CategoriesScenarioToolbarsConfigParser.parse(wrapWithConfig(configName))
      }.getMessage should include(message)
    }
  }

  it should "raise exception when configuration is missing" in {
    intercept[com.typesafe.config.ConfigException.Missing] {
      CategoriesScenarioToolbarsConfigParser.parse(ConfigFactory.empty())
    }
  }

}
