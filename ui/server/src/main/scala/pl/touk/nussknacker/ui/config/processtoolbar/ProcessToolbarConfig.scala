package pl.touk.nussknacker.ui.config.processtoolbar

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}

import java.util.UUID

object ProcessToolbarsConfig {
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  // scala 2.11 needs it
  implicit val optionConditionReader: ValueReader[Option[ToolbarCondition]] = new ValueReader[Option[ToolbarCondition]] {
    override def read(config: Config, path: String): Option[ToolbarCondition] =
      OptionReader.optionValueReader[ToolbarCondition].read(config, path)
  }

  // scala 2.11 needs it
  implicit val optionButtonsListReader: ValueReader[Option[List[ToolbarButtonConfig]]] = new ValueReader[Option[List[ToolbarButtonConfig]]] {
    override def read(config: Config, path: String): Option[List[ToolbarButtonConfig]] =
      OptionReader.optionValueReader[List[Config]].read(config, path).map(_.map(_.as[ToolbarButtonConfig]))
  }

  //It provides empty list when toolbar panel is not set
  implicit val panelListReader: ValueReader[List[ToolbarPanelConfig]] = new ValueReader[List[ToolbarPanelConfig]] {
    override def read(config: Config, path: String): List[ToolbarPanelConfig] = {
      config.getOrElse[List[Config]](path, List.empty).map(_.as[ToolbarPanelConfig])
    }
  }
}

case class ProcessToolbarsConfig(uuid: Option[UUID], topLeft: List[ToolbarPanelConfig], bottomLeft: List[ToolbarPanelConfig], topRight: List[ToolbarPanelConfig], bottomRight: List[ToolbarPanelConfig]) {
  lazy val uuidCode: UUID = uuid.getOrElse(UUID.nameUUIDFromBytes(hashCode().toString.getBytes))
}

object ProcessToolbarsConfigProvider extends LazyLogging {
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import ProcessToolbarsConfig._

  private val defaultProcessToolbarConfigPath = "processToolbarConfig.defaultConfig"
  private val categoryProcessToolbarConfigPath = "processToolbarConfig.categoryConfig"

  /**
    * Merging arrays in typesafe Config is done in primitive way - we can only override arrays of config.. So if
    * we want add / remove panel or button then we have to put base config with changes..
    *
    * TODO: Figure how to better do merging configs..
    */
  def create(config: Config, category: Option[String]): ProcessToolbarsConfig = {
    val defaultConfig = config.getConfig(defaultProcessToolbarConfigPath)

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
