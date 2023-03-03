package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateId
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

import java.net.URI

class ProcessStatusDefinitionService(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                                     categoryService: ProcessCategoryService) {

  def fetchStateDefinitions(): List[StateDefinition] = {
    //gather state definitions for all processing types
    val allProcessingTypeStateDefinitions = typeToConfig.all.toList.flatMap { case (processingType, processingTypeData) =>
      val processingTypeCategories = categoryService.getProcessingTypeCategories(processingType).toSet
      val stateDefinitionManager = processingTypeData.deploymentManager.processStateDefinitionManager
      stateDefinitionManager.statusIds().map(name =>
        StateDefinition(
          name,
          stateDefinitionManager.statusDisplayableName(name),
          stateDefinitionManager.statusIcon(name),
          processingTypeCategories
        ))
    }
    //flatten within one state id (aka name), here we expect that one state has the same definion across all processingTypes
    allProcessingTypeStateDefinitions.groupBy(_.name).map { case (name, stateDefinitions) =>
      val uniqueStateDefinitionsForId = stateDefinitions.groupBy(state => (state.name, state.displayableName, state.icon))
      if (uniqueStateDefinitionsForId.size > 1) {
        throw new IllegalStateException("State definitions are not unique")
      }
      val categories = stateDefinitions.flatMap(_.categories).toSet
      stateDefinitions.head.copy(categories = categories)
    }.toList
  }

}

@JsonCodec case class StateDefinition(name: StateId,
                                      displayableName: String,
                                      icon: Option[URI],
                                      categories: Set[String]
                                     )