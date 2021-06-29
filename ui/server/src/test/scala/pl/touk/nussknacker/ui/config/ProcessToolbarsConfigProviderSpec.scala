package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.common.config.ConfigException
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
      |        { type: "tips-panel", hide: { subprocess: true } }
      |      ]
      |      topRight: [
      |        {
      |          type: "process-info-panel"
      |          title: "Process Info"
      |          buttons: [
      |            { type: "process-save", icon: "/assets/buttons/save.svg", title: "save", disabled: { archived: true } }
      |            { type: "process-deploy" }
      |            { type: "custom-link", title: "metrics", icon: "/assets/buttons/metrics.svg", templateHref: "/metrics/{{processName}}" }
      |          ]
      |        }
      |      ]
      |    }
      |    categoryConfig {
      |      "Category2" {
      |        uuid: "58f1acff-d864-4d66-9f86-0fa7319f7043"
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
      |      "CustomLinkError" {
      |        topLeft: [
      |          {
      |            type: "process-info-panel"
      |            title: "Process Info"
      |            buttons: [
      |             { type: "custom-link", title: "metrics", icon: "/assets/buttons/metrics.svg" }
      |            ]
      |          }
      |        ]
      |      }
      |      "ButtonsError" {
      |        topLeft: [
      |          {
      |            id: "button1"
      |            type: "buttons-panel"
      |            title: "Buttons Info"
      |          }
      |        ]
      |      },
      |      "ButtonsIdError" {
      |        topLeft: [
      |          {
      |            type: "buttons-panel"
      |            title: "Buttons Info"
      |          }
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
        ToolbarPanelConfig(ProcessInfoPanel, None, Some("Process Info"), None, Some(List(
          ToolbarButtonConfig(ToolbarButtonConfigType.ProcessSave, Some("save"), Some("/assets/buttons/save.svg"), None, None, Some(ToolbarCondition(None, Some(true), None))),
          ToolbarButtonConfig(ToolbarButtonConfigType.ProcessDeploy, None, None, None, None, None),
          ToolbarButtonConfig(ToolbarButtonConfigType.CustomLink, Some("metrics"), Some("/assets/buttons/metrics.svg"), Some("/metrics/{{processName}}"), None, None),
        )),None)
      ),
      Nil
    )

    val categoryToolbarConfig = ProcessToolbarsConfig(
      Some(UUID.fromString("58f1acff-d864-4d66-9f86-0fa7319f7043")),
      List(ToolbarPanelConfig(TipsPanel, None, None, None, None, Some(ToolbarCondition(Some(true), None, None)))),
      Nil,
      List(
        ToolbarPanelConfig(ProcessInfoPanel, None, Some("Process Info Right"), Some(Small), Some(List(
          ToolbarButtonConfig(ToolbarButtonConfigType.ProcessSave, None, None, None, None, None),
        )),None)
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
      (processToolbarConfig, "CustomLinkError", "Button custom-link requires param: 'templateHref'."),
      (processToolbarConfig, "ButtonsIdError", "Toolbar buttons-panel requires param: 'id'."),
      (processToolbarConfig, "ButtonsError", "Toolbar buttons-panel requires non empty param: 'buttons'."),
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
