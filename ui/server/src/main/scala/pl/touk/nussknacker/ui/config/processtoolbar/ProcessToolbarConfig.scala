package pl.touk.nussknacker.ui.config.processtoolbar

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.{CollectionReaders, ValueReader}

import java.util.UUID

object ProcessToolbarsConfig {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  // scala 2.11 needs it
  implicit val panelListReader: ValueReader[List[ToolbarPanelConfig]] =
    (config: Config, path: String) => CollectionReaders.traversableReader[List, ToolbarPanelConfig].read(config, path)
}

case class ProcessToolbarsConfig(uuid: Option[UUID], topLeft: List[ToolbarPanelConfig], bottomLeft: List[ToolbarPanelConfig], topRight: List[ToolbarPanelConfig], bottomRight: List[ToolbarPanelConfig]) {
  lazy val uniqueCode = uuid.map(_.toString).getOrElse(hashCode().toString)
}

object ProcessToolbarsConfigProvider extends LazyLogging {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import ProcessToolbarsConfig._

  private val emptyConfig: Config = ConfigFactory.parseString(
    """
      |{
      |  processToolbarConfig {
      |    defaultConfig {
      |      topLeft: []
      |      bottomLeft: []
      |      topRight: []
      |      bottomRight:[]
      |    }
      |  }
      |  categoryConfig: {}
      |}
      |""".stripMargin
  )

  private val defaultProcessToolbarConfigPath = "processToolbarConfig.defaultConfig"
  private val categoryProcessToolbarConfigPath = "processToolbarConfig.categoryConfig"

  /**
    * TODO: Merging arrays in typesafe Config is done in primitive way - figure how to better do merging configs..
    */
  def create(config: Config, category: Option[String]): ProcessToolbarsConfig = {
    if (!config.hasPath(defaultProcessToolbarConfigPath)) {
      logger.warn(s"Can't load process toolbar config, missing path: $defaultProcessToolbarConfigPath. Loaded empty process toolbar config.")
    }

    val defaultConfig = config
      .withFallback(emptyConfig)
      .getConfig(defaultProcessToolbarConfigPath)

    category
      .map(cat =>
        createCategoryConfig(config, cat)
          .withFallback(defaultConfig)
      )
      .getOrElse(defaultConfig)
      .as[ProcessToolbarsConfig]
  }

  private def createCategoryConfig(config: Config, category: String): Config =
    config.getOrElse(s"$categoryProcessToolbarConfigPath.$category", {
      logger.info(s"Can't load category: $category process toolbar config.")
      ConfigFactory.empty()
    })
}
