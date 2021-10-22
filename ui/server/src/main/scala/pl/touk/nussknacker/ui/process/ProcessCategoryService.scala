package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.api.Permission.Read

import scala.collection.JavaConverters._

object  ProcessCategoryService{
  //TODO: Replace it by VO
  type Category = String
}

trait ProcessCategoryService {
  //It's temporary solution - category will be separated from process type
  def getTypeForCategory(category: Category) : Option[ProcessingType]
  def getAllCategories : List[Category]
  def getUserCategories(user: LoggedUser) : List[Category]
  def getProcessingTypeCategories(processingType: ProcessingType) : List[Category]
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

  //We assume that Read is always added when user has access to Write / Deploy / etc..
  override def getUserCategories(user: LoggedUser): List[Category] =
    if (user.isAdmin) getAllCategories else getAllCategories.filter(user.can(_, Read))

  override def getProcessingTypeCategories(processingType: ProcessingType): List[Category] =
    categoriesToTypesMap.filter{case (_, procType) => procType.equals(processingType)}.keys.toList.sorted
}
