package pl.touk.nussknacker.ui.process

import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

import scala.collection.JavaConverters._
import net.ceedubs.ficus.Ficus._

trait ProcessCategoryService {
  def getTypeForCategory(category: String) : Option[ProcessingType]
  def getAllCategories : List[ProcessingType]
}

class ConfigProcessCategoryService(config: Config) extends ProcessCategoryService {

  private val defaultCategoriesConfig = ConfigFactory.parseMap(Collections.singletonMap("Default", "streaming"))

  private val categoryConfigPath = "categoriesConfig"

  private val categoriesToTypesMap = {
    val categories = config.getOrElse(categoryConfigPath, defaultCategoriesConfig)
    categories.entrySet().asScala.map(_.getKey).map(category => category -> categories.getString(category)).toMap
  }

  override def getTypeForCategory(category: String) : Option[ProcessingType] =
    categoriesToTypesMap.get(category)

  val getAllCategories: List[ProcessingType] =
    categoriesToTypesMap.keys.toList
}