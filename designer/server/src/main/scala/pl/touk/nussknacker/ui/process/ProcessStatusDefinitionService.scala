package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

import java.net.URI

class ProcessStatusDefinitionService(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                                     categoryService: ProcessCategoryService) {

  def fetchStateDefinitions(): List[StateDefinition] = {
    //gather state definitions for all processing types
    val allProcessingTypeStateDefinitions = typeToConfig.all.toList.flatMap { case (processingType, processingTypeData) =>
      val processingTypeCategories = categoryService.getProcessingTypeCategories(processingType).toSet
      val stateDefinitionManager = processingTypeData.deploymentManager.processStateDefinitionManager
      stateDefinitionManager.stateDefinitions().map(definition =>
        StateDefinition(
          definition.name,
          definition.displayableName,
          definition.icon,
          processingTypeCategories
        ))
    }
    //group by state ID (aka name), here we expect that one state has the same definition across all processingTypes
    allProcessingTypeStateDefinitions.groupBy(_.name).map { case (name, stateDefinitionsByName) =>
      val uniqueStateDefinitionsByName = stateDefinitionsByName.groupBy(state => (state.name, state.displayableName, state.icon))
      if (uniqueStateDefinitionsByName.size > 1) {
        throw new IllegalStateException("State definitions are not unique")
      }
      val categories = stateDefinitionsByName.flatMap(_.categories).toSet
      stateDefinitionsByName.head.copy(categories = categories)
    }.toList
  }

}

@JsonCodec case class StateDefinition(name: StatusName,
                                      displayableName: String,
                                      icon: Option[URI],
                                      categories: Set[String])