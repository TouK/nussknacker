package pl.touk.nussknacker.ui.config.processtoolbar

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonConfigType.ToolbarButtonType

case class ToolbarButtonConfig(
  `type`: ToolbarButtonType,
  title: Option[String],
  icon: Option[String],
  templateHref: Option[String],
  hide: Option[ToolbarCondition],
  disabled: Option[ToolbarCondition]
) {

  if (ToolbarButtonConfigType.requiresHrefTemplate(`type`)) {
    require(templateHref.isDefined, s"Button ${`type`} requires param: 'templateHref'.")
  }
}

object ToolbarButtonConfigType extends Enumeration {
  implicit val typeEncoder: Encoder[ToolbarButtonType] = Encoder.enumEncoder(ToolbarButtonConfigType)
  implicit val typeDecoder: Decoder[ToolbarButtonType] = Decoder.enumDecoder(ToolbarButtonConfigType)

  type ToolbarButtonType = Value

  private lazy val buttonsWithHrefTemplate: List[ToolbarButtonType] = List(
    CustomAction,
    CustomLink
  )

  val ProcessSave: Value = Value("process-save")
  val ProcessCancel: Value = Value("process-cancel")
  val ProcessDeploy: Value = Value("process-deploy")

  val EditUndo: Value = Value("edit-undo")
  val EditRedo: Value = Value("edit-redo")
  val EditCopy: Value = Value("edit-copy")
  val EditPaste: Value = Value("edit-paste")
  val EditDelete: Value = Value("edit-delete")
  val EditLayout: Value = Value("edit-layout")

  val ProcessProperties: Value = Value("process-properties")
  val ProcessCompare: Value = Value("process-compare")
  val ProcessMigrate: Value = Value("process-migrate")
  val ProcessImport: Value = Value("process-import")
  val ProcessJSON: Value = Value("process-json")
  val ProcessPDF: Value = Value("process-pdf")

  val ProcessArchiveToggle: Value = Value("process-archive-toggle")
  val ProcessArchive: Value = Value("process-archive")
  val ProcessUnarchive: Value = Value("process-unarchive")

  val Group: Value = Value("group")
  val Ungroup: Value = Value("ungroup")

  val TestFromFile: Value = Value("test-from-file")
  val TestGenerate: Value = Value("test-generate")
  val TestCounts: Value = Value("test-counts")
  val TestHide: Value = Value("test-hide")

  val ViewBusinessView: Value = Value("view-business-view")
  val ViewZoomIn: Value = Value("view-zoom-in")
  val ViewZoomOut: Value = Value("view-zoom-out")
  val ViewReset: Value = Value("view-reset")

  val CustomLink: Value = Value("custom-link")
  val CustomAction: Value = Value("custom-action")

  def requiresHrefTemplate(`type`: ToolbarButtonType): Boolean =
    buttonsWithHrefTemplate.contains(`type`)
}
