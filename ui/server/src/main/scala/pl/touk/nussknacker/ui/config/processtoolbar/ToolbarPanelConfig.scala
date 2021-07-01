package pl.touk.nussknacker.ui.config.processtoolbar

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonsConfigVariant.ToolbarButtonVariant
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarPanelTypeConfig.ToolbarPanelType

object ToolbarButtonsConfigVariant extends Enumeration {
  implicit val variantEncoder: Encoder[ToolbarButtonVariant] = Encoder.enumEncoder(ToolbarButtonsConfigVariant)
  implicit val variantDecoder: Decoder[ToolbarButtonVariant] = Decoder.enumDecoder(ToolbarButtonsConfigVariant)

  type ToolbarButtonVariant = Value

  val Label: ToolbarButtonVariant = Value("label")
  val Small: ToolbarButtonVariant = Value("small")
}

object ToolbarPanelTypeConfig extends Enumeration {
  implicit val typeEncoder: Encoder[ToolbarPanelType] = Encoder.enumEncoder(ToolbarPanelTypeConfig)
  implicit val typeDecoder: Decoder[ToolbarPanelType] = Decoder.enumDecoder(ToolbarPanelTypeConfig)

  type ToolbarPanelType = Value

  private lazy val toolbarsWithButtons: List[ToolbarPanelType] = List(
    ProcessInfoPanel, ButtonsPanel
  )

  private lazy val toolbarsWithIdentity: List[ToolbarPanelType] = List(
    ButtonsPanel
  )

  val TipsPanel: Value = Value("tips-panel")
  val CreatorPanel: Value = Value("creator-panel")
  val VersionsPanel: Value = Value("versions-panel")
  val CommentsPanel: Value = Value("comments-panel")
  val AttachmentsPanel: Value = Value("attachments-panel")
  val ProcessInfoPanel: Value = Value("process-info-panel")
  val ButtonsPanel: Value = Value("buttons-panel")
  val DetailsPanel: Value = Value("details-panel")

  //Some of panels require buttons not empty list param, this method verify that..
  def requiresButtonsParam(`type`: ToolbarPanelType): Boolean =
    toolbarsWithButtons.contains(`type`)

  //Some of panels require id param, this method verify that..
  def requiresIdParam(`type`: ToolbarPanelType): Boolean =
    toolbarsWithIdentity.contains(`type`)
}

case class ToolbarPanelConfig(
  `type`: ToolbarPanelType,
  id: Option[String],
  title: Option[String],
  buttonsVariant: Option[ToolbarButtonVariant],
  buttons: Option[List[ToolbarButtonConfig]],
  hidden: Option[ToolbarCondition]
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
