package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.restmodel.{NuIcon, NuLink}
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonConfigType.ToolbarButtonType
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonsConfigVariant.ToolbarButtonVariant
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarPanelTypeConfig.ToolbarPanelType
import pl.touk.nussknacker.ui.config.processtoolbar._

import java.net.URI

trait ProcessToolbarService {
  def getProcessToolbarSettings(process: BaseProcessDetails[_]): ProcessToolbarSettings
}

class ConfigProcessToolbarService(config: Config, categories: List[String]) extends ProcessToolbarService {

  private val categoriesProcessToolbarConfig: Map[String, ProcessToolbarsConfig] =
    categories
      .map(category => category -> ProcessToolbarsConfigProvider.create(config, Some(category)))
      .toMap

  override def getProcessToolbarSettings(process: BaseProcessDetails[_]): ProcessToolbarSettings = {
    val toolbarConfig = categoriesProcessToolbarConfig.getOrElse(process.processCategory,
      throw new IllegalArgumentException(s"Try to get scenario toolbar settings for not existing category: ${process.processCategory}. Available categories: ${categoriesProcessToolbarConfig.keys.mkString(",")}.")
    )

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

  implicit def encoder(implicit linkEncoder: Encoder[NuLink]): Encoder[ProcessToolbarSettings] = deriveEncoder[ProcessToolbarSettings]

}

case class ProcessToolbarSettings(id: String, topLeft: List[ToolbarPanel], bottomLeft: List[ToolbarPanel], topRight: List[ToolbarPanel], bottomRight: List[ToolbarPanel])

object ToolbarPanel {

  implicit def encoder(implicit linkEncoder: Encoder[NuLink]): Encoder[ToolbarPanel] = deriveEncoder[ToolbarPanel]

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

case class ToolbarPanel(id: String, title: Option[String], buttonsVariant: Option[ToolbarButtonVariant], buttons: Option[List[ToolbarButton]])

object ToolbarButton {

  import ToolbarHelper._

  def fromConfig(config: ToolbarButtonConfig, process: BaseProcessDetails[_]): ToolbarButton = ToolbarButton(
    config.`type`,
    config.name.map(t => fillByProcessData(t, process)),
    config.title.map(t => fillByProcessData(t, process)),
    config.icon.map(i => NuIcon(URI.create(fillByProcessData(i, process, urlOption = true)))),
    config.url.map(th => NuLink(URI.create(fillByProcessData(th, process, urlOption = true)))),
    disabled = verifyCondition(config.disabled, process)
  )

  implicit def encoder(implicit linkEncoder: Encoder[NuLink]): Encoder[ToolbarButton] = deriveEncoder[ToolbarButton]
}

case class ToolbarButton(`type`: ToolbarButtonType, name: Option[String], title: Option[String], icon: Option[NuIcon], url: Option[NuLink], disabled: Boolean)

private [process] object ToolbarHelper {

  def createProcessToolbarId(config: ProcessToolbarsConfig, process: BaseProcessDetails[_]): String =
    s"${config.uuidCode}-${if(process.isArchived) "archived" else "not-archived"}-${if(process.isSubprocess) "fragment" else "scenario"}"

  def fillByProcessData(text: String, process: BaseProcessDetails[_], urlOption: Boolean = false): String = {
    val processName = if (urlOption) UriUtils.encodeURIComponent(process.name) else process.name

    text
      .replace("$processName", processName)
      .replace("$processId", process.processId.value.toString)
  }

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
