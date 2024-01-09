package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.component.{ComponentIdProvider, ComponentIdProviderFactory}
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.StatusNameToStateDefinitionsMapping
import pl.touk.nussknacker.ui.process.{
  ConfigProcessCategoryService,
  ProcessCategoryService,
  ProcessStateDefinitionService
}

final case class CombinedProcessingTypeData(
    statusNameToStateDefinitionsMapping: StatusNameToStateDefinitionsMapping,
    componentIdProvider: ComponentIdProvider,
    categoryService: ProcessCategoryService,
)

object CombinedProcessingTypeData {

  def create(
      processingTypes: Map[ProcessingType, ProcessingTypeData]
  ): CombinedProcessingTypeData = {
    val categoryService: ProcessCategoryService =
      ConfigProcessCategoryService(processingTypes.mapValuesNow(_.category))
    CombinedProcessingTypeData(
      statusNameToStateDefinitionsMapping =
        ProcessStateDefinitionService.createDefinitionsMappingUnsafe(processingTypes),
      componentIdProvider = ComponentIdProviderFactory.create(processingTypes),
      categoryService = categoryService
    )
  }

}
