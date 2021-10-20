package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

import scala.collection.JavaConverters._

trait ProcessCategoryService {

  //TODO: Replace it by VO
  type Category = String

  //It's temporary solution - category will be separated from process type
  def getTypeForCategory(category: Category) : Option[ProcessingType]
  def getAllCategories : List[Category]
}

class ConfigProcessCategoryService(config: Config) extends ProcessCategoryService {

  private val categoryConfigPath = "categoriesConfig"

  private val categoriesToTypesMap: Map[Category, ProcessingType] = {
    val categories = config.getConfig(categoryConfigPath)
    categories.entrySet().asScala.map(_.getKey).map(category => category -> categories.getString(category)).toMap
  }

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    categoriesToTypesMap.get(category)

  //We assume there can't be more categories then config returns
  val getAllCategories: List[Category] =
    categoriesToTypesMap.keys.toList.sorted

}
