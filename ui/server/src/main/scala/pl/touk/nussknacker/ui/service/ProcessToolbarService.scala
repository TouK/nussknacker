package pl.touk.nussknacker.ui.service

import com.typesafe.config.Config
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonConfigType.ToolbarButtonType
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarPanelTypeConfig.ToolbarPanelType
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonsConfigVariant.ToolbarButtonVariant
import pl.touk.nussknacker.ui.config.processtoolbar._

trait ProcessToolbarService {
  def getProcessToolbarSettings(process: BaseProcessDetails[_]): ProcessToolbarSettings
}

class ConfigProcessToolbarService(config: Config, categories: List[String]) extends ProcessToolbarService {

  private val defaultProcessToolbarConfig = ProcessToolbarsConfigProvider.create(config, None)

  private val categoriesProcessToolbarConfig: Map[String, ProcessToolbarsConfig] =
    categories
      .map(category => category -> ProcessToolbarsConfigProvider.create(config, Some(category)))
      .toMap

  override def getProcessToolbarSettings(process: BaseProcessDetails[_]): ProcessToolbarSettings = {
    val toolbarConfig = categoriesProcessToolbarConfig.getOrElse(process.processCategory, defaultProcessToolbarConfig)
    ProcessToolbarSettings.fromConfig(toolbarConfig, process)
  }
}

object ProcessToolbarSettings {

  import ToolbarHelper._

  def fromConfig(processToolbarConfig: ProcessToolbarsConfig, process: BaseProcessDetails[_]): ProcessToolbarSettings =
    ProcessToolbarSettings(
      createProcessToolbarId(processToolbarConfig, process),
      processToolbarConfig.topLeft.filterNot(tp => verifyCondition(tp.hidden, process)).map(tp => ToolbarPanel.fromConfig(tp, process)),
      processToolbarConfig.bottomLeft.filterNot(tp => verifyCondition(tp.hidden, process)).map(tp => ToolbarPanel.fromConfig(tp, process)),
      processToolbarConfig.topRight.filterNot(tp => verifyCondition(tp.hidden, process)).map(tp => ToolbarPanel.fromConfig(tp, process)),
      processToolbarConfig.bottomRight.filterNot(tp => verifyCondition(tp.hidden, process)).map(tp => ToolbarPanel.fromConfig(tp, process))
    )
}

@JsonCodec
case class ProcessToolbarSettings(id: String, topLeft: List[ToolbarPanel], bottomLeft: List[ToolbarPanel], topRight: List[ToolbarPanel], bottomRight: List[ToolbarPanel])

object ToolbarPanel {

  import ToolbarHelper._

  def apply(`type`: ToolbarPanelType, title: Option[String], buttonsVariant: Option[ToolbarButtonVariant], buttons: Option[List[ToolbarButton]]): ToolbarPanel =
    ToolbarPanel(`type`.toString, title, buttonsVariant, buttons)

  def fromConfig(config: ToolbarPanelConfig, process: BaseProcessDetails[_]): ToolbarPanel =
    ToolbarPanel(
      config.identity,
      config.title.map(t => fillByProcessData(t, process)),
      config.buttonsVariant,
      config.buttons.map(buttons =>
        buttons
          .filterNot(button => {
            verifyCondition(button.hidden, process)
          })
          .map(button => ToolbarButton.fromConfig(button, process))
      )
    )
}

@JsonCodec
case class ToolbarPanel(id: String, title: Option[String], buttonsVariant: Option[ToolbarButtonVariant], buttons: Option[List[ToolbarButton]])

object ToolbarButton {

  import ToolbarHelper._

  def fromConfig(config: ToolbarButtonConfig, process: BaseProcessDetails[_]): ToolbarButton = ToolbarButton(
    config.`type`,
    config.name.map(t => fillByProcessData(t, process)),
    config.title.map(t => fillByProcessData(t, process)),
    config.icon.map(i => fillByProcessData(i, process)),
    config.url.map(th => fillByProcessData(th, process)),
    disabled = verifyCondition(config.disabled, process)
  )
}

@JsonCodec
case class ToolbarButton(`type`: ToolbarButtonType, name: Option[String], title: Option[String], icon: Option[String], url: Option[String], disabled: Boolean)

private [service] object ToolbarHelper {

  def createProcessToolbarId(config: ProcessToolbarsConfig, process: BaseProcessDetails[_]): String =
    s"${config.uuidCode}-${if(process.isArchived) "archived" else "not-archived"}-${if(process.isSubprocess) "subprocess" else "process"}"

  def fillByProcessData(text: String, process: BaseProcessDetails[_]): String =
    text
      .replace("{{processName}}", process.name)
      .replace("{{processId}}", process.processId.value.toString)

  def verifyCondition(condition: Option[ToolbarCondition], process: BaseProcessDetails[_]): Boolean = {
    condition.nonEmpty && condition.exists(con => {
      if (con.shouldMatchAllOfConditions) {
        verifySubprocessCondition(con, process) && verifyArchivedCondition(con, process)
      } else {
        verifySubprocessCondition(con, process) || verifyArchivedCondition(con, process)
      }
    })
  }

  private def verifySubprocessCondition(condition: ToolbarCondition, process: BaseProcessDetails[_]) =
    verifyCondition(process.isSubprocess, condition.subprocess,condition.shouldMatchAllOfConditions)

  private def verifyArchivedCondition(condition: ToolbarCondition, process: BaseProcessDetails[_]) =
    verifyCondition(process.isArchived, condition.archived, condition.shouldMatchAllOfConditions)

  //When we should match all conditions and expected condition is empty (not set) then we ignore this condition
  private def verifyCondition(toVerify: Boolean, expected: Option[Boolean], shouldMatchAllOfConditions: Boolean): Boolean =
    (shouldMatchAllOfConditions && expected.isEmpty) || expected.exists(_.equals(toVerify))
}
