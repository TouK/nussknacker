package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

import scala.collection.JavaConverters._

trait ProcessCategoryService {
  //It's temporary solution - category will be separated from process type
  def getTypeForCategory(category: String) : Option[ProcessingType]
  def getAllCategories : List[ProcessingType]
}

class ConfigProcessCategoryService(config: Config) extends ProcessCategoryService {

  private val categoryConfigPath = "categoriesConfig"

  private val categoriesToTypesMap = {
    val categories = config.getConfig(categoryConfigPath)
    categories.entrySet().asScala.map(_.getKey).map(category => category -> categories.getString(category)).toMap
  }

  override def getTypeForCategory(category: String) : Option[ProcessingType] =
    categoriesToTypesMap.get(category)

  val getAllCategories: List[ProcessingType] =
    categoriesToTypesMap.keys.toList
}