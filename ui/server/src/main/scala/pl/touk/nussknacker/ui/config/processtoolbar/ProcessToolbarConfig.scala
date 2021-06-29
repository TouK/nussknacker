package pl.touk.nussknacker.ui.config.processtoolbar

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}

import java.util.UUID

object ProcessToolbarsConfig {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  // scala 2.11 needs it
  implicit val panelListReader: ValueReader[List[ToolbarPanelConfig]] = new ValueReader[List[ToolbarPanelConfig]] {
    override def read(config: Config, path: String): List[ToolbarPanelConfig] =
      OptionReader.optionValueReader[List[ToolbarPanelConfig]].read(config, path).getOrElse(List.empty)
  }
}

case class ProcessToolbarsConfig(uuid: Option[UUID], topLeft: List[ToolbarPanelConfig], bottomLeft: List[ToolbarPanelConfig], topRight: List[ToolbarPanelConfig], bottomRight: List[ToolbarPanelConfig]) {
  lazy val uuidCode: UUID = uuid.getOrElse(UUID.nameUUIDFromBytes(hashCode().toString.getBytes))
}

object ProcessToolbarsConfigProvider extends LazyLogging {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import ProcessToolbarsConfig.panelListReader

  private val emptyConfig: Config = ConfigFactory.parseString("{ topLeft: [], bottomLeft: [], topRight: [], bottomRight:[] }")

  private val defaultProcessToolbarConfigPath = "processToolbarConfig.defaultConfig"
  private val categoryProcessToolbarConfigPath = "processToolbarConfig.categoryConfig"

  /**
    * Merging arrays in typesafe Config is done in primitive way - we can only override arrays of config.. So if
    * we want add / remove panel or button then we have to put base config with changes..
    *
    * TODO: Figure how to better do merging configs..
    */
  def create(config: Config, category: Option[String]): ProcessToolbarsConfig = {
    val defaultConfig = config.getConfig(defaultProcessToolbarConfigPath).withFallback(emptyConfig)

    category
      .flatMap(
        getCategoryConfig(config, _)
          .map(_.withFallback(defaultConfig))
      )
      .getOrElse(defaultConfig)
      .as[ProcessToolbarsConfig]
  }

  private def getCategoryConfig(config: Config, category: String): Option[Config] = {
    val path = s"$categoryProcessToolbarConfigPath.$category"
    if (config.hasPath(path)) {
      Some(config.getConfig(path))
    } else {
      logger.debug(s"Can't load category: $category process toolbar config.")
      None
    }
  }
}
