package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService.ScenarioTypeConfiguration
import pl.touk.nussknacker.ui.process.ProcessCategoryService.{Category, CategoryNotFoundError}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.jdk.CollectionConverters._

object ProcessCategoryService {
  // TODO: Replace it by VO
  type Category = String

  class CategoryNotFoundError(category: Category)
      extends Exception(s"Category: $category not found")
      with BadRequestError

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

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  private[process] val categoryConfigPath = "categoriesConfig"
  private val scenarioTypesConfigPath     = "scenarioTypes"

  def apply(config: Config): ProcessCategoryService = {
    val isLegacyConfigFormat = config.hasPath(categoryConfigPath)
    if (isLegacyConfigFormat) {
      logger.warn(
        s"Legacy $categoryConfigPath format detected. Please replace it by categories defined inside $scenarioTypesConfigPath. " +
          "In the further versions the support for the old format will be removed"
      )
      new LegacyConfigProcessCategoryService(config)
    } else {
      config
        .getAs[Map[ProcessingType, ScenarioTypeConfiguration]](scenarioTypesConfigPath)
        .map(createProcessingTypeCategoryService)
        .getOrElse(
          throw new IllegalArgumentException(
            s"Illegal configuration format - no $scenarioTypesConfigPath path or scenario types without defined categories"
          )
        )
    }
  }

  private def createProcessingTypeCategoryService(
      scenarioTypesConfig: Map[ProcessingType, ScenarioTypeConfiguration]
  ) = {
    checkCategoryToProcessingTypeMappingAmbiguity(scenarioTypesConfig)
    new ProcessingTypeCategoryService(scenarioTypesConfig)
  }

  // TODO: this is temporary, after fully switch to paradigms we should replace restriction that category
  //       implies processing type with more lax restriction that category + paradigm + engine type
  //       implies processing type
  private def checkCategoryToProcessingTypeMappingAmbiguity(
      scenarioTypesConfig: Map[ProcessingType, ScenarioTypeConfiguration]
  ): Unit = {
    val processingTypesForEachCategory = scenarioTypesConfig.toList
      .flatMap { case (processingType, config) =>
        config.categories.map(_ -> processingType)
      }
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toSet)
    val ambiguousCategoryToProcessingTypeMappings: Map[Category, Set[ProcessingType]] =
      processingTypesForEachCategory.filter(_._2.size > 1)
    if (ambiguousCategoryToProcessingTypeMappings.nonEmpty)
      throw CategoryToProcessingTypeMappingAmbiguousException(ambiguousCategoryToProcessingTypeMappings)
  }

  private[process] final case class ScenarioTypeConfiguration(categories: List[Category])

  private[process] final case class CategoryToProcessingTypeMappingAmbiguousException(
      ambiguousCategoryToProcessingTypeMappings: Map[Category, Set[ProcessingType]]
  ) extends IllegalStateException(
        s"Ambiguous category to scenario type mapping detected for categories: $ambiguousCategoryToProcessingTypeMappings"
      )

}

class ProcessingTypeCategoryService(scenarioTypes: Map[String, ScenarioTypeConfiguration])
    extends ProcessCategoryService {
  override def getAllCategories: List[Category] = scenarioTypes.values.flatMap(_.categories).toList.distinct.sorted

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    scenarioTypes.find(_._2.categories.contains(category)).map(_._1)

  override def getProcessingTypeCategories(processingType: ProcessingType): List[Category] =
    scenarioTypes(processingType).categories

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
