package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil
import pl.touk.nussknacker.ui.config.scenariotoolbar._
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

class ConfigScenarioToolbarServiceSpec extends AnyFlatSpec with Matchers {

  import ToolbarButtonConfigType._
  import ToolbarButtonsConfigVariant._
  import ToolbarPanelTypeConfig._
  import org.scalatest.prop.TableDrivenPropertyChecks._

  private lazy val parsedConfig = CategoriesScenarioToolbarsConfigParser.parse(
    ConfigFactory.parseString(
      """
      |{
      |  processToolbarConfig {
      |    defaultConfig {
      |      topLeft: [
      |        { type: "tips-panel", hidden: {fragment: true} }
      |      ]
      |      topRight: [
      |        {
      |          type: "process-actions-panel"
      |          title: "Process Actions $processName"
      |          buttons: [
      |            { type: "process-save", icon: "/assets/$processId/buttons/save.svg", title: "save", disabled: {archived: true} }
      |            { type: "custom-link", name: "metrics", title: "metrics for process", url: "/metrics/$processName" }
      |            { type: "custom-link", name: "analytics", url: "/analytics/$processId", disabled: {archived: true, fragment: true, type: "allof"} }
      |          ]
      |        }
      |        {
      |           id: "buttons1"
      |           type: "buttons-panel"
      |           hidden: {fragment: false}
      |           buttonsVariant: "small"
      |           buttons: [ { type: "process-deploy" }, { type: "process-pdf", hidden: {archived: true} }]
      |        }
      |        {
      |           id: "buttons2"
      |           type: "buttons-panel"
      |           hidden: { archived: true }
      |           buttons: [ { type: "process-cancel" } ]
      |        }
      |      ]
      |    }
      |    categoryConfig {
      |      "Category1" {
      |        topLeft: [
      |          { type: "creator-panel", hidden: {fragment: true, archived: false, type: "allof"} }
      |          { type: "activities-panel" }
      |        ]
      |        bottomRight: []
      |      },
      |      "Category3" {
      |        uuid: "68013242-2007-462b-9526-7a9f8684227c"
      |        topLeft: [
      |          { type: "creator-panel", hidden: {fragment: true, archived: false} }
      |          { type: "activities-panel" }
      |        ]
      |        bottomRight: []
      |      }
      |    }
      |  }
      |}
      |""".stripMargin
    )
  )

  private val service = new ConfigScenarioToolbarService(parsedConfig)

  it should "verify all toolbar condition cases" in {
    val process          = createProcess("process", "Category1", isFragment = false, isArchived = false)
    val archivedProcess  = createProcess("archived-process", "Category1", isFragment = false, isArchived = true)
    val fragment         = createProcess("fragment", "Category1", isFragment = true, isArchived = false)
    val archivedFragment = createProcess("archived-fragment", "Category1", isFragment = true, isArchived = true)

    val testingData = Table(
      ("process", "condition", "expected"),
      (process, None, false),
      (archivedProcess, None, false),
      (fragment, None, false),
      (archivedFragment, None, false),

      // All of conditions match
      (process, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.AllOf))), true),
      (process, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.AllOf))), true),
      (process, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.AllOf))), true),
      (process, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.AllOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.AllOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.AllOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.AllOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.AllOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.AllOf))), true),
      (fragment, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (archivedFragment, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.AllOf))), false),
      (archivedFragment, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.AllOf))), true),
      (archivedFragment, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedFragment, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.AllOf))), true),
      (archivedFragment, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedFragment, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (archivedFragment, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedFragment, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.AllOf))), true),

      // One of conditions match
      (process, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.OneOf))), false),
      (process, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.OneOf))), false),
      (process, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.OneOf))), false),
      (archivedProcess, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.OneOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.OneOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.OneOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.OneOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.OneOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.OneOf))), false),
      (archivedProcess, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.OneOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.OneOf))), false),
      (fragment, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.OneOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.OneOf))), true),
      (archivedFragment, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.OneOf))), false),
      (archivedFragment, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.OneOf))), true),
      (archivedFragment, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.OneOf))), false),
      (archivedFragment, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.OneOf))), true),
      (archivedFragment, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.OneOf))), false),
      (archivedFragment, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.OneOf))), true),
      (archivedFragment, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (archivedFragment, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.OneOf))), true)
    )

    forAll(testingData) {
      (process: ScenarioWithDetailsEntity[_], condition: Option[ToolbarCondition], expected: Boolean) =>
        val result = ToolbarHelper.verifyCondition(condition, process)
        result shouldBe expected
    }
  }

  it should "properly create process toolbar configuration" in {
    val process          = createProcess("process with space", "Category1", isFragment = false, isArchived = false)
    val archivedProcess  = createProcess("archived-process", "Category1", isFragment = false, isArchived = true)
    val fragment         = createProcess("fragment", "Category1", isFragment = true, isArchived = false)
    val archivedFragment = createProcess("archived-fragment", "Category1", isFragment = true, isArchived = true)
    val processCategory2 = createProcess("process2", "Category2", isFragment = false, isArchived = false)

    val testingData = Table(
      "process",
      process,
      archivedProcess,
      fragment,
      archivedFragment,
      processCategory2
    )

    forAll(testingData) { (process: ScenarioWithDetailsEntity[_]) =>
      val result   = service.getScenarioToolbarSettings(process)
      val expected = createProcessToolbarSettings(process)
      result shouldBe expected
    }
  }

  private def createProcessToolbarSettings(process: ScenarioWithDetailsEntity[_]): ScenarioToolbarSettings = {
    val processToolbarConfig = parsedConfig.getConfig(process.processCategory)
    val id                   = ToolbarHelper.createScenarioToolbarId(processToolbarConfig, process)

    def processName(process: ScenarioWithDetailsEntity[_]) = UriUtils.encodeURIComponent(process.name.value)

    (process.isFragment, process.isArchived, process.processCategory) match {
      case (false, false, "Category1") =>
        ScenarioToolbarSettings(
          id,
          List(
            ToolbarPanel(CreatorPanel, None, None, None),
            ToolbarPanel(ActivitiesPanel, None, None, None),
          ),
          Nil,
          List(
            ToolbarPanel(
              ProcessActionsPanel,
              Some(s"Process Actions ${process.name}"),
              None,
              Some(
                List(
                  ToolbarButton(
                    ProcessSave,
                    None,
                    Some("save"),
                    Some(s"/assets/${process.processId.value}/buttons/save.svg"),
                    None,
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("metrics"),
                    Some("metrics for process"),
                    None,
                    Some(s"/metrics/${processName(process)}"),
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("analytics"),
                    None,
                    None,
                    Some(s"/analytics/${process.processId.value}"),
                    disabled = false
                  )
                )
              )
            ),
            ToolbarPanel(
              "buttons2",
              None,
              None,
              Some(
                List(
                  ToolbarButton(ProcessCancel, None, None, None, None, disabled = false)
                )
              )
            )
          ),
          List.empty,
        )
      case (false, true, "Category1") =>
        ScenarioToolbarSettings(
          id,
          List(
            ToolbarPanel(CreatorPanel, None, None, None),
            ToolbarPanel(ActivitiesPanel, None, None, None)
          ),
          Nil,
          List(
            ToolbarPanel(
              ProcessActionsPanel,
              Some(s"Process Actions ${process.name}"),
              None,
              Some(
                List(
                  ToolbarButton(
                    ProcessSave,
                    None,
                    Some("save"),
                    Some(s"/assets/${process.processId.value}/buttons/save.svg"),
                    None,
                    disabled = true
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("metrics"),
                    Some("metrics for process"),
                    None,
                    Some(s"/metrics/${processName(process)}"),
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("analytics"),
                    None,
                    None,
                    Some(s"/analytics/${process.processId.value}"),
                    disabled = false
                  )
                )
              )
            )
          ),
          List.empty,
        )
      case (true, false, "Category1") =>
        ScenarioToolbarSettings(
          id,
          List(
            ToolbarPanel(ActivitiesPanel, None, None, None),
          ),
          Nil,
          List(
            ToolbarPanel(
              ProcessActionsPanel,
              Some(s"Process Actions ${process.name}"),
              None,
              Some(
                List(
                  ToolbarButton(
                    ProcessSave,
                    None,
                    Some("save"),
                    Some(s"/assets/${process.processId.value}/buttons/save.svg"),
                    None,
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("metrics"),
                    Some("metrics for process"),
                    None,
                    Some(s"/metrics/${processName(process)}"),
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("analytics"),
                    None,
                    None,
                    Some(s"/analytics/${process.processId.value}"),
                    disabled = false
                  )
                )
              )
            ),
            ToolbarPanel(
              "buttons1",
              None,
              Some(Small),
              Some(
                List(
                  ToolbarButton(ProcessDeploy, None, None, None, None, disabled = false),
                  ToolbarButton(ProcessPDF, None, None, None, None, disabled = false)
                )
              )
            ),
            ToolbarPanel(
              "buttons2",
              None,
              None,
              Some(
                List(
                  ToolbarButton(ProcessCancel, None, None, None, None, disabled = false)
                )
              )
            )
          ),
          List.empty,
        )
      case (true, true, "Category1") =>
        ScenarioToolbarSettings(
          id,
          List(
            ToolbarPanel(CreatorPanel, None, None, None),
            ToolbarPanel(ActivitiesPanel, None, None, None)
          ),
          Nil,
          List(
            ToolbarPanel(
              ProcessActionsPanel,
              Some(s"Process Actions ${process.name}"),
              None,
              Some(
                List(
                  ToolbarButton(
                    ProcessSave,
                    None,
                    Some("save"),
                    Some(s"/assets/${process.processId.value}/buttons/save.svg"),
                    None,
                    disabled = true
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("metrics"),
                    Some("metrics for process"),
                    None,
                    Some(s"/metrics/${processName(process)}"),
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("analytics"),
                    None,
                    None,
                    Some(s"/analytics/${process.processId.value}"),
                    disabled = true
                  )
                )
              )
            ),
            ToolbarPanel(
              "buttons1",
              None,
              Some(Small),
              Some(
                List(
                  ToolbarButton(ProcessDeploy, None, None, None, None, disabled = false)
                )
              )
            )
          ),
          List.empty,
        )
      case (false, false, "Category2") =>
        ScenarioToolbarSettings(
          id,
          List(
            ToolbarPanel(TipsPanel, None, None, None)
          ),
          Nil,
          List(
            ToolbarPanel(
              ProcessActionsPanel,
              Some(s"Process Actions ${process.name}"),
              None,
              Some(
                List(
                  ToolbarButton(
                    ProcessSave,
                    None,
                    Some("save"),
                    Some(s"/assets/${process.processId.value}/buttons/save.svg"),
                    None,
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("metrics"),
                    Some("metrics for process"),
                    None,
                    Some(s"/metrics/${processName(process)}"),
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("analytics"),
                    None,
                    None,
                    Some(s"/analytics/${process.processId.value}"),
                    disabled = false
                  )
                )
              )
            ),
            ToolbarPanel(
              "buttons2",
              None,
              None,
              Some(
                List(
                  ToolbarButton(ProcessCancel, None, None, None, None, disabled = false)
                )
              )
            )
          ),
          Nil
        )
      case (false, false, "Category3") =>
        ScenarioToolbarSettings(
          id,
          List(
            ToolbarPanel(ActivitiesPanel, None, None, None)
          ),
          Nil,
          List(
            ToolbarPanel(
              ProcessActionsPanel,
              Some(s"Process Actions ${process.name}"),
              None,
              Some(
                List(
                  ToolbarButton(
                    ProcessSave,
                    None,
                    Some("save"),
                    Some(s"/assets/${process.processId.value}/buttons/save.svg"),
                    None,
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("metrics"),
                    Some("metrics for process"),
                    None,
                    Some(s"/metrics/${processName(process)}"),
                    disabled = false
                  ),
                  ToolbarButton(
                    CustomLink,
                    Some("analytics"),
                    None,
                    None,
                    Some(s"/analytics/${process.processId.value}"),
                    disabled = false
                  )
                )
              )
            ),
            ToolbarPanel(
              "buttons2",
              None,
              None,
              Some(
                List(
                  ToolbarButton(ProcessCancel, None, None, None, None, disabled = false)
                )
              )
            )
          ),
          List.empty,
        )
      case (_, _, _) =>
        ScenarioToolbarSettings("not-exist", Nil, Nil, Nil, Nil)
    }
  }

  private def createProcess(name: String, category: String, isFragment: Boolean, isArchived: Boolean) =
    TestProcessUtil.wrapWithScenarioDetailsEntity(
      name = ProcessName(name),
      category = category,
      isFragment = isFragment,
      isArchived = isArchived
    )

}
