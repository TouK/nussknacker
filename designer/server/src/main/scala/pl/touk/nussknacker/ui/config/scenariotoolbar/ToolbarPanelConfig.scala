package pl.touk.nussknacker.ui.config.scenariotoolbar

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarButtonsConfigVariant.ToolbarButtonVariant
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarPanelTypeConfig.ToolbarPanelType

object ToolbarButtonsConfigVariant extends Enumeration {
  implicit val variantEncoder: Encoder[ToolbarButtonVariant] = Encoder.encodeEnumeration(ToolbarButtonsConfigVariant)
  implicit val variantDecoder: Decoder[ToolbarButtonVariant] = Decoder.decodeEnumeration(ToolbarButtonsConfigVariant)

  type ToolbarButtonVariant = Value

  val Label: ToolbarButtonVariant = Value("label")
  val Small: ToolbarButtonVariant = Value("small")
}

object ToolbarPanelTypeConfig extends Enumeration {
  implicit val typeEncoder: Encoder[ToolbarPanelType] = Encoder.encodeEnumeration(ToolbarPanelTypeConfig)
  implicit val typeDecoder: Decoder[ToolbarPanelType] = Decoder.decodeEnumeration(ToolbarPanelTypeConfig)

  type ToolbarPanelType = Value

  private lazy val toolbarsWithButtons: List[ToolbarPanelType] = List(
    ProcessActionsPanel,
    ButtonsPanel
  )

  private lazy val toolbarsWithIdentity: List[ToolbarPanelType] = List(
    ButtonsPanel
  )

  val SearchPanel: Value         = Value("search-panel")
  val TipsPanel: Value           = Value("tips-panel")
  val StickyNotesPanel: Value    = Value("sticky-notes-panel")
  val CreatorPanel: Value        = Value("creator-panel")
  val ProcessInfoPanel: Value    = Value("process-info-panel")
  val ProcessActionsPanel: Value = Value("process-actions-panel")
  val ButtonsPanel: Value        = Value("buttons-panel")
  val ActivitiesPanel: Value     = Value("activities-panel")

  // Some of panels require buttons not empty list param, this method verify that..
  def requiresButtonsParam(`type`: ToolbarPanelType): Boolean =
    toolbarsWithButtons.contains(`type`)

  // Some of panels require id param, this method verify that..
  def requiresIdParam(`type`: ToolbarPanelType): Boolean =
    toolbarsWithIdentity.contains(`type`)
}

final case class ToolbarPanelConfig(
    `type`: ToolbarPanelType,
    id: Option[String],
    title: Option[String],
    buttonsVariant: Option[ToolbarButtonVariant],
    buttons: Option[List[ToolbarButtonConfig]],
    hidden: Option[ToolbarCondition],
    // for custom toolbar components
    additionalParams: Option[Map[String, String]]
) {

  if (ToolbarPanelTypeConfig.requiresIdParam(`type`)) {
    require(id.exists(_.nonEmpty), s"Toolbar ${`type`} requires param: 'id'.")
  } else {
    require(id.isEmpty, s"Toolbar ${`type`} doesn't contain param: 'id'.")
  }

  if (ToolbarPanelTypeConfig.requiresButtonsParam(`type`)) {
    require(buttons.exists(_.nonEmpty), s"Toolbar ${`type`} requires non empty param: 'buttons'.")
  } else {
    require(buttons.isEmpty, s"Toolbar ${`type`} doesn't contain param: 'buttons'.")
    require(buttonsVariant.isEmpty, s"Toolbar ${`type`} doesn't contain param: 'buttonsVariant'.")
  }

  def identity: String = id.getOrElse(`type`.toString)
}
