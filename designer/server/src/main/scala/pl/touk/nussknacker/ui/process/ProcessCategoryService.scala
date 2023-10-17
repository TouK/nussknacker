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
import pl.touk.nussknacker.ui.process.ProcessCategoryService.{Category, CategoryNotFoundError}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.jdk.CollectionConverters._

object ProcessCategoryService {
  // TODO: Replace it by VO
  type Category = String

  class CategoryNotFoundError(category: Category) extends BadRequestError(s"Category: $category not found")
}

// TODO: Maybe we should merge this with ProcessingTypeSetupService?
trait ProcessCategoryService {
  // TODO: Get rid of category -> ProcessingType methods after fully switch into ProcessingMode and EngineSetupName
  final def getTypeForCategoryUnsafe(category: Category): ProcessingType =
    getTypeForCategory(category).getOrElse(throw new CategoryNotFoundError(category))
  def getTypeForCategory(category: Category): Option[ProcessingType]
  final def getAllCategories: List[Category] = getAllCategoriesSet.toList.sorted
  protected[process] def getAllCategoriesSet: Set[Category]
  final def getProcessingTypeCategories(processingType: ProcessingType): List[Category] =
    getProcessingTypeCategoriesSet(processingType).toList.sorted
  protected[process] def getProcessingTypeCategoriesSet(processingType: ProcessingType): Set[Category]
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
    val (processingTypesWithDefinedCategories, notDefinedCategoriesProcessingTypes) = processingCategories.toList.map {
      case (processingType, CategoriesConfig(Some(categories))) =>
        (Map(processingType -> categories), Set.empty[ProcessingType])
      case (processingTypeWithoutCategories, CategoriesConfig(None)) =>
        (Map.empty[ProcessingType, List[Category]], Set(processingTypeWithoutCategories))
    }.combineAll
    val isLegacyConfigFormat = config.hasPath(categoryConfigPath)
    val service = if (isLegacyConfigFormat) {
      logger.warn(
        s"Legacy $categoryConfigPath format detected. Please replace it by categories defined inside scenarioTypes. " +
          "In the further versions the support for the old format will be removed"
      )
      val legacy = new LegacyConfigProcessCategoryService(config)
      if (notDefinedCategoriesProcessingTypes.nonEmpty) {
        new MixedProcessCategoryService(new ProcessingTypeCategoryService(processingTypesWithDefinedCategories), legacy)
      } else {
        legacy
      }
    } else if (notDefinedCategoriesProcessingTypes.nonEmpty) {
      throw new IllegalArgumentException(
        s"Illegal categories configuration format. Some scenario types has no categories configured: $notDefinedCategoriesProcessingTypes"
      )
    } else {
      new ProcessingTypeCategoryService(processingTypesWithDefinedCategories)
    }
    checkCategoryToProcessingTypeMappingAmbiguity(processingCategories.keys, service)
    service
  }

  // TODO: this is temporary, after fully switch to processing modes we should replace restriction that category
  //       implies processing type with more lax restriction that category + processing mode + engine setup
  //       implies processing type
  private def checkCategoryToProcessingTypeMappingAmbiguity(
      scenarioTypes: Iterable[ProcessingType],
      service: ProcessCategoryService
  ): Unit = {
    val processingTypesForEachCategory = scenarioTypes
      .flatMap { processingType =>
        service.getProcessingTypeCategories(processingType).map(_ -> processingType)
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
  override protected[process] def getAllCategoriesSet: Set[Category] = scenarioTypes.values.flatten.toSet

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    scenarioTypes.find(_._2.contains(category)).map(_._1)

  override protected[process] def getProcessingTypeCategoriesSet(processingType: ProcessingType): Set[Category] =
    scenarioTypes.get(processingType).map(_.toSet).getOrElse(Set.empty)

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

  override protected[process] def getProcessingTypeCategoriesSet(processingType: ProcessingType): Set[Category] =
    categoriesToTypesMap.filter { case (_, procType) => procType.equals(processingType) }.keySet

}

class MixedProcessCategoryService(main: ProcessCategoryService, fallback: ProcessCategoryService)
    extends ProcessCategoryService {
  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    main.getTypeForCategory(category) orElse fallback.getTypeForCategory(category)

  override protected[process] def getAllCategoriesSet: Set[Category] =
    main.getAllCategoriesSet ++ fallback.getAllCategoriesSet

  override protected[process] def getProcessingTypeCategoriesSet(processingType: ProcessingType): Set[Category] =
    main.getProcessingTypeCategoriesSet(processingType) ++ fallback.getProcessingTypeCategoriesSet(processingType)
}
