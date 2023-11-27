package pl.touk.nussknacker.ui.process

import cats.implicits.toFoldableOps
import cats.instances.map._
import cats.instances.set._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.CategoriesConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.process.ProcessCategoryService.{
  Category,
  CategoryNotFoundError,
  ProcessingTypeNotFoundError
}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.jdk.CollectionConverters._

object ProcessCategoryService {
  // TODO: Replace it by VO
  type Category = String

  class CategoryNotFoundError(category: Category) extends BadRequestError(s"Category: $category not found")
  class ProcessingTypeNotFoundError(processingType: ProcessingType)
      extends BadRequestError(s"Processing type: $processingType not found or no categories configured for it")
}

trait ProcessCategoryService {
  final def getTypeForCategoryUnsafe(category: Category): ProcessingType =
    getTypeForCategory(category).getOrElse(throw new CategoryNotFoundError(category))
  def getTypeForCategory(category: Category): Option[ProcessingType]
  final def getAllCategories: List[Category] = getAllCategoriesSet.toList.sorted
  protected[process] def getAllCategoriesSet: Set[Category]
  def getProcessingTypeCategoryUnsafe(processingType: ProcessingType): Category =
    getProcessingTypeCategory(processingType).getOrElse(throw new ProcessingTypeNotFoundError(processingType))
  def getProcessingTypeCategory(processingType: ProcessingType): Option[Category]
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
    val (processingTypesWithDefinedCategory, notDefinedCategoryProcessingTypes) = processingCategories.toList.map {
      case (processingType, CategoriesConfig(Some(category))) =>
        (Map(processingType -> category), Set.empty[ProcessingType])
      case (processingTypeWithoutCategory, CategoriesConfig(None)) =>
        (Map.empty[ProcessingType, Category], Set(processingTypeWithoutCategory))
    }.combineAll
    val isLegacyConfigFormat = config.hasPath(categoryConfigPath)
    val service = if (isLegacyConfigFormat) {
      logger.warn(
        s"Legacy $categoryConfigPath format detected. Please replace it by categories defined inside scenarioTypes. " +
          "In the further versions the support for the old format will be removed"
      )
      val legacy = new LegacyConfigProcessCategoryService(config)
      if (notDefinedCategoryProcessingTypes.nonEmpty) {
        new MixedProcessCategoryService(new ProcessingTypeCategoryService(processingTypesWithDefinedCategory), legacy)
      } else {
        legacy
      }
    } else if (notDefinedCategoryProcessingTypes.nonEmpty) {
      throw new IllegalArgumentException(
        s"Illegal categories configuration format. Some scenario types has no category configured: $notDefinedCategoryProcessingTypes"
      )
    } else {
      new ProcessingTypeCategoryService(processingTypesWithDefinedCategory)
    }
    checkProcessingTypeToCategoryMappingAmbiguity(service)
    checkCategoryToProcessingTypeMappingAmbiguity(processingCategories.keys, service)
    service
  }

  private def checkProcessingTypeToCategoryMappingAmbiguity(
      service: ProcessCategoryService
  ): Unit = {
    val processingTypesForEachCategory = service.getAllCategories
      .map { category =>
        service.getTypeForCategoryUnsafe(category) -> category
      }
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toSet)
    val ambiguousProcessingTypeToCategoryMappings: Map[ProcessingType, Set[Category]] =
      processingTypesForEachCategory.filter(_._2.size > 1)
    if (ambiguousProcessingTypeToCategoryMappings.nonEmpty)
      throw ProcessingTypeToCategoryMappingAmbiguousException(ambiguousProcessingTypeToCategoryMappings)
  }

  // TODO: this is temporary, after fully switch to paradigms we should replace restriction that category
  //       implies processing type with more lax restriction that category + paradigm + engine type
  //       implies processing type
  private def checkCategoryToProcessingTypeMappingAmbiguity(
      scenarioTypes: Iterable[ProcessingType],
      service: ProcessCategoryService
  ): Unit = {
    val processingTypesForEachCategory = scenarioTypes
      .map { processingType =>
        service.getProcessingTypeCategoryUnsafe(processingType) -> processingType
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

  private[process] final case class ProcessingTypeToCategoryMappingAmbiguousException(
      ambiguousProcessingTypeToCategoryMappings: Map[ProcessingType, Set[Category]]
  ) extends IllegalStateException(
        s"Ambiguous scenario type to category mapping detected for categories: $ambiguousProcessingTypeToCategoryMappings"
      )

}

class ProcessingTypeCategoryService(scenarioTypes: Map[String, Category]) extends ProcessCategoryService {
  override protected[process] def getAllCategoriesSet: Set[Category] = scenarioTypes.values.toSet

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    scenarioTypes.find(_._2 == category).map(_._1)

  override def getProcessingTypeCategory(processingType: ProcessingType): Option[Category] =
    scenarioTypes.get(processingType)

}

class LegacyConfigProcessCategoryService(config: Config) extends ProcessCategoryService {

  private val categoriesToTypesMap: Map[Category, ProcessingType] = {
    val categories = config.getConfig(ConfigProcessCategoryService.categoryConfigPath)
    categories.entrySet().asScala.map(_.getKey).map(category => category -> categories.getString(category)).toMap
  }

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    categoriesToTypesMap.get(category)

  // We assume there can't be more categories then config returns
  override protected[process] def getAllCategoriesSet: Set[Category] =
    categoriesToTypesMap.keys.toSet

  override def getProcessingTypeCategory(processingType: ProcessingType): Option[Category] =
    categoriesToTypesMap.collectFirst { case (category, `processingType`) => category }

}

class MixedProcessCategoryService(main: ProcessCategoryService, fallback: ProcessCategoryService)
    extends ProcessCategoryService {
  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    main.getTypeForCategory(category) orElse fallback.getTypeForCategory(category)

  override protected[process] def getAllCategoriesSet: Set[Category] =
    main.getAllCategoriesSet ++ fallback.getAllCategoriesSet

  override def getProcessingTypeCategory(processingType: ProcessingType): Option[Category] =
    main.getProcessingTypeCategory(processingType).orElse(fallback.getProcessingTypeCategory(processingType))
}
