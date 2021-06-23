package pl.touk.nussknacker.ui.service

import argonaut.JsonObject
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.config.processtoolbar.{ProcessToolbarsConfigProvider, ToolbarButtonConfigType, ToolbarButtonsConfigVariant, ToolbarCondition, ToolbarConditionType, ToolbarPanelTypeConfig}

import java.time.LocalDateTime
import java.util.UUID

class ConfigProcessToolbarServiceSpec extends FlatSpec with Matchers {

  import ToolbarButtonConfigType._
  import ToolbarPanelTypeConfig._
  import ToolbarButtonsConfigVariant._
  import org.scalatest.prop.TableDrivenPropertyChecks._

  private val generator = new scala.util.Random

  private lazy val config: Config = ConfigFactory.parseString(
    """
      |{
      |  processToolbarConfig {
      |    defaultConfig {
      |      topLeft: [
      |        { type: "tips-panel", hide: {subprocess: true} }
      |      ]
      |      topRight: [
      |        {
      |          type: "process-info-panel"
      |          title: "Process Info {{processName}}"
      |          buttons: [
      |            { type: "process-save", icon: "/assets/{{processId}}/buttons/save.svg", title: "save", disabled: {archived: true} }
      |            { type: "custom-link", title: "metrics", templateHref: "/metrics/{{processName}}" }
      |            { type: "custom-link", templateHref: "/analytics/{{processId}}", disabled: {archived: true, subprocess: true, type: "allof"} }
      |          ]
      |        }
      |        {
      |           id: "buttons1"
      |           type: "buttons-panel"
      |           hide: {subprocess: false}
      |           buttonsVariant: "small"
      |           buttons: [ { type: "process-deploy" }, { type: "process-pdf", hide: {archived: true} }]
      |        }
      |        {
      |           id: "buttons2"
      |           type: "buttons-panel"
      |           hide: { archived: true }
      |           buttons: [ { type: "process-cancel" } ]
      |        }
      |      ]
      |    }
      |    categoryConfig {
      |      "Category1" {
      |        topLeft: [
      |          { type: "creator-panel", hide: {subprocess: true, archived: false, type: "allof"} }
      |          { type: "attachments-panel", hide: {subprocess: true, archived: true, type: "allof"} }
      |          { type: "comments-panel", hide: {subprocess: false, archived: true, type: "allof"} }
      |        ]
      |        bottomRight: [
      |          { type: "versions-panel" }
      |        ]
      |      },
      |      "Category3" {
      |        uuid: "68013242-2007-462b-9526-7a9f8684227c"
      |        topLeft: [
      |          { type: "creator-panel", hide: {subprocess: true, archived: false} }
      |          { type: "attachments-panel", hide: {subprocess: true, archived: true } }
      |          { type: "comments-panel", hide: {subprocess: false, archived: true } }
      |        ]
      |        bottomRight: [
      |          { type: "versions-panel" }
      |        ]
      |      }
      |    }
      |  }
      |}
      |""".stripMargin
  )

  private val categories = List("Category1", "Category3")

  it should "verify all toolbar condition cases" in {
    val process = createProcess("process", "Category1", isSubprocess = false, isArchived = false)
    val archivedProcess = createProcess("archived-process", "Category1", isSubprocess = false, isArchived = true)
    val subprocess = createProcess("subprocess", "Category1", isSubprocess = true, isArchived = false)
    val archivedSubprocess = createProcess("archived-subprocess", "Category1", isSubprocess = true, isArchived = true)

    val testingData = Table(
      ("process", "condition", "expected"),
      (process, None, false),
      (archivedProcess, None, false),
      (subprocess, None, false),
      (archivedSubprocess, None, false),

      //All
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

      (subprocess, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.AllOf))), false),
      (subprocess, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.AllOf))), true),
      (subprocess, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.AllOf))), true),
      (subprocess, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.AllOf))), false),
      (subprocess, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (subprocess, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (subprocess, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.AllOf))), true),
      (subprocess, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.AllOf))), false),

      (archivedSubprocess, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.AllOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.AllOf))), true),
      (archivedSubprocess, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.AllOf))), true),
      (archivedSubprocess, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.AllOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.AllOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.AllOf))), true),

      //OneOf
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

      (subprocess, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.OneOf))), false),
      (subprocess, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.OneOf))), true),
      (subprocess, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.OneOf))), true),
      (subprocess, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.OneOf))), false),
      (subprocess, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (subprocess, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.OneOf))), false),
      (subprocess, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (subprocess, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.OneOf))), true),

      (archivedSubprocess, Some(ToolbarCondition(Some(false), None, Some(ToolbarConditionType.OneOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(Some(true), None, Some(ToolbarConditionType.OneOf))), true),
      (archivedSubprocess, Some(ToolbarCondition(None, Some(false), Some(ToolbarConditionType.OneOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(None, Some(true), Some(ToolbarConditionType.OneOf))), true),
      (archivedSubprocess, Some(ToolbarCondition(Some(false), Some(false), Some(ToolbarConditionType.OneOf))), false),
      (archivedSubprocess, Some(ToolbarCondition(Some(false), Some(true), Some(ToolbarConditionType.OneOf))), true),
      (archivedSubprocess, Some(ToolbarCondition(Some(true), Some(false), Some(ToolbarConditionType.OneOf))), true),
      (archivedSubprocess, Some(ToolbarCondition(Some(true), Some(true), Some(ToolbarConditionType.OneOf))), true)
    )

    forAll(testingData) { (process: BaseProcessDetails[_], condition:  Option[ToolbarCondition], expected: Boolean) =>
      val result = ToolbarHelper.verifyCondition(condition, process)
      result shouldBe expected
    }
  }

  it should "properly create process toolbar configuration" in {
    val service = new ConfigProcessToolbarService(config, categories)
    val serviceWithEmptyConfig = new ConfigProcessToolbarService(ConfigFactory.empty(), List.empty)

    val process = createProcess("process", "Category1", isSubprocess = false, isArchived = false)
    val archivedProcess = createProcess("archived-process", "Category1", isSubprocess = false, isArchived = true)
    val subprocess = createProcess("subprocess", "Category1", isSubprocess = true, isArchived = false)
    val archivedSubprocess = createProcess("archived-subprocess", "Category1", isSubprocess = true, isArchived = true)
    val processCategory2 = createProcess("process2", "Category2", isSubprocess = false, isArchived = false)
    val processCategory3 = createProcess("process3", "Category3", isSubprocess = false, isArchived = false)
    val emptyProcess = createProcess("empty", "empty", isSubprocess = false, isArchived = false)

    val testingData = Table(
      ("service", "process"),
      (service, process),
      (service, archivedProcess),
      (service, subprocess),
      (service, archivedSubprocess),
      (service, processCategory2),
      (service, processCategory3),
      (serviceWithEmptyConfig, emptyProcess)
    )

    forAll(testingData) { (service: ProcessToolbarService, process: BaseProcessDetails[_]) =>
      val result = service.getProcessToolbarSettings(process)
      val expected = createProcessToolbarSettings(process)
      result shouldBe expected
    }
  }

  private def createProcessToolbarSettings(process: BaseProcessDetails[_]): ProcessToolbarSettings = {
    val emptyUuid = ToolbarHelper.createProcessToolbarUUID(process, ProcessToolbarsConfigProvider.create(ConfigFactory.empty(), Some(process.processCategory)))
    val uuid = ToolbarHelper.createProcessToolbarUUID(process, ProcessToolbarsConfigProvider.create(config, Some(process.processCategory)))

    (process.isSubprocess, process.isArchived, process.processCategory) match {
      case (false, false, "Category1") => ProcessToolbarSettings(
        uuid,
        List(
          ToolbarPanel(CreatorPanel, None, None, None),
          ToolbarPanel(AttachmentsPanel, None, None, None),
          ToolbarPanel(CommentsPanel, None, None, None)
        ),
        Nil,
        List(
          ToolbarPanel(ProcessInfoPanel, Some(s"Process Info ${process.name}"), None, Some(List(
            ToolbarButton(ProcessSave, Some("save"), Some(s"/assets/${process.processId.value}/buttons/save.svg"), None, disabled = false),
            ToolbarButton(CustomLink, Some("metrics"), None, Some(s"/metrics/${process.name}"), disabled = false),
            ToolbarButton(CustomLink, None, None, Some(s"/analytics/${process.processId.value}"), disabled = false)
          ))),
          ToolbarPanel("buttons2", None, None,  Some(List(
            ToolbarButton(ProcessCancel, None, None, None, disabled = false)
          )))
        ),
        List(ToolbarPanel(VersionsPanel, None, None, None))
      )
      case (false, true, "Category1") => ProcessToolbarSettings(
        uuid,
        List(
          ToolbarPanel(CreatorPanel, None, None, None),
          ToolbarPanel(AttachmentsPanel, None, None, None)
        ),
        Nil,
        List(
          ToolbarPanel(ProcessInfoPanel, Some(s"Process Info ${process.name}"), None, Some(List(
            ToolbarButton(ProcessSave, Some("save"), Some(s"/assets/${process.processId.value}/buttons/save.svg"), None, disabled = true),
            ToolbarButton(CustomLink, Some("metrics"), None, Some(s"/metrics/${process.name}"), disabled = false),
            ToolbarButton(CustomLink, None, None, Some(s"/analytics/${process.processId.value}"), disabled = false)
          )))
        ),
        List(ToolbarPanel(VersionsPanel, None, None, None))
      )
      case (true, false, "Category1") => ProcessToolbarSettings(
        uuid,
        List(
          ToolbarPanel(AttachmentsPanel, None, None, None),
          ToolbarPanel(CommentsPanel, None, None, None)
        ),
        Nil,
        List(
          ToolbarPanel(ProcessInfoPanel, Some(s"Process Info ${process.name}"), None, Some(List(
            ToolbarButton(ProcessSave, Some("save"), Some(s"/assets/${process.processId.value}/buttons/save.svg"), None, disabled = false),
            ToolbarButton(CustomLink, Some("metrics"), None, Some(s"/metrics/${process.name}"), disabled = false),
            ToolbarButton(CustomLink, None, None, Some(s"/analytics/${process.processId.value}"), disabled = false)
          ))),
          ToolbarPanel("buttons1", None, Some(Small), Some(List(
            ToolbarButton(ProcessDeploy, None, None, None, disabled = false),
            ToolbarButton(ProcessPDF, None, None, None, disabled = false),
          ))),
          ToolbarPanel("buttons2", None, None, Some(List(
            ToolbarButton(ProcessCancel, None, None, None, disabled = false)
          )))
        ),
        List(ToolbarPanel(VersionsPanel, None, None, None))
      )
      case (true, true, "Category1") => ProcessToolbarSettings(
        uuid,
        List(
          ToolbarPanel(CreatorPanel, None, None, None),
          ToolbarPanel(CommentsPanel, None, None, None)
        ),
        Nil,
        List(
          ToolbarPanel(ProcessInfoPanel, Some(s"Process Info ${process.name}"), None, Some(List(
            ToolbarButton(ProcessSave, Some("save"), Some(s"/assets/${process.processId.value}/buttons/save.svg"), None, disabled = true),
            ToolbarButton(CustomLink, Some("metrics"), None, Some(s"/metrics/${process.name}"), disabled = false),
            ToolbarButton(CustomLink, None, None, Some(s"/analytics/${process.processId.value}"), disabled = true)
          ))),
          ToolbarPanel("buttons1", None, Some(Small),  Some(List(
            ToolbarButton(ProcessDeploy, None, None, None, disabled = false)
          )))
        ),
        List(ToolbarPanel(VersionsPanel, None, None, None))
      )
      case (false, false, "Category2") => ProcessToolbarSettings(
        uuid,
        List(
          ToolbarPanel(TipsPanel, None, None, None)
        ),
        Nil,
        List(
          ToolbarPanel(ProcessInfoPanel, Some(s"Process Info ${process.name}"), None, Some(List(
            ToolbarButton(ProcessSave, Some("save"), Some(s"/assets/${process.processId.value}/buttons/save.svg"), None, disabled = false),
            ToolbarButton(CustomLink, Some("metrics"), None, Some(s"/metrics/${process.name}"), disabled = false),
            ToolbarButton(CustomLink, None, None, Some(s"/analytics/${process.processId.value}"), disabled = false)
          ))),
          ToolbarPanel("buttons2", None, None, Some(List(
            ToolbarButton(ProcessCancel, None, None, None, disabled = false)
          )))
        ),
        Nil
      )
      case (false, false, "Category3") => ProcessToolbarSettings(
        UUID.fromString("68013242-2007-462b-9526-7a9f8684227c"),
        List(
          ToolbarPanel(AttachmentsPanel, None, None, None)
        ),
        Nil,
        List(
          ToolbarPanel(ProcessInfoPanel, Some(s"Process Info ${process.name}"), None, Some(List(
            ToolbarButton(ProcessSave, Some("save"), Some(s"/assets/${process.processId.value}/buttons/save.svg"), None, disabled = false),
            ToolbarButton(CustomLink, Some("metrics"), None, Some(s"/metrics/${process.name}"), disabled = false),
            ToolbarButton(CustomLink, None, None, Some(s"/analytics/${process.processId.value}"), disabled = false)
          ))),
          ToolbarPanel("buttons2", None, None, Some(List(
            ToolbarButton(ProcessCancel, None, None, None, disabled = false)
          )))
        ),
        List(ToolbarPanel(VersionsPanel, None, None, None))
      )
      case (_, _, _) =>
        ProcessToolbarSettings(emptyUuid, Nil, Nil, Nil, Nil)
    }
  }

  private def createProcess(name: String, category: ProcessingType, isSubprocess: Boolean, isArchived: Boolean) = {
    BaseProcessDetails[JsonObject](
      id = name,
      name = name,
      processId = ProcessId(math.abs(generator.nextLong())),
      processVersionId = 1L,
      isLatestVersion = true,
      description = None,
      isArchived = isArchived,
      isSubprocess = isSubprocess,
      processType = ProcessType.Graph,
      processingType = category,
      processCategory = category,
      modificationDate = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "admin",
      tags = Nil,
      lastDeployedAction = None,
      lastAction = None,
      json = None,
      history = Nil,
      modelVersion = None
    )
  }
}
