package pl.touk.nussknacker.engine

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.scenariodetails.{EngineSetupName, ProcessingMode}
import pl.touk.nussknacker.ui.process.ProcessCategoryService

class ProcessingTypeSetupService(categoryService: ProcessCategoryService) {

  // FIXME: real implementation
  protected def processingTypesSetupsMap: Map[ProcessingType, ProcessingTypeDetails] = Map(
    "streaming" -> ProcessingTypeDetails(
      ProcessingMode.Streaming,
      EngineSetupName("Flink")
    )
  )

  def setups: List[ProcessingTypeSetup] =
    (
      for {
        (processingType, details) <- processingTypesSetupsMap.toList
        category                  <- categoryService.getProcessingTypeCategories(processingType)
      } yield ProcessingTypeSetup(details.processingMode, category, details.engineSetupName)
    ).sortBy(setup => (setup.processingMode.value, setup.category, setup.engineSetupName.value))

  def details(processingType: ProcessingType): ProcessingTypeDetails = processingTypesSetupsMap(processingType)

}

object ProcessingTypeSetupService {

  def apply(
      processingTypes: Map[ProcessingType, ProcessingTypeData],
      categoryService: ProcessCategoryService
  ): ProcessingTypeSetupService = new ProcessingTypeSetupService(categoryService)

}

// ProcessingTypeSetup => ProcessingType
// ProcessingType => ProcessingTypeDetails

// Details + Category = Setup
// Category doesn't fit Processing Type details because you can have multiple categories inside one processing type
case class ProcessingTypeDetails(processingMode: ProcessingMode, engineSetupName: EngineSetupName)

@JsonCodec
case class ProcessingTypeSetup(processingMode: ProcessingMode, category: String, engineSetupName: EngineSetupName)
