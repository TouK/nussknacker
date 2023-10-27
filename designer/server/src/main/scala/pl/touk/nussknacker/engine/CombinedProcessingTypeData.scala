package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.definition.ComponentIdProvider
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.component.ComponentIdProviderFactory
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
      processingTypes: Map[ProcessingType, ProcessingTypeData],
      designerConfig: ConfigWithUnresolvedVersion
  ): CombinedProcessingTypeData = {
    val categoryService: ProcessCategoryService =
      ConfigProcessCategoryService(designerConfig.resolved, processingTypes.mapValuesNow(_.categoriesConfig))
    CombinedProcessingTypeData(
      statusNameToStateDefinitionsMapping =
        ProcessStateDefinitionService.createDefinitionsMappingUnsafe(processingTypes),
      // While creation of component id provider, we validate all component ids but fragments.
      // We assume that fragments cannot have overridden component id thus are not merged/deduplicated across processing types.
      componentIdProvider = ComponentIdProviderFactory.createUnsafe(processingTypes, categoryService),
      categoryService = categoryService
    )
  }

}
