package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.config.processtoolbar._

import java.util.UUID

class ProcessToolbarsConfigProviderSpec extends FlatSpec with Matchers {

  import org.scalatest.prop.TableDrivenPropertyChecks._
  import ToolbarButtonsConfigVariant._
  import ToolbarPanelTypeConfig._
  
  lazy val processToolbarConfig: Config = ConfigFactory.parseString(
    """
      |{
      |processToolbarConfig {
      |    defaultConfig {
      |      topLeft: [
      |        { type: "tips-panel", hidden: { subprocess: true } }
      |      ]
      |      topRight: [
      |        {
      |          type: "process-info-panel"
      |          title: "Process Info"
      |          buttons: [
      |            { type: "process-save", icon: "/assets/buttons/save.svg", name: "save", disabled: { archived: true } }
      |            { type: "process-deploy" }
      |            { type: "custom-link", name: "metrics", title: "Metrics for process {{processName}}", icon: "/assets/buttons/metrics.svg", url: "/metrics/{{processName}}" }
      |          ]
      |        }
      |      ]
      |    }
      |    categoryConfig {
      |      "Category2" {
      |        uuid: 58f1acff-d864-4d66-9f86-0fa7319f7043
      |        topRight: [
      |          {
      |            type: "process-info-panel"
      |            title: "Process Info Right"
      |            buttonsVariant: "small"
      |            buttons: [
      |              { type: "process-save" }
      |            ]
      |          }
      |        ]
      |        bottomRight: [
      |          { type: "versions-panel" }
      |        ]
      |      }
      |      "MissingUrlError" {
      |        topLeft: [
      |          {
      |            type: "process-info-panel"
      |            title: "Process Info"
      |            buttons: [
      |             { type: "custom-link", name: "metrics", icon: "/assets/buttons/metrics.svg" }
      |            ]
      |          }
      |        ]
      |      },
      |      "MissingNameError" {
      |        topLeft: [
      |          {
      |            type: "process-info-panel"
      |            title: "Process Info"
      |            buttons: [
      |             { type: "custom-link", url: "/metrics", icon: "/assets/buttons/metrics.svg" }
      |            ]
      |          }
      |        ]
      |      },
      |      "RedundantUrlError" {
      |        topLeft: [
      |          {
      |            type: "process-info-panel"
      |            title: "Process Info"
      |            buttons: [
      |              { type: "process-save", title: "metrics", icon: "/assets/buttons/save.svg", url: "no-url" }
      |            ]
      |          }
      |        ]
      |      },
      |      "MissingButtonsError" {
      |        topLeft: [
      |          { id: "button1", type: "buttons-panel", title: "Buttons Info" }
      |        ]
      |      },
      |      "RedundantButtonsError" {
      |        topLeft: [
      |          { type: "tips-panel", hidden: { subprocess: true }, buttons: [] }
      |        ]
      |      },
      |      "RedundantButtonsVariantError" {
      |        topLeft: [
      |          { type: "tips-panel", hidden: { subprocess: true }, buttonsVariant: "small" }
      |        ]
      |      },
      |      "MissingIdError" {
      |        topLeft: [
      |          { type: "buttons-panel", title: "Buttons Info" }
      |        ]
      |      },
      |      "RedundantIdError" {
      |        topLeft: [
      |          { type: "tips-panel", hidden: { subprocess: true }, id: "BadId" }
      |        ]
      |      }
      |    }
      |  }
      |}
      |""".stripMargin
  )

  it should "properly create process toolbar configuration" in {
    val defaultToolbarConfig = ProcessToolbarsConfig(
      None,
      List(ToolbarPanelConfig(TipsPanel, None, None, None, None, Some(ToolbarCondition(Some(true), None, None)))),
      Nil,
      List(
        ToolbarPanelConfig(ProcessInfoPanel, None,
          Some("Process Info"), None, Some(List(
          ToolbarButtonConfig(ToolbarButtonConfigType.ProcessSave, Some("save"), None, Some("/assets/buttons/save.svg"), None, None, Some(ToolbarCondition(None, Some(true), None))),
          ToolbarButtonConfig(ToolbarButtonConfigType.ProcessDeploy, None, None, None, None, None, None),
          ToolbarButtonConfig(ToolbarButtonConfigType.CustomLink, Some("metrics"), Some("Metrics for process {{processName}}"), Some("/assets/buttons/metrics.svg"), Some("/metrics/{{processName}}"), None, None)
        )), None)
      ),
      Nil
    )

    val categoryToolbarConfig = ProcessToolbarsConfig(
      Some(UUID.fromString("58f1acff-d864-4d66-9f86-0fa7319f7043")),
      List(ToolbarPanelConfig(TipsPanel, None, None, None, None, Some(ToolbarCondition(Some(true), None, None)))),
      Nil,
      List(
        ToolbarPanelConfig(ProcessInfoPanel, None, Some("Process Info Right"), Some(Small), Some(List(
          ToolbarButtonConfig(ToolbarButtonConfigType.ProcessSave, None, None, None, None, None, None)
        )), None)
      ),
      List(ToolbarPanelConfig(VersionsPanel, None, None, None, None, None))
    )

    val testingConfigs = Table(
      ("config", "category", "expected"),
      (processToolbarConfig, "NotExistCategory", defaultToolbarConfig),
      (processToolbarConfig, "Category2", categoryToolbarConfig)
    )

    forAll(testingConfigs) { (config: Config, category: String, expected) =>
      val result = ProcessToolbarsConfigProvider.create(config, Some(category))
      result shouldBe expected
    }
  }

  it should "raise exception when configuration contains errors" in {
    val errorTestingConfigs = Table(
      ("config", "category", "message"),
      (processToolbarConfig, "MissingUrlError", "Button custom-link requires param: 'url'."),
      (processToolbarConfig, "MissingNameError", "Button custom-link requires param: 'name'."),
      (processToolbarConfig, "MissingIdError", "Toolbar buttons-panel requires param: 'id'."),
      (processToolbarConfig, "MissingButtonsError", "Toolbar buttons-panel requires non empty param: 'buttons'."),
      (processToolbarConfig, "RedundantButtonsError", "Toolbar tips-panel doesn't contain param: 'buttons'."),
      (processToolbarConfig, "RedundantIdError", "Toolbar tips-panel doesn't contain param: 'id'."),
      (processToolbarConfig, "RedundantUrlError", "Button process-save doesn't contain param: 'url'."),
      (processToolbarConfig, "RedundantButtonsVariantError", "Toolbar tips-panel doesn't contain param: 'buttonsVariant'.")
    )

    forAll(errorTestingConfigs) { (config: Config, category: String, message) =>
      intercept[IllegalArgumentException] {
        ProcessToolbarsConfigProvider.create(config, Some(category))
      }.getMessage should include(message)
    }
  }

  it should "raise exception when configuration is missing" in {
    intercept[com.typesafe.config.ConfigException.Missing] {
      ProcessToolbarsConfigProvider.create(ConfigFactory.empty(), None)
    }
  }
}
