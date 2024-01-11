package pl.touk.nussknacker.ui.process

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.process.ProcessCategoryService.{
  Category,
  CategoryNotFoundError,
  ProcessingTypeNotFoundError
}
import pl.touk.nussknacker.ui.security.api.LoggedUser

object ProcessCategoryService {
  // TODO: Replace it by VO
  type Category = String

  class CategoryNotFoundError(category: Category) extends BadRequestError(s"Category: $category not found")

  class ProcessingTypeNotFoundError(processingType: ProcessingType)
      extends BadRequestError(
        s"Processing type [$processingType] not found, or there are no categories configured for it"
      )

}

trait ProcessCategoryService {
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
      .map(category =>
        category -> categoriesService
          .getTypeForCategory(category)
          .getOrElse(
            throw new IllegalStateException(s"Processing type not defined for category: $category, but it should be")
          )
      )
      .toMap
  }

}

object ConfigProcessCategoryService extends LazyLogging {

  def apply(processingCategories: Map[ProcessingType, String]): ProcessCategoryService = {
    val service = new ProcessingTypeCategoryService(processingCategories)
    checkCategoryToProcessingTypeMappingAmbiguity(processingCategories.keys, service)
    service
  }

  // TODO: this is temporary, after fully switch to processing modes we should replace restriction that category
  //       implies processing type with more lax restriction that category + processing mode + engine type
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
        s"These categories are configured in more than one scenario type, which is not allowed now: $ambiguousCategoryToProcessingTypeMappings"
      )

}

class ProcessingTypeCategoryService(scenarioTypes: Map[String, Category]) extends ProcessCategoryService {
  override protected[process] def getAllCategoriesSet: Set[Category] = scenarioTypes.values.toSet

  override def getTypeForCategory(category: Category): Option[ProcessingType] =
    scenarioTypes.find(_._2 == category).map(_._1)

  override def getProcessingTypeCategory(processingType: ProcessingType): Option[Category] =
    scenarioTypes.get(processingType)

}
