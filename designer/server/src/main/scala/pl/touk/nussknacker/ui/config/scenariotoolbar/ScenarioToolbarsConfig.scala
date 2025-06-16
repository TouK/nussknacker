package pl.touk.nussknacker.ui.config.scenariotoolbar

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.config.Implicits.ConfigExt
import pl.touk.nussknacker.ui.config.scenariotoolbar.ScenarioToolbarsConfig.PanelsAreaName
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarConditionType.ToolbarConditionType
import pl.touk.nussknacker.ui.process.DocsButtonConfig

import java.util.UUID

object ScenarioToolbarsConfig {

  type PanelsAreaName = String

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  implicit val scenarioToolbarConfigValueReader: ValueReader[ScenarioToolbarsConfig] = {
    Ficus.configValueReader.map(ScenarioToolbarsConfig.parse)
  }

  def parse(rawConfig: Config): ScenarioToolbarsConfig = {
    val uuid             = rawConfig.getAs[UUID]("uuid")
    val panelAreasConfig = rawConfig.withoutPath("uuid").rootAs[Map[PanelsAreaName, List[ToolbarPanelConfig]]]
    ScenarioToolbarsConfig(uuid, panelAreasConfig)
  }

}

// It is a case class to make hashCode working correctly - see uuidCode
final case class ScenarioToolbarsConfig(
    private val uuid: Option[UUID],
    panelAreasConfig: Map[PanelsAreaName, List[ToolbarPanelConfig]]
) {
  lazy val uuidCode: UUID = uuid.getOrElse(UUID.nameUUIDFromBytes(hashCode().toString.getBytes))
}

final case class ToolbarPanelConfig(
    `type`: String,
    id: Option[String],
    title: Option[String],
    buttonsVariant: Option[String],
    buttons: Option[List[ToolbarButtonConfig]],
    hidden: Option[ToolbarCondition],
    // for custom toolbar components
    additionalParams: Option[Map[String, String]]
) {

  def identity: String = id.getOrElse(`type`)
}

final case class ToolbarButtonConfig(
    `type`: String,
    name: Option[String],
    title: Option[String],
    titleForUserRole: Option[Map[String, String]],
    icon: Option[String],
    url: Option[String],
    hidden: Option[ToolbarCondition],
    disabled: Option[ToolbarCondition],
    markdownContent: Option[String] = None,
    docs: Option[DocsButtonConfig] = None
)

object ToolbarConditionType extends Enumeration {

  type ToolbarConditionType = Value

  val OneOf: Value = Value("oneof")
  val AllOf: Value = Value("allof")

  def isAllOf(`type`: ToolbarConditionType): Boolean =
    `type`.equals(AllOf)
}

final case class ToolbarCondition(
    fragment: Option[Boolean],
    archived: Option[Boolean],
    always: Option[Boolean],
    oneOfUserRoles: Option[Set[String]],
    `type`: Option[ToolbarConditionType]
) {
  def shouldMatchAllOfConditions: Boolean = `type`.exists(ToolbarConditionType.isAllOf)
}
