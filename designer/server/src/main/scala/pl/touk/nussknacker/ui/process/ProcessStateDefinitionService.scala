package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.deployment.StateDefinition
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

class ProcessStateDefinitionService(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                                    categoryService: ProcessCategoryService) {

  def fetchStateDefinitions(): List[UIStateDefinition] = {
    //gather state definitions for all processing types
    val allProcessingTypeStateDefinitions = typeToConfig.all.toList.flatMap { case (processingType, processingTypeData) =>
      val processingTypeCategories = categoryService.getProcessingTypeCategories(processingType).toSet
      val stateDefinitionManager = processingTypeData.deploymentManager.processStateDefinitionManager
      stateDefinitionManager.stateDefinitions().map(definition => UIStateDefinition(definition, processingTypeCategories))
    }
    //group by state ID (aka name), here we expect that one state has the same definition across all processingTypes
    allProcessingTypeStateDefinitions.groupBy(_.definition.name).map { case (name, stateDefinitionsByName) =>
      val uniqueStateDefinitionsByName = stateDefinitionsByName.map(_.definition)
        .groupBy(state => (state.displayableName, state.icon, state.tooltip, state.description))
      if (uniqueStateDefinitionsByName.size > 1) {
        throw new IllegalStateException("State definitions are not unique")
      }
      val categories = stateDefinitionsByName.flatMap(_.categories).toSet
      stateDefinitionsByName.head.copy(categories = categories)
    }.toList
  }

}

@JsonCodec case class UIStateDefinition(definition: StateDefinition, categories: Set[String])