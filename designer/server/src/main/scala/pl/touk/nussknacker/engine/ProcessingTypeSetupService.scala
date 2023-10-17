package pl.touk.nussknacker.engine

import cats.data.Validated
import cats.data.Validated.Valid
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.processingtypesetup.EngineSetupName
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.security.api.LoggedUser

// We have to keep everything keyed by ProcessingTypeCategory instead of just ProcessingType because given
// model definition can return different components depending on category
class ProcessingTypeSetupService(setups: Map[ProcessingTypeCategory, ProcessingTypeSetup]) {

  def processingTypeParametersCombinations(implicit loggedUser: LoggedUser): List[ProcessingTypeParametersCombination] =
    setups.toList
      .filter { case (ProcessingTypeCategory(_, category), _) =>
        loggedUser.can(category, Permission.Write)
      }
      .map { case (ProcessingTypeCategory(_, category), setup) =>
        ProcessingTypeParametersCombination(setup.processingMode, setup.engineSetup, category)
      }
      .sortBy(setup => (setup.processingMode.value, setup.category, setup.engineSetup.name.value))

  def processingTypeSetup(processingTypeCategory: ProcessingTypeCategory): ProcessingTypeSetup = setups(
    processingTypeCategory
  )

  def processingType(
      queriedProcessingMode: Option[ProcessingMode],
      queriedEngineSetupName: Option[EngineSetupName],
      category: String
  ): Validated[NuDesignerError, ProcessingType] = {
    val matchingProcessingTypesWithEngineErrors = setups.collect {
      case (
            ProcessingTypeCategory(processingType, `category`),
            ProcessingTypeSetup(processingMode, EngineSetup(engineSetupName, errors))
          )
          if queriedProcessingMode.forall(_ == processingMode) && queriedEngineSetupName.forall(_ == engineSetupName) =>
        (processingType, errors)
    }
    matchingProcessingTypesWithEngineErrors.toList match {
      case (singleProcessingType, Nil) :: Nil =>
        Valid(singleProcessingType)
      // FIXME: proper handle errors + tests
      case (_, nonEmptyErrors) :: Nil => ???
      case Nil                        => ???
      case _                          => ???
    }
  }

}

object ProcessingTypeSetupService {

  def apply(
      processingTypes: Map[ProcessingType, ProcessingTypeData],
      categoryService: ProcessCategoryService
  ): ProcessingTypeSetupService = {
    val setups = ProcessingTypeSetupsProvider.processingTypeSetups(processingTypes, categoryService)
    new ProcessingTypeSetupService(setups)
  }

}

final case class ProcessingTypeCategory(processingType: ProcessingType, category: Category)

// ParametersCombination = Setup + Category
case class ProcessingTypeSetup(processingMode: ProcessingMode, engineSetup: EngineSetup)
@JsonCodec
case class EngineSetup(name: EngineSetupName, errors: List[String])

@JsonCodec
case class ProcessingTypeParametersCombination(
    processingMode: ProcessingMode,
    engineSetup: EngineSetup,
    category: String
)
