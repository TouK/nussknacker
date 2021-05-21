package pl.touk.nussknacker.ui.config.processtoolbars

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.config.processtoolbars.ToolbarButtonType.ToolbarButtonType


@JsonCodec
case class ToolbarButton(`type`: ToolbarButtonType, title: Option[String], icon: Option[String], templateHref: Option[String]) {
  if (ToolbarButtonType.requiresHrefTemplate(`type`)) {
    assert(templateHref.isDefined, s"Button ${`type`} requires param: 'templateHref'.")
  }
}

object ToolbarButtonVariant extends Enumeration {
  implicit val variantEncoder: Encoder[ToolbarButtonVariant] = Encoder.enumEncoder(ToolbarButtonVariant)
  implicit val variantDecoder: Decoder[ToolbarButtonVariant] = Decoder.enumDecoder(ToolbarButtonVariant)

  type ToolbarButtonVariant = Value

  val Label: ToolbarButtonVariant = Value("label")
  val Small: ToolbarButtonVariant = Value("small")
}

object ToolbarButtonType extends Enumeration {
  implicit val typeEncoder: Encoder[ToolbarButtonType] = Encoder.enumEncoder(ToolbarButtonType)
  implicit val typeDecoder: Decoder[ToolbarButtonType] = Decoder.enumDecoder(ToolbarButtonType)

  type ToolbarButtonType = Value

  val ProcessSave: Value = Value("process-save")

  val Deploy: Value = Value("deploy")
  val DeployCancel: Value = Value("deploy-cancel")
  val DeployMetrics: Value = Value("deploy-metrics")

  val EditUndo: Value = Value("edit-undo")
  val EditRedo: Value = Value("edit-redo")
  val EditCopy: Value = Value("edit-copy")
  val EditPaste: Value = Value("edit-paste")
  val EditDelete: Value = Value("edit-delete")
  val EditLayout: Value = Value("edit-layout")

  val Group: Value = Value("group")
  val Ungroup: Value = Value("ungroup")
  val EditProperties: Value = Value("edit-properties")

  val ProcessCompare: Value = Value("process-compare")
  val ProcessMigrate: Value = Value("process-migrate")
  val ProcessImport: Value = Value("process-import")
  val ProcessJSON: Value = Value("process-JSON")
  val ProcessPDF: Value = Value("process-PDF")
  val ProcessArchiveToggle: Value = Value("process-archive-toggle")
  val ProcessArchive: Value = Value("process-archive")
  val ProcessUnarchive: Value = Value("process-unarchive")

  val TestFromFile: Value = Value("test-fromFile")
  val TestGenerate: Value = Value("test-generate")
  val TestCounts: Value = Value("test-counts")
  val TestHide: Value = Value("test-hide")

  val ViewBusinessView: Value = Value("view-businessView")
  val ViewZoomIn: Value = Value("view-zoomIn")
  val ViewZoomOut: Value = Value("view-zoomOut")
  val ViewReset: Value = Value("view-reset")


  val CustomLink: Value = Value("custom-link")


  private val buttonsWithHrefTemplate = List(
    CustomLink
  )

  def requiresHrefTemplate(`type`: ToolbarButtonType): Boolean =
    buttonsWithHrefTemplate.contains(`type`)
}
