package pl.touk.nussknacker.ui.process

import cats.implicits.toFoldableOps
import cats.instances.map._
import cats.instances.set._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.CategoriesConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.process.ProcessCategoryService.{Category, CategoryNotFoundError}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.jdk.CollectionConverters._

object ProcessCategoryService {
  // TODO: Replace it by VO
  type Category = String

  class CategoryNotFoundError(category: Category) extends BadRequestError(s"Category: $category not found")
}

trait ProcessCategoryService {
  def getTypeForCategoryUnsafe(category: Category): ProcessingType =
    getTypeForCategory(category).getOrElse(throw new CategoryNotFoundError(category))
  def getTypeForCategory(category: Category): Option[ProcessingType]
  def getAllCategories: List[Category]
  def getProcessingTypeCategories(processingType: ProcessingType): List[Category]
}

class UserCategoryService(categoriesService: ProcessCategoryService) {

  // We assume that Read is always added when user has access to Write / Deploy / etc..
  def getUserCategories(user: LoggedUser): List[Category] = {
    val allCategories = categoriesService.getAllCategories
    if (user.isAdmin) allCategories else allCategories.filter(user.can(_, Permission.Read))
  }

  def getUserCategoriesWithType(user: LoggedUser): Map[Category, ProcessingType] = {
    getUserCategories(user)
      .map(category => category -> categoriesService.getTypeForCategoryUnsafe(category))
      .toMap
  }

}

object ConfigProcessCategoryService extends LazyLogging {

  private[process] val categoryConfigPath = "categoriesConfig"

  def apply(config: Config, processingCategories: Map[ProcessingType, CategoriesConfig]): ProcessCategoryService = {
    val isLegacyConfigFormat = config.hasPath(categoryConfigPath)
    if (isLegacyConfigFormat) {
      logger.warn(
        s"Legacy $categoryConfigPath format detected. Please replace it by categories defined inside scenarioTypes. " +
          "In the further versions the support for the old format will be removed"
      )
      new LegacyConfigProcessCategoryService(config)
    } else {
      createServiceUsingNewFormat(processingCategories)
    }
  }

  private def createServiceUsingNewFormat(processingCategories: Map[ProcessingType, CategoriesConfig]) = {
    val (processingTypesWithDefinedCategories, notDefinedCategoriesProcessingTypes) = processingCategories.toList.map {
      case (processingType, CategoriesConfig(Some(categories))) =>
        (Map(processingType -> categories), Set.empty[ProcessingType])
      case (processingTypeWithoutCategories, CategoriesConfig(None)) =>
        (Map.empty[ProcessingType, List[Category]], Set(processingTypeWithoutCategories))
    }.combineAll

    if (notDefinedCategoriesProcessingTypes.nonEmpty) {
      throw new IllegalArgumentException(
        s"Illegal categories configuration format. Some scenario types has no categories configured: $notDefinedCategoriesProcessingTypes"
      )
    } else {
      createProcessingTypeCategoryService(processingTypesWithDefinedCategories)
    }
  }

  private def createProcessingTypeCategoryService(
      scenarioTypesConfig: Map[ProcessingType, List[Category]]
  ) = {
    checkCategoryToProcessingTypeMappingAmbiguity(scenarioTypesConfig)
    new ProcessingTypeCategoryService(scenarioTypesConfig)
  }

  // TODO: this is temporary, after fully switch to paradigms we should replace restriction that category
  //       implies processing type with more lax restriction that category + paradigm + engine type
  //       implies processing type
  private def checkCategoryToProcessingTypeMappingAmbiguity(
      scenarioTypesConfig: Map[ProcessingType, List[Category]]
  ): Unit = {
    val processingTypesForEachCategory = scenarioTypesConfig.toList
      .flatMap { case (processingType, categories) =>
        categories.map(_ -> processingType)
      }
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toSet)
    val ambiguousCategoryToProcessingTypeMappings: Map[Category, Set[ProcessingType]] =
      processingTypesForEachCategory.filter(_._2.size > 1)
    if (ambiguousCategoryToProcessingTypeMappings.nonEmpty)
      throw CategoryToProcessingTypeMappingAmbiguousException(ambiguousCategoryToProcessingTypeMappings)
  }

  private[process] final case class CategoryToProcessingTypeMappingAmbiguousException(
      ambiguousCategoryToProcessingTypeMappings: Map[Category, Set[ProcessingType]]
  ) extends IllegalStateException(
        s"Ambiguous category to scenario type mapping detected for categories: $ambiguousCategoryToProcessingTypeMappings"
      )

}

class ProcessingTypeCategoryService(scenarioTypes: Map[String, List[Category]]) extends ProcessCategoryService {
  override def getAllCategories: List[Category] = scenarioTypes.values.flatten.toList.distinct.sorted

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    scenarioTypes.find(_._2.contains(category)).map(_._1)

  override def getProcessingTypeCategories(processingType: ProcessingType): List[Category] =
    scenarioTypes(processingType)

}

class LegacyConfigProcessCategoryService(config: Config) extends ProcessCategoryService {

  private val categoriesToTypesMap: Map[Category, ProcessingType] = {
    val categories = config.getConfig(ConfigProcessCategoryService.categoryConfigPath)
    categories.entrySet().asScala.map(_.getKey).map(category => category -> categories.getString(category)).toMap
  }

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    categoriesToTypesMap.get(category)

  // We assume there can't be more categories then config returns
  val getAllCategories: List[Category] =
    categoriesToTypesMap.keys.toList.sorted

  override def getProcessingTypeCategories(processingType: ProcessingType): List[Category] =
    categoriesToTypesMap.filter { case (_, procType) => procType.equals(processingType) }.keys.toList.sorted

}
