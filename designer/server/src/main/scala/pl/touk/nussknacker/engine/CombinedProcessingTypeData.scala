package pl.touk.nussknacker.engine

import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.component.{ComponentIdProvider, DefaultComponentIdProvider}
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessStateDefinitionService}
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.StatusNameToStateDefinitionsMapping

case class CombinedProcessingTypeData(statusNameToStateDefinitionsMapping: StatusNameToStateDefinitionsMapping,
                                      componentIdProvider: ComponentIdProvider,
                                     )

object CombinedProcessingTypeData {

  def create(processingTypes: Map[ProcessingType, ProcessingTypeData],
             categoryService: ProcessCategoryService): CombinedProcessingTypeData = {
    CombinedProcessingTypeData(
      statusNameToStateDefinitionsMapping = ProcessStateDefinitionService.createDefinitionsMappingUnsafe(processingTypes),
      componentIdProvider = DefaultComponentIdProvider.createUnsafe(processingTypes, categoryService)
    )
  }

}
