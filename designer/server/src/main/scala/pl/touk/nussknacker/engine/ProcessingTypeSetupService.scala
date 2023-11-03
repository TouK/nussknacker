package pl.touk.nussknacker.engine

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.processingtypesetup.{EngineSetupName, ProcessingMode}
import pl.touk.nussknacker.ui.process.ProcessCategoryService

class ProcessingTypeSetupService(
    categoryService: ProcessCategoryService,
    setups: Map[ProcessingType, ProcessingTypeSetup]
) {

  def processingTypeParametersCombinations: List[ProcessingTypeParametersCombination] =
    (
      for {
        (processingType, details) <- setups.toList
        category                  <- categoryService.getProcessingTypeCategories(processingType)
      } yield ProcessingTypeParametersCombination(details.processingMode, details.engineSetup, category)
    ).sortBy(setup => (setup.processingMode.value, setup.category, setup.engineSetup.name.value))

  def processingTypeSetup(processingType: ProcessingType): ProcessingTypeSetup = setups(processingType)

}

object ProcessingTypeSetupService {

  def apply(
      processingTypes: Map[ProcessingType, ProcessingTypeData],
      categoryService: ProcessCategoryService
  ): ProcessingTypeSetupService = {
    val setups = ProcessingTypeSetupsProvider.processingTypeSetups(processingTypes)
    new ProcessingTypeSetupService(categoryService, setups)
  }

}

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
