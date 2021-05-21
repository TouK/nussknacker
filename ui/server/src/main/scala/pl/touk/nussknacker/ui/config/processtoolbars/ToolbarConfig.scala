package pl.touk.nussknacker.ui.config.processtoolbars

import com.typesafe.config.Config
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.config.processtoolbars.ToolbarButtonVariant.ToolbarButtonVariant


@JsonCodec
case class ProcessAndSubprocessToolbarsConfig(processConfig: CategoriesProcessToolbarsConfig,
                                              subprocessConfig: CategoriesProcessToolbarsConfig)

@JsonCodec
case class CategoriesProcessToolbarsConfig(defaultConfig: ProcessToolbarsConfig,
                                           categoriesConfigs: Map[String, ProcessToolbarsConfig])

@JsonCodec
case class ProcessToolbarsConfig(topRight: Option[List[ProcessToolbars]],
                                 bottomRight: Option[List[ProcessToolbars]],
                                 opLeft: Option[List[ProcessToolbars]],
                                 bottomLeft: Option[List[ProcessToolbars]],
                                 hidden: Option[List[ProcessToolbars]])

@JsonCodec
case class ProcessToolbars(id: String,
                           title: Option[String],
                           buttonsVariant: Option[ToolbarButtonVariant],
                           buttons: Option[List[ToolbarButton]])


object ProcessAndSubprocessToolbarsConfig {
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.EnumerationReader._

  private val processAndSubprocessToolbarsConfigPath = "processAndSubprocessToolbarsConfig"

  def create(config: Config): ProcessAndSubprocessToolbarsConfig =
    config.as[ProcessAndSubprocessToolbarsConfig](processAndSubprocessToolbarsConfigPath)
}


