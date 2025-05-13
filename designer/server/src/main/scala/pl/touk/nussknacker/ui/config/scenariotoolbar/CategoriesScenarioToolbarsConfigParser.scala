package pl.touk.nussknacker.ui.config.scenariotoolbar

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object CategoriesScenarioToolbarsConfigParser extends LazyLogging {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  import ScenarioToolbarsConfig._

  // TODO: process -> scenario
  private val defaultScenarioToolbarConfigPath  = "processToolbarConfig.defaultConfig"
  private val categoryScenarioToolbarConfigPath = "processToolbarConfig.categoryConfig"

  /**
    * Merging arrays in typesafe Config is done in primitive way - we can only override arrays of config.. So if
    * we want add / remove panel or button then we have to put base config with changes..
    *
    * TODO: Figure how to better do merging configs..
    */
  def parse(config: Config): CategoriesScenarioToolbarsConfig = {
    val defaultConfig     = config.getConfig(defaultScenarioToolbarConfigPath)
    val perCategoryConfig = config.getAs[Map[String, Config]](categoryScenarioToolbarConfigPath).getOrElse(Map.empty)
    val parsedPerCategoryConfig = perCategoryConfig.mapValuesNow { categoryConfig =>
      categoryConfig.withFallback(defaultConfig).as[ScenarioToolbarsConfig]
    }
    val parsedDefaultConfig = defaultConfig.as[ScenarioToolbarsConfig]
    new CategoriesScenarioToolbarsConfig(parsedDefaultConfig, parsedPerCategoryConfig)
  }

}

class CategoriesScenarioToolbarsConfig(
    defaultConfig: ScenarioToolbarsConfig,
    categoryConfig: Map[String, ScenarioToolbarsConfig]
) {
  def getConfig(category: String): ScenarioToolbarsConfig = categoryConfig.getOrElse(category, defaultConfig)
}
