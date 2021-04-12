package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.config.ToolbarButtonType.{CustomLink, ToolbarButtonType}
import pl.touk.nussknacker.ui.config.ToolbarButtonVariant.ToolbarButtonVariant

object ToolbarButtonVariant extends Enumeration {
  implicit val variantEncoder: Encoder[ToolbarButtonVariant] = Encoder.enumEncoder(ToolbarButtonVariant)
  implicit val variantDecoder: Decoder[ToolbarButtonVariant] = Decoder.enumDecoder(ToolbarButtonVariant)

  type ToolbarButtonVariant = Value

  val Labeled: ToolbarButtonVariant = Value("labeled")
  val Small: ToolbarButtonVariant = Value("small")
}

@JsonCodec
case class ProcessToolbars(id: String, title: Option[String], buttonsVariant: Option[ToolbarButtonVariant], buttons: Option[List[ToolbarButton]])

object ToolbarButtonType extends Enumeration {
  implicit val typeEncoder: Encoder[ToolbarButtonType] = Encoder.enumEncoder(ToolbarButtonType)
  implicit val typeDecoder: Decoder[ToolbarButtonType] = Decoder.enumDecoder(ToolbarButtonType)

  type ToolbarButtonType = Value

  val ProcessSave: Value = Value("process-save")

  val ProcessActionDeploy: Value = Value("process-action-deploy")
  val ProcessActionCancel: Value = Value("process-action-cancel")
  val ProcessActionArchive: Value = Value("process-action-archive")
  val ProcessActionUnArchive: Value = Value("process-action-unarchive")

  val CustomLink: Value = Value("custom-link")

  val ViewBusiness: Value = Value("view-business")
  val ViewZoomIn: Value = Value("view-zoom-in")
  val ViewZoomOut: Value = Value("view-zoom-out")
  val ViewReset: Value = Value("view-reset")

  val EditUndo: Value = Value("edit-undo")
  val EditRedo: Value = Value("edit-redo")
  val EditCopy: Value = Value("edit-copy")
  val EditPaste: Value = Value("edit-paste")
  val EditDelete: Value = Value("edit-delete")
  val EditResetLayout: Value = Value("edit-reset-layout")

  val ProcessProperties: Value = Value("process-properties")
  val ProcessCompare: Value = Value("process-compare")
  val ProcessMigrate: Value = Value("process-migrate")
  val ProcessImport: Value = Value("process-import")
  val ProcessJson: Value = Value("process-json")
  val ProcessPdf: Value = Value("process-pdf")

  val TestFromFile: Value = Value("test-from-file")
  val TestGenerate: Value = Value("test-generate")
  val TestCounts: Value = Value("test-counts")
  val TestHideCounts: Value = Value("test-hide-counts")

  val GroupStart: Value = Value("group-start")
  val GroupFinish: Value = Value("group-finish")
  val GroupCancel: Value = Value("group-cancel")
  val GroupUngroup: Value = Value("group-ungroup")

  val buttonsWithHrefTemplate = List(
    CustomLink
  )

  def requiresHrefTemplate(`type`: ToolbarButtonType): Boolean =
    buttonsWithHrefTemplate.contains(`type`)
}

@JsonCodec
case class ToolbarButton(`type`: ToolbarButtonType, title: Option[String], icon: Option[String], templateHref: Option[String]) {
  if (ToolbarButtonType.requiresHrefTemplate(`type`)) {
    assert(templateHref.isDefined, s"Button ${`type`} requires param: 'templateHref'.")
  }
}

@JsonCodec
case class DefaultProcessToolbarsConfig(topRight: List[ProcessToolbars],
                                        bottomRight: List[ProcessToolbars],
                                        topLeft: List[ProcessToolbars],
                                        bottomLeft: List[ProcessToolbars],
                                        hidden: List[ProcessToolbars])


object DefaultProcessToolbarsConfig {
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.EnumerationReader._

  val defaultProcessToolbarsConfigPath = "defaultProcessToolbars"

  def create(config: Config): DefaultProcessToolbarsConfig =
    config.as[DefaultProcessToolbarsConfig](defaultProcessToolbarsConfigPath)
}
