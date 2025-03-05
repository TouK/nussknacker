package pl.touk.nussknacker.ui.config.scenariotoolbar

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarButtonConfigType.ToolbarButtonType
import pl.touk.nussknacker.ui.process.DocsButtonConfig

final case class ToolbarButtonConfig(
    `type`: ToolbarButtonType,
    name: Option[String],
    title: Option[String],
    icon: Option[String],
    url: Option[String],
    hidden: Option[ToolbarCondition],
    disabled: Option[ToolbarCondition],
    markdownContent: Option[String] = None,
    docs: Option[DocsButtonConfig] = None
) {

  if (ToolbarButtonConfigType.requiresNameParam(`type`)) {
    require(name.isDefined, s"Button ${`type`} requires param: 'name'.")
  }

  if (ToolbarButtonConfigType.requiresUrlParam(`type`)) {
    require(url.isDefined, s"Button ${`type`} requires param: 'url'.")
  } else {
    require(url.isEmpty, s"Button ${`type`} doesn't contain param: 'url'.")
  }

}

object ToolbarButtonConfigType extends Enumeration {
  implicit val typeEncoder: Encoder[ToolbarButtonType] = Encoder.encodeEnumeration(ToolbarButtonConfigType)
  implicit val typeDecoder: Decoder[ToolbarButtonType] = Decoder.decodeEnumeration(ToolbarButtonConfigType)

  type ToolbarButtonType = Value

  private lazy val customButtons: List[ToolbarButtonType] = List(
    CustomLink
  )

  private lazy val urlButtons: List[ToolbarButtonType] = List(
    CustomLink
  )

  val ProcessSave: Value           = Value("process-save")
  val ProcessCancel: Value         = Value("process-cancel")
  val ProcessDeploy: Value         = Value("process-deploy")
  val ProcessRunOffSchedule: Value = Value("process-run-off-schedule")

  val EditUndo: Value   = Value("edit-undo")
  val EditRedo: Value   = Value("edit-redo")
  val EditCopy: Value   = Value("edit-copy")
  val EditPaste: Value  = Value("edit-paste")
  val EditDelete: Value = Value("edit-delete")
  val EditLayout: Value = Value("edit-layout")

  val ProcessProperties: Value = Value("process-properties")
  val ProcessCompare: Value    = Value("process-compare")
  val ProcessMigrate: Value    = Value("process-migrate")
  val ProcessImport: Value     = Value("process-import")
  val ProcessExport: Value     = Value("process-export")
  val ProcessPDF: Value        = Value("process-pdf")

  val ProcessArchiveToggle: Value = Value("process-archive-toggle")
  val ProcessArchive: Value       = Value("process-archive")
  val ProcessUnarchive: Value     = Value("process-unarchive")

  val TestFromFile: Value    = Value("test-from-file")
  val TestGenerate: Value    = Value("test-generate")
  val GenerateAndTest: Value = Value("generate-and-test")
  val AdhocTesting: Value    = Value("adhoc-testing")
  val TestCounts: Value      = Value("test-counts")
  val TestHide: Value        = Value("test-hide")

  val ViewZoomIn: Value  = Value("view-zoom-in")
  val ViewZoomOut: Value = Value("view-zoom-out")
  val ViewReset: Value   = Value("view-reset")

  val CustomLink: Value = Value("custom-link")

  def requiresNameParam(`type`: ToolbarButtonType): Boolean =
    customButtons.contains(`type`)

  def requiresUrlParam(`type`: ToolbarButtonType): Boolean =
    urlButtons.contains(`type`)

}
