package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.component.{ComponentIdProvider, ComponentIdProviderFactory}
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.StateDefinitionDeduplicationResult
import pl.touk.nussknacker.ui.process.{
  ConfigProcessCategoryService,
  ProcessCategoryService,
  ProcessStateDefinitionService
}

final case class CombinedProcessingTypeData(
    statusNameToStateDefinitionsMapping: Map[StatusName, StateDefinitionDeduplicationResult],
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
